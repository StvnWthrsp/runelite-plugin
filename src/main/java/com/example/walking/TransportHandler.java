package com.example.walking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import shortestpath.Transport;
import shortestpath.TransportType;

import com.example.ActionService;
import com.example.GameService;
import com.example.HumanizerService;

import java.util.List;
import java.util.Objects;

/**
 * Handles transport-related walking logic including teleports and other long-distance movement.
 */
@Slf4j
public class TransportHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final WalkingUtil walkingUtil;
    
    private long transportStartTime = 0;

    public TransportHandler(Client client, GameService gameService, ActionService actionService, HumanizerService humanizerService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.walkingUtil = new WalkingUtil(client, actionService);
    }

    /**
     * Handles transport step in the walking path.
     * 
     * @param path the complete walking path
     * @param pathIndex current index in the path
     * @param currentLocation player's current location
     * @return true if transport was handled, false to continue with normal walking
     */
    public boolean handleTransportStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        if (pathIndex >= path.size()) {
            return false;
        }

        // Check for transport steps using proper node detection
        log.info("DEBUG: Checking for transport step at pathIndex {}", pathIndex);
        
        if (pathIndex + 1 < path.size()) {
            WorldPoint currentStep = path.get(pathIndex);
            WorldPoint nextStep = path.get(pathIndex + 1);
            int distance = currentStep.distanceTo(nextStep);
            
            // DEBUG: Add detailed logging for distance calculation
            log.info("DEBUG: Path step {} -> {}, planes: {} -> {}, calculated distance: {}", 
                    currentStep, nextStep, currentStep.getPlane(), nextStep.getPlane(), distance);
            
            // Check for broken distance calculation (Integer.MAX_VALUE indicates bug)
            if (distance == Integer.MAX_VALUE) {
                log.warn("DEBUG: Distance calculation returned MAX_VALUE - this indicates a bug in distance calculation between planes");
                // Calculate 2D distance manually for plane changes
                int dx = Math.abs(nextStep.getX() - currentStep.getX());
                int dy = Math.abs(nextStep.getY() - currentStep.getY());
                int distance2D = (int) Math.sqrt(dx * dx + dy * dy);
                log.info("DEBUG: Manual 2D distance calculation: {}", distance2D);
                distance = distance2D; // Use 2D distance instead
            }
            
            // Check for teleports - prioritize large distance OR plane change with medium distance
            if (distance > 20 || (currentStep.getPlane() != nextStep.getPlane() && distance > 10)) {
                log.info("Detected possible teleport from {} to {} (distance: {}, plane change: {})", 
                        currentStep, nextStep, distance, currentStep.getPlane() != nextStep.getPlane());
                
                return executeTransport(currentStep, nextStep, currentLocation);
            }
        }

        return false;
    }

    /**
     * Execute transport (teleport) from origin to destination.
     * 
     * @param origin transport origin point
     * @param destination transport destination point
     * @param currentLocation player's current location
     * @return true if transport was executed or player needs to move to origin
     */
    private boolean executeTransport(WorldPoint origin, WorldPoint destination, WorldPoint currentLocation) {
        // Check if we're close to the transport origin
        if (currentLocation.distanceTo(origin) <= 2) {
            log.info("At transport origin, executing transport to {}", destination);
            
            // Determine teleport type based on destination
            String teleportType = determineTeleportType(destination);
            
            if (teleportType != null) {
                log.info("Using teleport: {}", teleportType);
                boolean success = actionService.castSpell(teleportType);
                
                if (success) {
                    transportStartTime = System.currentTimeMillis();
                    return true; // Transport executed successfully
                } else {
                    log.warn("Failed to execute teleport {}, falling back to walking", teleportType);
                    return false; // Let normal walking handle this
                }
            } else {
                log.warn("Could not determine teleport type for destination {}, falling back to walking", destination);
                return false;
            }
        } else {
            // Walk to the transport origin first
            log.info("Walking to transport origin at {}", origin);
            walkingUtil.walkTo(origin);
            return true; // Handled - walking to origin
        }
    }

    /**
     * Determine the teleport type based on destination coordinates.
     * 
     * @param destination the destination point
     * @return teleport spell name or null if no suitable teleport
     */
    private String determineTeleportType(WorldPoint destination) {
        // Only return teleport types for actual long-distance teleports
        // This method should NOT be used for stairs/ladders/doors!
        
        // Varrock teleport area (around Varrock square)
        if (destination.getX() >= 3200 && destination.getX() <= 3230 && 
            destination.getY() >= 3420 && destination.getY() <= 3450) {
            return "Varrock Teleport";
        }
        
        // Lumbridge teleport area (around Lumbridge castle)
        if (destination.getX() >= 3200 && destination.getX() <= 3230 && 
            destination.getY() >= 3200 && destination.getY() <= 3230) {
            return "Lumbridge Teleport";
        }
        
        // Falador teleport area (around Falador square)
        if (destination.getX() >= 2960 && destination.getX() <= 2990 && 
            destination.getY() >= 3380 && destination.getY() <= 3410) {
            return "Falador Teleport";
        }
        
        // Add more teleport destinations as needed
        
        return null; // No suitable teleport found
    }

    /**
     * Check if currently executing transport and handle completion.
     * 
     * @return true if transport is in progress, false if completed or not active
     */
    public boolean isTransportInProgress() {
        if (transportStartTime > 0) {
            long elapsed = System.currentTimeMillis() - transportStartTime;
            if (elapsed > 30000) { // 30 second timeout
                log.warn("Transport timeout reached, considering transport complete");
                transportStartTime = 0;
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Complete the current transport operation.
     */
    public void completeTransport() {
        transportStartTime = 0;
    }

}