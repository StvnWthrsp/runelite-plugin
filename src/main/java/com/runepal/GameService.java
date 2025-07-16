package com.runepal;

import com.runepal.entity.Interactable;
import com.runepal.services.GameStateService;
import com.runepal.services.EntityService;
import com.runepal.services.ClickService;
import com.runepal.services.UtilityService;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import com.runepal.entity.NpcEntity;

import javax.inject.Inject;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class GameService {
    private final GameStateService gameStateService;
    private final EntityService entityService;
    private final ClickService clickService;
    private final UtilityService utilityService;

    // Performance optimization: Cache for reachability calculations
    private final Map<String, Boolean> reachabilityCache = new ConcurrentHashMap<>();
    private long lastCacheCleanup = System.currentTimeMillis();
    private static final long CACHE_CLEANUP_INTERVAL_MS = 30000; // 30 seconds
    private static final int MAX_CACHE_SIZE = 1000;

    @Inject
    public GameService(GameStateService gameStateService, EntityService entityService, 
                      ClickService clickService, UtilityService utilityService) {
        this.gameStateService = Objects.requireNonNull(gameStateService, "gameStateService cannot be null");
        this.entityService = Objects.requireNonNull(entityService, "entityService cannot be null");
        this.clickService = Objects.requireNonNull(clickService, "clickService cannot be null");
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService cannot be null");
    }

    public boolean isInventoryFull() {
        return gameStateService.isInventoryFull();
    }

    public boolean isInventoryEmpty() {
        return gameStateService.isInventoryEmpty();
    }

    public boolean isPlayerIdle() {
        return gameStateService.isPlayerIdle();
    }

    public GameObject findNearestGameObject(int... ids) {
        return entityService.findNearestGameObject(ids);
    }

    public int getInventoryItemId(int slot) {
        return gameStateService.getInventoryItemId(slot);
    }

    public Point getInventoryItemPoint(int slot) {
        return gameStateService.getInventoryItemPoint(slot);
    }

    public Point getBankItemPoint(int slot) {
        return gameStateService.getBankItemPoint(slot);
    }

    public Point getRandomClickablePoint(NPC npc) {
        return clickService.getRandomClickablePoint(npc);
    }

    public Point getRandomClickablePoint(GameObject gameObject) {
        return clickService.getRandomClickablePoint(gameObject);
    }

    public Point getRandomClickablePoint(TileObject tileObject) {
        return clickService.getRandomClickablePoint(tileObject);
    }

    public NPC findNearestNpc(String[] npcNames) {
        return entityService.findNearestNpc(npcNames);
    }

    /**
     * Generic method to find the nearest interactable entity that matches the given predicate.
     * This replaces the separate findNearestGameObject and findNearestNpc methods.
     * 
     * @param predicate the condition to match
     * @return the nearest matching Interactable, or null if none found
     */
    public Interactable findNearest(Predicate<Interactable> predicate) {
        return entityService.findNearest(predicate);
    }

    /**
     * Gets a random clickable point within the bounds of any interactable entity.
     * This replaces the separate getRandomClickablePoint methods for GameObject and NPC.
     * 
     * @param interactable the interactable entity
     * @return a random point within the clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Interactable interactable) {
        return clickService.getRandomClickablePoint(interactable);
    }

    /**
     * Gets a random clickable point within the bounds of a Widget.
     * This method handles UI widgets like bank buttons, interfaces, etc.
     * 
     * @param widget the widget to get a click point for
     * @return a random point within the widget bounds, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Widget widget) {
        return clickService.getRandomClickablePoint(widget);
    }

    /**
     * Gets a random point within the specified bounds.
     * Useful for clicking within arbitrary rectangular areas.
     * 
     * @param bounds the rectangle bounds
     * @return a random point within the bounds, or Point(-1, -1) if bounds are empty
     */
    public Point getRandomPointInBounds(Rectangle bounds) {
        return clickService.getRandomPointInBounds(bounds);
    }

    /**
     * Gets all interactable entities in the current scene.
     * This includes both GameObjects and NPCs wrapped in their respective entity adapters.
     * 
     * @return a stream of all interactable entities
     */
    public Stream<Interactable> getAllInteractables() {
        return entityService.getAllInteractables();
    }

    // --- Convenience methods using the new unified approach ---
    
    /**
     * Convenience method to find the nearest GameObject with any of the specified IDs.
     * Uses the new unified findNearest method internally.
     * 
     * @param ids the GameObject IDs to search for
     * @return the nearest matching GameObject, or null if none found
     */
    public GameObject findNearestGameObjectNew(int... ids) {
        return entityService.findNearestGameObjectNew(ids);
    }
    
    /**
     * Convenience method to find the nearest NPC with any of the specified names.
     * Uses the new unified findNearest method internally.
     * 
     * @param npcNames the NPC names to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpcNew(String... npcNames) {
        return entityService.findNearestNpcNew(npcNames);
    }

    public boolean isItemInList(int itemId, int[] list) {
        return utilityService.isItemInList(itemId, list);
    }


    public boolean verifyHoverAction(String expectedAction, String expectedTarget) {
        return utilityService.verifyHoverAction(expectedAction, expectedTarget);
    }

    public WorldPoint getPlayerLocation() {
        return gameStateService.getPlayerLocation();
    }

    /**
     * Convenience method to find the nearest NPC with a specific ID.
     * 
     * @param npcId the NPC ID to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpc(int npcId) {
        return entityService.findNearestNpc(npcId);
    }

    /**
     * Convenience method to find the nearest GameObject with a specific ID.
     * 
     * @param gameObjectId the GameObject ID to search for
     * @return the nearest matching GameObject, or null if none found
     */
    public GameObject findNearestGameObject(int gameObjectId) {
        return entityService.findNearestGameObject(gameObjectId);
    }

    /**
     * Checks if the player has a specific item in their inventory.
     * 
     * @param itemId the item ID to check for
     * @return true if the player has the item, false otherwise
     */
    public boolean hasItem(int itemId) {
        return gameStateService.hasItem(itemId);
    }

    /**
     * Gets the current animation ID of the local player.
     * 
     * @return the current animation ID, or -1 if no animation is playing
     */
    public int getCurrentAnimation() {
        return gameStateService.getCurrentAnimation();
    }

    /**
     * Checks if the local player is currently performing a specific animation.
     * 
     * @param animationId the animation ID to check for
     * @return true if the player is performing the specified animation, false otherwise
     */
    public boolean isCurrentAnimation(int animationId) {
        return gameStateService.isCurrentAnimation(animationId);
    }

    /**
     * Checks if the local player is currently performing any mining animation.
     * 
     * @return true if the player is performing a mining animation, false otherwise
     */
    public boolean isCurrentlyMining() {
        return gameStateService.isCurrentlyMining();
    }

    /**
     * Checks if the local player is currently performing any cooking animation.
     * 
     * @return true if the player is performing a cooking animation, false otherwise
     */
    public boolean isCurrentlyCooking() {
        return gameStateService.isCurrentlyCooking();
    }

    /**
     * Checks if the local player is currently performing any woodcutting animation.
     * 
     * @return true if the player is performing a woodcutting animation, false otherwise
     */
    public boolean isCurrentlyWoodcutting() {
        return gameStateService.isCurrentlyWoodcutting();
    }

    public Rectangle getMenuEntryBounds(MenuEntry entry, int entryIndex) {
        return gameStateService.getMenuEntryBounds(entry, entryIndex);
    }

    public boolean isMouseOverObject(GameObject object) {
        return gameStateService.isMouseOverObject(object);
    }

    public boolean isMouseOverNpc(NPC npc) {
        return gameStateService.isMouseOverNpc(npc);
    }

    public int getInventoryItemIndex(int itemId) {
        return gameStateService.getInventoryItemIndex(itemId);
    }

    // Phase 2: Combat Bot Reachability Enhancement Methods

    /**
     * Checks if an NPC is reachable from the player's current location.
     * Uses basic collision detection for Level 1 implementation with performance caching.
     * 
     * @param npc the NPC to check reachability for
     * @param playerLocation the player's current world location
     * @return true if the NPC is likely reachable, false otherwise
     */
    public boolean isNpcReachable(NPC npc, WorldPoint playerLocation) {
        if (npc == null || playerLocation == null) {
            return false;
        }

        WorldPoint npcLocation = npc.getWorldLocation();
        if (npcLocation == null) {
            return false;
        }

        // Check if NPC is on the same plane
        if (npcLocation.getPlane() != playerLocation.getPlane()) {
            return false;
        }

        // Basic distance check - NPCs beyond 15 tiles are likely unreachable for combat
        int distance = playerLocation.distanceTo(npcLocation);
        if (distance > 15) {
            return false;
        }

        // NPCs within 3 tiles are generally reachable unless there's a major obstacle
        if (distance <= 3) {
            return true;
        }

        // For medium distances, use cached line-of-sight checking
        return isLineOfSightClearCached(playerLocation, npcLocation);
    }

    /**
     * Cached version of line-of-sight checking for performance optimization.
     * 
     * @param from starting point
     * @param to ending point
     * @return true if line of sight is clear, false if blocked
     */
    private boolean isLineOfSightClearCached(WorldPoint from, WorldPoint to) {
        // Clean cache periodically
        cleanupCacheIfNeeded();
        
        // Create cache key based on coordinates (rounded to nearest 2x2 area for efficiency)
        String cacheKey = String.format("%d,%d,%d-%d,%d,%d", 
            from.getX() / 2 * 2, from.getY() / 2 * 2, from.getPlane(),
            to.getX() / 2 * 2, to.getY() / 2 * 2, to.getPlane());
        
        // Check cache first
        Boolean cachedResult = reachabilityCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Calculate and cache result
        boolean result = isBasicLineOfSightClear(from, to);
        
        // Only cache if we're not at capacity
        if (reachabilityCache.size() < MAX_CACHE_SIZE) {
            reachabilityCache.put(cacheKey, result);
        }
        
        return result;
    }

    /**
     * Periodically cleans up the reachability cache to prevent memory leaks.
     */
    private void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheCleanup > CACHE_CLEANUP_INTERVAL_MS) {
            reachabilityCache.clear();
            lastCacheCleanup = currentTime;
            log.debug("Cleared reachability cache for performance");
        }
    }

    /**
     * Performs basic line-of-sight checking between two points.
     * Uses RuneLite collision data for obstacle detection.
     * 
     * @param from starting point
     * @param to ending point
     * @return true if basic line of sight is clear, false if blocked
     */
    private boolean isBasicLineOfSightClear(WorldPoint from, WorldPoint to) {
        try {
            // Get collision data from the client
            CollisionData[] collisionMaps = gameStateService.getClient().getTopLevelWorldView().getCollisionMaps();
            if (collisionMaps == null || from.getPlane() >= collisionMaps.length) {
                // If we can't get collision data, assume reachable (conservative approach)
                return true;
            }

            CollisionData collisionData = collisionMaps[from.getPlane()];
            if (collisionData == null) {
                return true;
            }

            // Check a few key points along the path for major obstacles
            int deltaX = to.getX() - from.getX();
            int deltaY = to.getY() - from.getY();
            int steps = Math.max(Math.abs(deltaX), Math.abs(deltaY));
            
            // Sample 3-5 points along the path
            int samplePoints = Math.min(5, Math.max(3, steps / 2));
            
            for (int i = 1; i < samplePoints; i++) {
                int checkX = from.getX() + (deltaX * i) / samplePoints;
                int checkY = from.getY() + (deltaY * i) / samplePoints;
                
                // Convert to local coordinates for collision checking
                int localX = checkX - gameStateService.getClient().getTopLevelWorldView().getScene().getBaseX();
                int localY = checkY - gameStateService.getClient().getTopLevelWorldView().getScene().getBaseY();
                
                // Check if the point is within scene bounds
                if (localX >= 0 && localX < 104 && localY >= 0 && localY < 104) {
                    // Check for complete blocking (walls, etc.)
                    int flags = collisionData.getFlags()[localX][localY];
                    
                    // Check for major obstacles (full collision flags)
                    if ((flags & 0x1280180) != 0) { // Major blocking flags
                        return false;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            // If collision checking fails, assume reachable (defensive programming)
            log.debug("Failed to check line of sight: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Finds the best combat target from a list of NPC names, considering reachability.
     * Enhanced NPC selection that filters unreachable targets.
     * 
     * @param npcNames array of NPC names to search for
     * @param playerLocation the player's current world location
     * @return the best reachable NPC for combat, or null if none found
     */
    public NPC findBestCombatTarget(String[] npcNames, WorldPoint playerLocation) {
        if (npcNames == null || npcNames.length == 0 || playerLocation == null) {
            return null;
        }

        // Get all potential targets using existing filtering logic
        List<NPC> candidates = findAllValidCombatNpcs(npcNames);
        
        if (candidates.isEmpty()) {
            return null;
        }

        // Apply reachability filtering
        List<NPC> reachableTargets = candidates.stream()
            .filter(npc -> isNpcReachable(npc, playerLocation))
            .collect(Collectors.toList());
        
        if (reachableTargets.isEmpty()) {
            log.debug("No reachable NPCs found from {} candidates", candidates.size());
            return null;
        }

        log.debug("Found {} reachable NPCs out of {} total candidates", reachableTargets.size(), candidates.size());
        
        // Sort by preference: closer distance is better, but consider screen visibility
        return reachableTargets.stream()
            .min(Comparator.comparingDouble(npc -> calculateTargetScore(npc, playerLocation)))
            .orElse(null);
    }

    /**
     * Finds all valid combat NPCs using the same filtering logic as CombatTask.
     * 
     * @param npcNames array of NPC names to search for
     * @return list of valid combat NPCs
     */
    private List<NPC> findAllValidCombatNpcs(String[] npcNames) {
        return entityService.getAllInteractables()
            .filter(interactable -> interactable instanceof NpcEntity)
            .map(interactable -> ((NpcEntity) interactable).getNpc())
            .filter(npc -> {
                // Apply the same filtering logic as CombatTask
                if (npc.getName() == null) {
                    return false;
                }
                
                // Check if NPC name matches our target list
                boolean nameMatches = Arrays.stream(npcNames)
                    .anyMatch(targetName -> npc.getName().toLowerCase().contains(targetName.toLowerCase().trim()));
                if (!nameMatches) {
                    return false;
                }
                
                // Only exclude NPCs that are definitely dead (health ratio exactly 0 AND in combat)
                if (npc.getHealthRatio() == 0 && npc.getInteracting() != null) {
                    return false; // NPC is dead (health 0 while in combat)
                }
                
                // Skip NPCs already in combat with another player (not us)
                Player localPlayer = gameStateService.getClient().getLocalPlayer();
                if (npc.getInteracting() != null && npc.getInteracting() != localPlayer) {
                    return false;
                }
                
                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculates a target score for NPC selection prioritization.
     * Lower scores are better (closer/more desirable targets).
     * 
     * @param npc the NPC to score
     * @param playerLocation the player's current location
     * @return target score (lower is better)
     */
    private double calculateTargetScore(NPC npc, WorldPoint playerLocation) {
        if (npc == null || playerLocation == null) {
            return Double.MAX_VALUE;
        }

        WorldPoint npcLocation = npc.getWorldLocation();
        if (npcLocation == null) {
            return Double.MAX_VALUE;
        }

        // Base score is world distance
        double score = playerLocation.distanceTo(npcLocation);
        
        // Add bonus for NPCs already being attacked by the player (continue current fight)
        Player localPlayer = gameStateService.getClient().getLocalPlayer();
        if (localPlayer != null && npc.equals(localPlayer.getInteracting())) {
            score -= 5.0; // Strong preference for current target
        }
        
        // Add slight penalty for NPCs that are in combat (but not with the player)
        if (npc.getInteracting() != null && !npc.equals(localPlayer.getInteracting())) {
            score += 2.0; // Slight penalty for NPCs in combat with others
        }

        // Check if NPC is on screen for better targeting
        try {
            if (npc.getConvexHull() != null) {
                score -= 1.0; // Bonus for visible NPCs
            }
        } catch (Exception e) {
            // Ignore errors in visibility checking
        }
        
        return score;
    }
} 