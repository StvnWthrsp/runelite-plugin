package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

/**
 * Service for managing supply levels, inventory monitoring, and banking triggers.
 * Provides centralized logic for determining when to bank and what supplies are needed.
 */
@Slf4j
@Singleton
public class SupplyManager {
    
    private final Client client;
    private final GameService gameService;
    private final PotionService potionService;
    private final BotConfig config;
    
    // Food item IDs (same as CombatTask for consistency)
    private static final int[] FOOD_IDS = {
        379,  // Lobster
        385,  // Shark
        7946, // Monkfish
        361,  // Tuna
        373,  // Swordfish
        2142, // Cooked karambwan
        329   // Salmon
    };
    
    @Inject
    public SupplyManager(Client client, GameService gameService, PotionService potionService, BotConfig config) {
        this.client = client;
        this.gameService = gameService;
        this.potionService = potionService;
        this.config = config;
    }
    
    /**
     * Check if supplies are running low and banking is needed.
     * 
     * @return true if supplies are low and banking should be initiated
     */
    public boolean suppliesLow() {
        boolean foodLow = getFoodCount() < config.combatMinFoodCount();
        boolean potionsLow = false;
        
        // Check prayer potions if prayer potion usage is enabled
        if (config.combatUsePrayerPotions()) {
            potionsLow = getPrayerPotionCount() < config.combatMinPrayerPotions();
        }
        
        // Check combat potions if combat potion usage is enabled
        if (config.combatUseCombatPotions() && !potionsLow) {
            potionsLow = getCombatPotionCount() < config.combatMinCombatPotions();
        }
        
        boolean suppliesLow = foodLow || potionsLow;
        
        log.debug("Supply check - Food: {}/{} (low: {}), Prayer potions: {}/{} (low: {}), Combat potions: {}/{} (low: {}), Overall low: {}", 
                 getFoodCount(), config.combatMinFoodCount(), foodLow,
                 getPrayerPotionCount(), config.combatMinPrayerPotions(), 
                 config.combatUsePrayerPotions() && getPrayerPotionCount() < config.combatMinPrayerPotions(),
                 getCombatPotionCount(), config.combatMinCombatPotions(),
                 config.combatUseCombatPotions() && getCombatPotionCount() < config.combatMinCombatPotions(),
                 suppliesLow);
        
        return suppliesLow;
    }
    
    /**
     * Get the count of food items in the inventory.
     * 
     * @return Number of food items in inventory
     */
    public int getFoodCount() {
        int count = 0;
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (Arrays.stream(FOOD_IDS).anyMatch(id -> id == itemId)) {
                count++;
            }
        }
        
        log.debug("Found {} food items in inventory", count);
        return count;
    }
    
    /**
     * Get the count of prayer potions in the inventory.
     * 
     * @return Number of prayer potions in inventory
     */
    public int getPrayerPotionCount() {
        int count = potionService.getPotionCount(PotionService.PotionType.PRAYER_POTION);
        log.debug("Found {} prayer potions in inventory", count);
        return count;
    }
    
    /**
     * Get the count of combat potions in the inventory.
     * 
     * @return Number of combat potions in inventory
     */
    public int getCombatPotionCount() {
        int count = potionService.getPotionCount(PotionService.PotionType.SUPER_COMBAT);
        log.debug("Found {} combat potions in inventory", count);
        return count;
    }
    
    /**
     * Get the count of antipoison potions in the inventory.
     * 
     * @return Number of antipoison potions in inventory
     */
    public int getAntipoisonCount() {
        int count = potionService.getPotionCount(PotionService.PotionType.ANTIPOISON);
        log.debug("Found {} antipoison potions in inventory", count);
        return count;
    }
    
    /**
     * Check if the inventory has adequate supplies for combat.
     * 
     * @return true if supplies are adequate, false if banking is recommended
     */
    public boolean hasAdequateSupplies() {
        return !suppliesLow();
    }
    
    /**
     * Check if player has any food in inventory.
     * 
     * @return true if at least one food item exists in inventory
     */
    public boolean hasFood() {
        return getFoodCount() > 0;
    }
    
    /**
     * Check if player has any prayer potions in inventory.
     * 
     * @return true if at least one prayer potion exists in inventory
     */
    public boolean hasPrayerPotions() {
        return getPrayerPotionCount() > 0;
    }
    
    /**
     * Check if player has any combat potions in inventory.
     * 
     * @return true if at least one combat potion exists in inventory
     */
    public boolean hasCombatPotions() {
        return getCombatPotionCount() > 0;
    }
    
    /**
     * Get a summary of current supply levels.
     * 
     * @return String summary of current supplies
     */
    public String getSupplySummary() {
        return String.format("Supplies - Food: %d, Prayer: %d, Combat: %d, Antipoison: %d", 
                           getFoodCount(), getPrayerPotionCount(), getCombatPotionCount(), getAntipoisonCount());
    }
    
    /**
     * Check if inventory space is available for looting.
     * 
     * @return true if there are empty inventory slots
     */
    public boolean hasInventorySpace() {
        return !gameService.isInventoryFull();
    }
    
    /**
     * Get the number of free inventory slots.
     * 
     * @return Number of empty inventory slots
     */
    public int getFreeInventorySlots() {
        int usedSlots = 0;
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (itemId != -1) { // -1 indicates empty slot
                usedSlots++;
            }
        }
        
        int freeSlots = 28 - usedSlots;
        log.debug("Free inventory slots: {}/28", freeSlots);
        return freeSlots;
    }
    
    /**
     * Determine if emergency banking is needed (very low on critical supplies).
     * 
     * @return true if emergency banking is required
     */
    public boolean needsEmergencyBanking() {
        // Emergency banking when food is critically low (1 or 0)
        boolean criticalFoodShortage = getFoodCount() <= 1;
        
        // Emergency banking when using prayers but no prayer potions remain
        boolean criticalPrayerShortage = config.combatUsePrayerPotions() && 
                                       config.combatUsePrayers() && 
                                       getPrayerPotionCount() == 0;
        
        boolean emergency = criticalFoodShortage || criticalPrayerShortage;
        
        if (emergency) {
            log.warn("Emergency banking needed - Food: {}, Prayer potions: {}", 
                    getFoodCount(), getPrayerPotionCount());
        }
        
        return emergency;
    }
    
    /**
     * Check if the player should continue combat based on supply levels.
     * 
     * @return true if combat can continue, false if should stop/bank
     */
    public boolean canContinueCombat() {
        // Can continue if we have food and either don't use prayers or have prayer potions
        boolean hasMinimalFood = getFoodCount() > 0;
        boolean prayerOk = !config.combatUsePrayers() || 
                          !config.combatUsePrayerPotions() || 
                          getPrayerPotionCount() > 0;
        
        boolean canContinue = hasMinimalFood && prayerOk;
        
        log.debug("Can continue combat: {} (Food: {}, Prayer OK: {})", 
                 canContinue, hasMinimalFood, prayerOk);
        
        return canContinue;
    }
}