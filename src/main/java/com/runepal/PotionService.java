package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Point;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing potion detection, consumption, and monitoring.
 * Provides centralized logic for all potion-related operations in combat scenarios.
 */
@Slf4j
@Singleton
public class PotionService {
    
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final ScheduledExecutorService scheduler;
    
    @Inject
    public PotionService(Client client, GameService gameService, ActionService actionService, HumanizerService humanizerService) {
        this.client = client;
        this.gameService = gameService;
        this.actionService = actionService;
        this.humanizerService = humanizerService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * Enum defining different types of potions with their item IDs.
     * Each potion type contains item IDs from full dose (4) to empty (1).
     */
    public enum PotionType {
        PRAYER_POTION(2434, 139, 141, 143),      // Prayer potion (4) to (1)
        SUPER_COMBAT(12695, 12697, 12699, 12701), // Super combat (4) to (1)
        ANTIPOISON(175, 177, 179, 181),          // Antipoison (4) to (1)
        ENERGY(3016, 3018, 3020, 3022),          // Energy potion (4) to (1)
        SUPER_STRENGTH(2440, 145, 147, 149),     // Super strength (4) to (1)
        SUPER_ATTACK(2436, 2438, 147, 149),      // Super attack (4) to (1)
        SUPER_DEFENCE(2442, 163, 165, 167);      // Super defence (4) to (1)
        
        private final int[] itemIds;
        
        PotionType(int... itemIds) {
            this.itemIds = itemIds;
        }
        
        public int[] getItemIds() {
            return itemIds;
        }
        
        public boolean matches(int itemId) {
            return Arrays.stream(itemIds).anyMatch(id -> id == itemId);
        }
    }
    
    /**
     * Check if player needs a prayer potion based on current prayer points.
     * 
     * @param threshold Prayer point percentage threshold (0-100)
     * @return true if prayer points are below threshold
     */
    public boolean needsPrayerPotion(int threshold) {
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        
        if (maxPrayer == 0) {
            return false; // No prayer levels
        }
        
        int prayerPercentage = (currentPrayer * 100) / maxPrayer;
        boolean needsPotion = prayerPercentage <= threshold;
        
        log.debug("Prayer points: {}/{} ({}%) - Threshold: {}% - Needs potion: {}", 
                 currentPrayer, maxPrayer, prayerPercentage, threshold, needsPotion);
        
        return needsPotion;
    }
    
    /**
     * Check if player should consume a combat potion.
     * This is typically done at the start of combat or when effects wear off.
     * 
     * @return true if combat potion should be consumed
     */
    public boolean needsCombatPotion() {
        // Check if any combat stats are at base level (no boost)
        int currentStr = client.getBoostedSkillLevel(Skill.STRENGTH);
        int baseStr = client.getRealSkillLevel(Skill.STRENGTH);
        
        int currentAtt = client.getBoostedSkillLevel(Skill.ATTACK);
        int baseAtt = client.getRealSkillLevel(Skill.ATTACK);
        
        int currentDef = client.getBoostedSkillLevel(Skill.DEFENCE);
        int baseDef = client.getRealSkillLevel(Skill.DEFENCE);
        
        // Need combat potion if any combat stat is not boosted
        boolean needsBoost = (currentStr <= baseStr) || (currentAtt <= baseAtt) || (currentDef <= baseDef);
        
        log.debug("Combat stats - Str: {}/{}, Att: {}/{}, Def: {}/{} - Needs boost: {}", 
                 currentStr, baseStr, currentAtt, baseAtt, currentDef, baseDef, needsBoost);
        
        return needsBoost;
    }
    
    /**
     * Check if player is poisoned and needs antipoison.
     * 
     * @return true if player is poisoned
     */
    public boolean needsAntipoison() {
        // Check for poison venom state - this would need to be implemented
        // based on game state detection (poison icon, chat messages, etc.)
        // For now, return false as poison detection is complex
        return false;
    }
    
    /**
     * Find a potion of the specified type in the player's inventory.
     * 
     * @param potionType The type of potion to find
     * @return Point of the potion in inventory, or null if not found
     */
    public Point findPotionInInventory(PotionType potionType) {
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (potionType.matches(itemId)) {
                Point potionPoint = gameService.getInventoryItemPoint(slot);
                log.debug("Found {} potion at slot {} (item ID: {})", potionType, slot, itemId);
                return potionPoint;
            }
        }
        
        log.debug("No {} potion found in inventory", potionType);
        return null;
    }
    
    /**
     * Consume a potion of the specified type with humanized delay.
     * 
     * @param potionType The type of potion to consume
     * @return true if potion consumption was initiated, false if no potion found
     */
    public boolean consumePotion(PotionType potionType) {
        Point potionPoint = findPotionInInventory(potionType);
        
        if (potionPoint == null) {
            log.warn("Cannot consume {}: not found in inventory", potionType);
            return false;
        }
        
        log.info("Consuming {} potion at point: {}", potionType, potionPoint);
        
        // Use humanized delay before consuming
        int delay = humanizerService.getRandomDelay(300, 600);
        
        scheduler.schedule(() -> {
            actionService.sendClickRequest(potionPoint, false);
            log.info("Consumed {} potion", potionType);
        }, delay, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    /**
     * Get the count of potions of a specific type in inventory.
     * 
     * @param potionType The type of potion to count
     * @return Number of potions of this type in inventory
     */
    public int getPotionCount(PotionType potionType) {
        int count = 0;
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (potionType.matches(itemId)) {
                count++;
            }
        }
        
        log.debug("Found {} potions of type {} in inventory", count, potionType);
        return count;
    }
    
    /**
     * Check if player has any potions of the specified type.
     * 
     * @param potionType The type of potion to check for
     * @return true if at least one potion of this type exists in inventory
     */
    public boolean hasPotion(PotionType potionType) {
        return findPotionInInventory(potionType) != null;
    }
    
    /**
     * Shutdown the potion service and clean up resources.
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            log.info("PotionService scheduler shutdown completed");
        }
    }
}