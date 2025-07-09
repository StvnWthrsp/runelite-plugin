package com.example.walking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

import com.example.ActionService;
import com.example.GameService;
import com.example.HumanizerService;

import java.util.List;
import java.util.Objects;

/**
 * Handles stairs and ladder-related walking logic including detection and interaction.
 */
@Slf4j
public class StairsHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final WalkingUtil walkingUtil;

    public StairsHandler(Client client, GameService gameService, ActionService actionService, HumanizerService humanizerService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.walkingUtil = new WalkingUtil(client, actionService);
    }

    /**
     * Handles stairs step in the walking path.
     * 
     * @param path the complete walking path
     * @param pathIndex current index in the path
     * @param currentLocation player's current location
     * @return true if stairs were handled, false to continue with normal walking
     */
    public boolean handleStairsStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        if (pathIndex + 1 >= path.size()) {
            return false;
        }

        WorldPoint currentStep = path.get(pathIndex);
        WorldPoint nextStep = path.get(pathIndex + 1);
        int distance = currentStep.distanceTo(nextStep);
        
        // Check for plane changes with short distance (stairs/ladders only)
        if (currentStep.getPlane() != nextStep.getPlane() && distance <= 10) {
            log.info("DEBUG: Detected short-distance plane change from {} to {} (distance: {}) - this should be handled as stairs/ladder", 
                    currentStep, nextStep, distance);
            
            // Check if we're close to the stair location
            if (currentLocation.distanceTo(currentStep) <= 2) {
                log.info("At stair location, looking for stairs/ladder object");
                GameObject stairObject = findStairObjectGeneric(currentStep);
                if (stairObject != null) {
                    String action = nextStep.getPlane() > currentStep.getPlane() ? "Climb-up" : "Climb-down";
                    log.info("Found stair object {} at {}, using action: {}", stairObject.getId(), currentStep, action);
                    return handleStairsOrLadder(stairObject, action);
                } else {
                    log.info("Stair object detected but is not GameObject, continuing with normal walking.");
                }
            } else {
                // Walk to the stair location first
                log.info("Walking to stair location at {}", currentStep);
                walkingUtil.walkTo(currentStep);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Find a stair object at the specified location.
     * 
     * @param location the location to search for stairs
     * @return GameObject representing stairs/ladder or null if not found
     */
    private GameObject findStairObjectGeneric(WorldPoint location) {
        Scene scene = client.getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        
        int baseX = client.getWorldView(-1).getBaseX();
        int baseY = client.getWorldView(-1).getBaseY();
        int sceneX = location.getX() - baseX;
        int sceneY = location.getY() - baseY;
        int plane = location.getPlane();
        
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || 
            sceneY < 0 || sceneY >= Constants.SCENE_SIZE ||
            plane < 0 || plane >= tiles.length) {
            return null;
        }
        
        Tile tile = tiles[plane][sceneX][sceneY];
        if (tile == null) {
            return null;
        }
        
        // Check all game objects on this tile
        for (GameObject gameObject : tile.getGameObjects()) {
            if (gameObject != null && isStairObjectGeneric(gameObject)) {
                return gameObject;
            }
        }
        
        return null;
    }

    /**
     * Check if a game object is a stair or ladder.
     * 
     * @param gameObject the object to check
     * @return true if it's a stair/ladder, false otherwise
     */
    private boolean isStairObjectGeneric(GameObject gameObject) {
        if (gameObject == null) {
            return false;
        }
        
        int id = gameObject.getId();
        
        // Common stair and ladder IDs
        return id == ObjectID.SPIRALSTAIRSMIDDLE || id == ObjectID.SPIRALSTAIRS ||
               id == ObjectID.SPIRALSTAIRSDOWN || id == ObjectID.SPIRALSTAIRSTOP ||
               isLumbridgeStaircase(gameObject);
    }

    /**
     * Check if this is a Lumbridge castle staircase.
     * 
     * @param gameObject the object to check
     * @return true if it's a Lumbridge staircase, false otherwise
     */
    private boolean isLumbridgeStaircase(GameObject gameObject) {
        if (gameObject == null) {
            return false;
        }
        
        WorldPoint location = gameObject.getWorldLocation();
        
        // Lumbridge castle staircase locations (approximate)
        return (location.getX() >= 3205 && location.getX() <= 3210 &&
                location.getY() >= 3205 && location.getY() <= 3230) ||
               (location.getX() >= 3203 && location.getX() <= 3208 &&
                location.getY() >= 3224 && location.getY() <= 3229);
    }

    /**
     * Handle interaction with stairs or ladder.
     * 
     * @param stairObject the stair/ladder object
     * @param action the action to perform ("Climb-up" or "Climb-down")
     * @return true if handled successfully, false otherwise
     */
    private boolean handleStairsOrLadder(GameObject stairObject, String action) {
        log.info("Interacting with stair object {} using action: {}", stairObject.getId(), action);
        
        // Check if this is a Lumbridge staircase that needs special handling
        if (isLumbridgeStaircase(stairObject)) {
            return handleLumbridgeStaircase(stairObject, action);
        }
        
        // Generic stair interaction
        actionService.interactWithGameObject(stairObject, action);
        return true;
    }

    /**
     * Handle Lumbridge castle staircase interaction.
     * 
     * @param stairObject the staircase object
     * @param action the action to perform
     * @return true if handled successfully, false otherwise
     */
    private boolean handleLumbridgeStaircase(GameObject stairObject, String action) {
        log.info("Handling Lumbridge staircase at {} with action: {}", stairObject.getWorldLocation(), action);
        
        // Lumbridge staircases might have different action names
        String[] possibleActions = {action, "Climb-up", "Climb-down", "Climb", "Use"};
        
        for (String possibleAction : possibleActions) {
            log.debug("Trying action: {} on Lumbridge staircase", possibleAction);
            actionService.interactWithGameObject(stairObject, possibleAction);
            break; // Try the first action for now
        }
        
        return true;
    }

}