package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Point;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing prayer activation, monitoring, and optimization.
 * Provides centralized logic for all prayer-related operations in combat scenarios.
 */
@Slf4j
@Singleton
public class PrayerService {
    
    private final Client client;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final ScheduledExecutorService scheduler;
    
    @Inject
    public PrayerService(Client client, ActionService actionService, HumanizerService humanizerService) {
        this.client = client;
        this.actionService = actionService;
        this.humanizerService = humanizerService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    /**
     * Enum defining common combat prayers with their IDs and purposes.
     */
    public enum CombatPrayer {
        // Offensive prayers
        PIETY(22, "Piety", true),
        CHIVALRY(25, "Chivalry", true),
        ULTIMATE_STRENGTH(13, "Ultimate Strength", true),
        INCREDIBLE_REFLEXES(16, "Incredible Reflexes", true),
        
        // Defensive prayers
        PROTECT_FROM_MELEE(18, "Protect from Melee", false),
        PROTECT_FROM_MISSILES(19, "Protect from Missiles", false),
        PROTECT_FROM_MAGIC(20, "Protect from Magic", false),
        THICK_SKIN(0, "Thick Skin", false),
        
        // Utility prayers
        RAPID_RESTORE(9, "Rapid Restore", false),
        RAPID_HEAL(10, "Rapid Heal", false);
        
        private final int id;
        private final String name;
        private final boolean isOffensive;
        
        CombatPrayer(int id, String name, boolean isOffensive) {
            this.id = id;
            this.name = name;
            this.isOffensive = isOffensive;
        }
        
        public int getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public boolean isOffensive() {
            return isOffensive;
        }
    }
    
    /**
     * Check if a specific prayer is currently active.
     * 
     * @param prayer The prayer to check
     * @return true if the prayer is active, false otherwise
     */
    public boolean isPrayerActive(CombatPrayer prayer) {
        Prayer prayerEnum = Prayer.values()[prayer.getId()];
        boolean isActive = client.isPrayerActive(prayerEnum);
        
        log.debug("Prayer {} ({}) is active: {}", prayer.getName(), prayer.getId(), isActive);
        return isActive;
    }
    
    /**
     * Check if any offensive prayers are currently active.
     * 
     * @return true if at least one offensive prayer is active
     */
    public boolean hasOffensivePrayersActive() {
        for (CombatPrayer prayer : CombatPrayer.values()) {
            if (prayer.isOffensive() && isPrayerActive(prayer)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the current prayer points.
     * 
     * @return Current prayer points
     */
    public int getCurrentPrayerPoints() {
        return client.getBoostedSkillLevel(Skill.PRAYER);
    }
    
    /**
     * Get the maximum prayer points.
     * 
     * @return Maximum prayer points
     */
    public int getMaxPrayerPoints() {
        return client.getRealSkillLevel(Skill.PRAYER);
    }
    
    /**
     * Get the current prayer points as a percentage.
     * 
     * @return Prayer points percentage (0-100)
     */
    public int getPrayerPercentage() {
        int current = getCurrentPrayerPoints();
        int max = getMaxPrayerPoints();
        
        if (max == 0) {
            return 0;
        }
        
        return (current * 100) / max;
    }
    
    /**
     * Check if prayer points are below the specified threshold.
     * 
     * @param threshold Threshold percentage (0-100)
     * @return true if prayer points are below threshold
     */
    public boolean needsPrayerRestore(int threshold) {
        return getPrayerPercentage() <= threshold;
    }
    
    /**
     * Activate a specific prayer with humanized delay.
     * 
     * @param prayer The prayer to activate
     * @return true if activation was initiated, false if already active or failed
     */
    public boolean activatePrayer(CombatPrayer prayer) {
        if (isPrayerActive(prayer)) {
            log.debug("Prayer {} is already active", prayer.getName());
            return false;
        }
        
        int currentPrayer = getCurrentPrayerPoints();
        if (currentPrayer <= 0) {
            log.warn("Cannot activate prayer {}: no prayer points remaining", prayer.getName());
            return false;
        }
        
        Widget prayerWidget = getPrayerWidget(prayer);
        if (prayerWidget == null) {
            log.warn("Cannot find widget for prayer {}", prayer.getName());
            return false;
        }
        
        log.info("Activating prayer: {}", prayer.getName());
        
        // Use humanized delay before activating prayer
        int delay = humanizerService.getRandomDelay(200, 400);
        
        scheduler.schedule(() -> {
            Point prayerPoint = new Point(
                prayerWidget.getCanvasLocation().getX() + prayerWidget.getWidth() / 2,
                prayerWidget.getCanvasLocation().getY() + prayerWidget.getHeight() / 2
            );
            actionService.sendClickRequest(prayerPoint, false);
            log.info("Activated prayer: {}", prayer.getName());
        }, delay, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    /**
     * Deactivate a specific prayer with humanized delay.
     * 
     * @param prayer The prayer to deactivate
     * @return true if deactivation was initiated, false if already inactive or failed
     */
    public boolean deactivatePrayer(CombatPrayer prayer) {
        if (!isPrayerActive(prayer)) {
            log.debug("Prayer {} is already inactive", prayer.getName());
            return false;
        }
        
        Widget prayerWidget = getPrayerWidget(prayer);
        if (prayerWidget == null) {
            log.warn("Cannot find widget for prayer {}", prayer.getName());
            return false;
        }
        
        log.info("Deactivating prayer: {}", prayer.getName());
        
        // Use humanized delay before deactivating prayer
        int delay = humanizerService.getRandomDelay(200, 400);
        
        scheduler.schedule(() -> {
            Point prayerPoint = new Point(
                prayerWidget.getCanvasLocation().getX() + prayerWidget.getWidth() / 2,
                prayerWidget.getCanvasLocation().getY() + prayerWidget.getHeight() / 2
            );
            actionService.sendClickRequest(prayerPoint, false);
            log.info("Deactivated prayer: {}", prayer.getName());
        }, delay, TimeUnit.MILLISECONDS);
        
        return true;
    }
    
    /**
     * Deactivate all currently active prayers to conserve prayer points.
     */
    public void deactivateAllPrayers() {
        log.info("Deactivating all active prayers");
        
        for (CombatPrayer prayer : CombatPrayer.values()) {
            if (isPrayerActive(prayer)) {
                deactivatePrayer(prayer);
            }
        }
    }
    
    /**
     * Activate the best available offensive prayer based on player's prayer level.
     * 
     * @return true if a prayer was activated, false otherwise
     */
    public boolean activateBestOffensivePrayer() {
        int prayerLevel = client.getRealSkillLevel(Skill.PRAYER);
        
        // Try to activate the best available offensive prayer
        if (prayerLevel >= 70) {
            return activatePrayer(CombatPrayer.PIETY);
        } else if (prayerLevel >= 60) {
            return activatePrayer(CombatPrayer.CHIVALRY);
        } else if (prayerLevel >= 31) {
            return activatePrayer(CombatPrayer.ULTIMATE_STRENGTH);
        } else if (prayerLevel >= 16) {
            return activatePrayer(CombatPrayer.INCREDIBLE_REFLEXES);
        }
        
        log.debug("No suitable offensive prayer available for prayer level {}", prayerLevel);
        return false;
    }
    
    /**
     * Get the prayer widget for a specific prayer.
     * 
     * @param prayer The prayer to get the widget for
     * @return The prayer widget, or null if not found
     */
    private Widget getPrayerWidget(CombatPrayer prayer) {
        // Prayer widgets are typically found in the prayer tab
        // This is a simplified implementation - actual widget IDs may vary
        try {
            Widget prayerTab = client.getWidget(InterfaceID.Prayerbook.CONTAINER);
            if (prayerTab != null) {
                Widget[] prayerWidgets = prayerTab.getChildren();
                if (prayerWidgets != null && prayer.getId() < prayerWidgets.length) {
                    return prayerWidgets[prayer.getId()];
                }
            }
        } catch (Exception e) {
            log.warn("Error finding prayer widget for {}: {}", prayer.getName(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Shutdown the prayer service and clean up resources.
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            log.info("PrayerService scheduler shutdown completed");
        }
    }
}