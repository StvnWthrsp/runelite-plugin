package com.example;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.GameObject;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.pathfinder.TransportNode;
import shortestpath.pathfinder.Node;
import shortestpath.Transport;
import shortestpath.TransportType;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.Constants;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class WalkTask implements BotTask {

    private enum WalkState {
        IDLE,
        CALCULATING_PATH,
        WALKING,
        EXECUTING_TELEPORT,
        OPENING_DOOR,
        USING_STAIRS,
        WAITING_FOR_TRANSPORT,
        FAILED,
        FINISHED
    }

    private static final int RETRY_LIMIT = 5;

    @Getter
    private final WorldPoint destination;
    private final RunepalPlugin plugin;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;
    private final GameService gameService;
    private final HumanizerService humanizerService;

    private WalkState currentState = null;
    private int delayTicks = 0;
    private int retries = 0;
    private List<WorldPoint> path;
    private List<Node> nodePath;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;
    private final ScheduledExecutorService scheduler;
    private final ActionService actionService;
    
    // Transport execution state
    private Object pendingTransport;
    private long transportStartTime;
    private static final long TRANSPORT_TIMEOUT_MS = 30000; // 30 seconds
    private int pathIndex = 0;
    private TileObject doorToOpen;
    private GameObject stairsToUse;
    private String actionToTake;

    public WalkTask(RunepalPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination, ActionService actionService, GameService gameService, HumanizerService humanizerService) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.pathfinderConfig = pathfinderConfig;
        this.destination = destination;
        this.gameService = gameService;
        this.humanizerService = humanizerService;
        ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("walk-task-%d").build();
        this.pathfinderExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.actionService = actionService;
    }

    @Override
    public void onStart() {
        log.info("Starting enhanced walk task to {}", destination);
        this.currentState = WalkState.IDLE;
        this.pathIndex = 0;
    }

    @Override
    public void onLoop() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        switch (currentState) {
            case IDLE:
                calculatePath();
                break;
            case CALCULATING_PATH:
                checkPathCalculation();
                break;
            case WALKING:
                handleWalking();
                break;
            case EXECUTING_TELEPORT:
                handleTeleportExecution();
                break;
            case OPENING_DOOR:
                handleDoorOpening();
                break;
            case USING_STAIRS:
                handleStairs();
                break;
            case WAITING_FOR_TRANSPORT:
                handleTransportWait();
                break;
            default:
                break;
        }
    }

    private void calculatePath() {
        WorldPoint start = gameService.getPlayerLocation();
        if (start.equals(destination)) {
            log.info("Already at destination.");
            currentState = WalkState.FINISHED;
            return;
        }

        log.info("Calculating enhanced path from {} to {}", start, destination);
        int startPacked = WorldPointUtil.packWorldPoint(start);
        int endPacked = WorldPointUtil.packWorldPoint(destination);

        pathfinderConfig.refresh();
        pathfinder = new Pathfinder(pathfinderConfig, startPacked, Collections.singleton(endPacked));
        pathfinderFuture = pathfinderExecutor.submit(pathfinder);
        currentState = WalkState.CALCULATING_PATH;
    }

    private void checkPathCalculation() {
        if (pathfinder == null || !pathfinder.isDone()) {
            return;
        }

        List<Integer> resultPath = pathfinder.getPath();
        if (resultPath.isEmpty()) {
            log.warn("No path found to {}", destination);
            currentState = WalkState.FAILED;
            return;
        }

        this.path = resultPath.stream()
                .map(WorldPointUtil::unpackWorldPoint)
                .collect(Collectors.toList());
        
        // Store the node path for proper transport detection
        this.nodePath = getNodePath();
        
        log.info("Enhanced path calculated with {} steps (including potential teleports/doors).", this.path.size());
        currentState = WalkState.WALKING;
        pathIndex = 0;
    }

    private void handleWalking() {
        WorldPoint currentLocation = gameService.getPlayerLocation();

        // Check if we're already walking to a destination
        if (client.getLocalDestinationLocation() != null) {
            return;
        }

        // Check if we're already at the destination, or not at destination after reaching end of path
        if (currentLocation.equals(destination)) {
            log.info("Already at destination.");
            currentState = WalkState.FINISHED;
            return;
        }
        if (pathIndex >= path.size()) {
            log.warn("Reached end of path but not at destination. Recalculating...");
            currentState = WalkState.IDLE;
            return;
        }

        // Update path index based on current location
        log.debug("DEBUG: Before updatePathIndex - pathIndex: {}, currentLocation: {}", pathIndex, currentLocation);
        updatePathIndex(currentLocation);
        log.debug("DEBUG: After updatePathIndex - pathIndex: {}", pathIndex);

        // TODO: Rewrite transport steps using shortest-path Transport information. See getAllTransportPointsInPath().
        // Check for transport steps using proper node detection
//        log.info("DEBUG: Checking for transport step at pathIndex {}, isTransportStep: {}", pathIndex, isTransportStep(pathIndex));
//        if (pathIndex < path.size() && isTransportStep(pathIndex)) {
//            WorldPoint currentStep = path.get(pathIndex);
//            Transport transport = getTransportInfo(pathIndex);
//
//            log.info("DEBUG: Transport info - type: {}, displayInfo: {}, transport: {}",
//                    transport != null ? transport.getType() : "null",
//                    transport != null ? transport.getDisplayInfo() : "null",
//                    transport != null ? "not null" : "null");
//
//            if (transport != null) {
//                log.info("Detected transport step: {} at {}", transport.getDisplayInfo(), currentStep);
//
//                // Check if we're close to the transport origin
//                if (currentLocation.distanceTo(currentStep) <= 2) {
//                    log.info("At transport origin, executing transport: {}", transport.getDisplayInfo());
//                    executeTransportInteraction(transport, currentStep);
//                    return;
//                } else {
//                    // Walk to the transport origin first
//                    log.info("Walking to transport origin at {}", currentStep);
//                    walkTo(currentStep);
//                    return;
//                }
//            }
//        }
        
        // Legacy detection for backwards compatibility
        if (pathIndex + 1 < path.size()) {
            WorldPoint currentStep = path.get(pathIndex);
            WorldPoint nextStep = path.get(pathIndex + 1);
            int distance = currentStep.distanceTo(nextStep);
            
            // DEBUG: Add detailed logging for distance calculation
            log.debug("DEBUG: Path step {} -> {}, planes: {} -> {}, calculated distance: {}",
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
            
            // First check for teleports - prioritize large distance OR plane change with medium distance
            // Teleports can change planes and have varying distances depending on the type
            if (distance > 20 || (currentStep.getPlane() != nextStep.getPlane() && distance > 10)) {
                log.info("Detected possible teleport from {} to {} (distance: {}, plane change: {})", 
                        currentStep, nextStep, distance, currentStep.getPlane() != nextStep.getPlane());
                
                // Check if we're close to the current step (teleport origin)
                if (currentLocation.distanceTo(currentStep) <= 2) {
                    log.info("At teleport origin, executing transport to {}", nextStep);
                    executeTransport(currentStep, nextStep);
                    return;
                } else {
                    // Walk to the teleport origin first
                    log.info("Walking to teleport origin at {}", currentStep);
                    walkTo(currentStep);
                    return;
                }
            }
            
            // Then check for plane changes with short distance (stairs/ladders only)
            if (currentStep.getPlane() != nextStep.getPlane() && distance <= 10) {
                log.info("DEBUG: Detected short-distance plane change from {} to {} (distance: {}) - this should be handled as stairs/ladder", currentStep, nextStep, distance);
                
                  // Check if we're close to the stair location
                  if (currentLocation.distanceTo(currentStep) <= 2) {
                      log.info("At stair location, looking for stairs/ladder object");
                      GameObject stairObject = findStairObjectGeneric(currentStep);
                      if (stairObject != null) {
                          String action = nextStep.getPlane() > currentStep.getPlane() ? "Climb-up" : "Climb-down";
                          log.info("Found stair object {} at {}, using action: {}", stairObject.getId(), currentStep, action);
                          stairsToUse = stairObject;
                          actionToTake = action;
                          currentState = WalkState.USING_STAIRS;
                          delayTicks = humanizerService.getRandomDelay(1, 5);
                          return;
                      } else {
                            log.info("Stair object detected but is not GameObject, continuing with normal walking.");
                      }
                } else {
                    // Walk to the stair location first
                    log.info("Walking to stair location at {}", currentStep);
                    walkTo(currentStep);
                    return;
                }
            }
        }

        // Check for doors blocking our path using collision data
        log.info("Current location: {}", currentLocation);
        log.info("pathIndex location: {}", path.get(pathIndex));
        DoorInfo doorInfo = findDoorBlockingPath(currentLocation);
        if (doorInfo != null) {
            log.info("Door detected at {} blocking path to {}", doorInfo.doorLocation, doorInfo.targetLocation);
            
            // Walk to the door first if we're not adjacent
            if (currentLocation.distanceTo(doorInfo.doorLocation) > 1) {
                log.info("Walking to door at {}, stopping at {}", doorInfo.doorLocation, doorInfo.lastUnblockedPoint);
                walkTo(doorInfo.lastUnblockedPoint);
                return;
            }
            
            // We're adjacent to the door, try to interact with it
            doorToOpen = findDoorObject(doorInfo.doorLocation);
            if ((doorToOpen != null && doorToOpen instanceof GameObject) || (doorToOpen != null && doorToOpen instanceof WallObject)) {
                log.debug("Found door object {} at {}, attempting to open", doorToOpen.getId(), doorInfo.doorLocation);
                currentState = WalkState.OPENING_DOOR;
                delayTicks = humanizerService.getRandomDelay(1, 5);
                return;
            } else {
                log.warn("Door object detected but is not GameObject or WallObject, continuing with normal walking.");
            }
        }

        // Normal walking - proceed to next point in path
        if (pathIndex < path.size()) {
            WorldPoint target = getNextMinimapTarget();
            log.info("DEBUG: Normal walking - pathIndex: {}, target: {}, currentLocation: {}", pathIndex, target, currentLocation);
            walkTo(target);
            delayTicks = humanizerService.getRandomDelay(3, 10);
        } else {
            log.warn("DEBUG: pathIndex {} >= path.size() {}, cannot proceed with walking", pathIndex, path.size());
        }
    }


    private void executeTransport(WorldPoint origin, WorldPoint destination) {
        log.info("Executing transport from {} to {}", origin, destination);
        
        // Determine teleport type based on destination
        String teleportType = determineTeleportType(destination);
        
        if (teleportType != null) {
            // Ensure spellbook is open
            Widget magicTab = plugin.getClient().getWidget(InterfaceID.MagicSpellbook.UNIVERSE); // Standard spellbook
            if (magicTab == null || magicTab.isHidden()) {
                log.warn("Could not find magic interface, attempting to open it.");
                actionService.openMagicInterface();
                delayTicks = humanizerService.getShortDelay();
                return;
            }

            log.info("Using teleport: {}", teleportType);
            boolean success = actionService.castSpell(teleportType);
            
            if (success) {
                currentState = WalkState.EXECUTING_TELEPORT;
                transportStartTime = System.currentTimeMillis();
                delayTicks = humanizerService.getRandomDelay(5, 15); // Wait for teleport to complete
            } else {
                log.warn("Failed to execute teleport {}, falling back to walking", teleportType);
                // Fall back to normal walking if teleport fails
                walkTo(destination);
            }
        } else {
            log.warn("Could not determine teleport type for destination {}, falling back to walking", destination);
            walkTo(destination);
        }
    }

    private String determineTeleportType(WorldPoint destination) {
        // Only return teleport types for actual long-distance teleports
        // This method should NOT be used for stairs/ladders/doors!
        
        // Varrock teleport area (around Varrock square)
        if (destination.getX() >= 3200 && destination.getX() <= 3230 && 
            destination.getY() >= 3420 && destination.getY() <= 3450) {
            return "Varrock Teleport";
        }
        
        // Falador teleport area (around Falador square)
        if (destination.getX() >= 2950 && destination.getX() <= 2980 && 
            destination.getY() >= 3370 && destination.getY() <= 3400) {
            return "Falador Teleport";
        }
        
        // Lumbridge home teleport area - but only for actual teleports, not stairs!
        // We need to be very specific here to avoid false positives
        if (destination.getX() >= 3218 && destination.getX() <= 3222 && 
            destination.getY() >= 3218 && destination.getY() <= 3222 &&
            destination.getPlane() == 0) { // Only ground floor for home teleport
            return "Home Teleport";
        }
        
        // Add more teleport destinations as needed
        
        return null; // Unknown destination, can't teleport
    }

    private void handleTeleportExecution() {
        WorldPoint currentLocation = gameService.getPlayerLocation();
        
        // Check if teleport completed by seeing if we're at the expected destination
        if (pathIndex + 1 < path.size()) {
            WorldPoint expectedDestination = path.get(pathIndex + 1);
            if (currentLocation.distanceTo(expectedDestination) <= 5) {
                log.info("Teleport completed successfully, arrived near {}", expectedDestination);
                pathIndex += 2; // Skip both the origin and destination steps
                currentState = WalkState.WALKING;
                return;
            }
        }
        
        // Check for timeout
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Teleport execution timed out, falling back to walking");
            currentState = WalkState.WALKING;
            return;
        }
        
        // Continue waiting for teleport to complete
        delayTicks = humanizerService.getRandomDelay(2, 5);
    }

    /**
     * Finds a door that is blocking our path by checking collision data
     */
    private DoorInfo findDoorBlockingPath(WorldPoint currentLocation) {
        log.debug("Finding door blocking path from {} to {}", currentLocation, path.get(pathIndex));
        // Look ahead in our path to find where we might be blocked
        for (int i = pathIndex; i < Math.min(pathIndex + 15, path.size()); i++) {
            WorldPoint pathPoint = path.get(i);
            log.trace("Checking if movement between {} and {} is blocked by a door", currentLocation, pathPoint);
            // Check if we can move from current point to this path point
            if (isMovementBlockedByDoor(currentLocation, pathPoint)) {
                log.debug("Movement between current location{} and {} is blocked by a door", currentLocation, pathPoint);
                log.debug("Last reachable point: {}", currentLocation);
                return new DoorInfo(pathPoint, pathPoint, currentLocation);
            }
            
            // Also check the previous point to this point
            if (i > 0) {
                WorldPoint prevPoint = path.get(i - 1);
                if (isMovementBlockedByDoor(prevPoint, pathPoint)) {
                    log.debug("Movement between {} and {} is blocked by a door", currentLocation, pathPoint);
                    log.debug("Last reachable point: {}", path.get(i - 1));
                    return new DoorInfo(pathPoint, pathPoint, path.get(i - 1));
                }
            }
        }
        
        return null;
    }

    /**
     * Checks if movement between two adjacent points is blocked by a door using collision data
     */
    private boolean isMovementBlockedByDoor(WorldPoint from, WorldPoint to) {
        // Only check adjacent tiles
        if (from.distanceTo(to) > 1) {
            return false;
        }
        
        try {
            // Get collision data from the client
            CollisionData[] collisionData = client.getCollisionMaps();
            if (collisionData == null) {
                return false;
            }
            
            int plane = from.getPlane();
            if (plane < 0 || plane >= collisionData.length) {
                return false;
            }
            
            CollisionData planeCollision = collisionData[plane];
            if (planeCollision == null) {
                return false;
            }
            
            // Calculate direction from 'from' to 'to'
            int dx = to.getX() - from.getX();
            int dy = to.getY() - from.getY();
            
            // Convert world coordinates to collision map coordinates
            int baseX = client.getTopLevelWorldView().getBaseX();
            int baseY = client.getTopLevelWorldView().getBaseY();
            int localX = from.getX() - baseX;
            int localY = from.getY() - baseY;
            
            // Check if coordinates are within bounds
            if (localX < 0 || localX >= 104 || localY < 0 || localY >= 104) {
                return false;
            }
            
            int[][] flags = planeCollision.getFlags();
            int currentFlags = flags[localX][localY];
            
            // Check for door-specific collision flags based on direction
            if (dx == 1 && dy == 0) { // Moving east
                // Check if there's a wall blocking east movement
                return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0;
            } else if (dx == -1 && dy == 0) { // Moving west
                // Check if there's a wall blocking west movement
                return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0;
            } else if (dx == 0 && dy == 1) { // Moving north
                // Check if there's a wall blocking north movement
                return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0;
            } else if (dx == 0 && dy == -1) { // Moving south
                // Check if there's a wall blocking south movement
                return (currentFlags & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0;
            }
            
            return false;
        } catch (Exception e) {
            log.warn("Error checking collision data: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Finds the actual door GameObject at the specified location
     */
    private TileObject findDoorObject(WorldPoint location) {
        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = location.getPlane();
        
        // Convert world coordinates to scene coordinates
        int baseX = client.getTopLevelWorldView().getBaseX();
        int baseY = client.getTopLevelWorldView().getBaseY();
        int sceneX = location.getX() - baseX;
        int sceneY = location.getY() - baseY;
        
        // Check if coordinates are within scene bounds
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || 
            sceneY < 0 || sceneY >= Constants.SCENE_SIZE || 
            z < 0 || z >= Constants.MAX_Z) {
            return null;
        }
        
        Tile tile = tiles[z][sceneX][sceneY];
        if (tile == null) {
            return null;
        }
        
        // Check all game objects on this tile and adjacent tiles
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int checkX = sceneX + dx;
                int checkY = sceneY + dy;
                
                if (checkX >= 0 && checkX < Constants.SCENE_SIZE && 
                    checkY >= 0 && checkY < Constants.SCENE_SIZE) {
                    
                    Tile checkTile = tiles[z][checkX][checkY];
                    if (checkTile != null) {
                        for (GameObject obj : checkTile.getGameObjects()) {
                            if (obj != null && isClosedDoor(obj)) {
                                return obj;
                            }
                        }
                        
                        // Also check wall objects - but we need to handle them differently
                        WallObject wallObj = checkTile.getWallObject();
                        if (wallObj != null && isClosedDoor(wallObj)) {
                            return wallObj;
                        }
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Checks if a TileObject is a door
     */
    private boolean isClosedDoor(TileObject obj) {
        if (obj == null) {
            return false;
        }
        
        // Check for common door object IDs and names
        int id = obj.getId();
        
        // Common door IDs (this list may need to be expanded)
        int[] doorIds = {
            1516, 1517, 1518, 1519, 1520, 1521, 1522, 1523, 1524, 1525, // Basic doors
            1530, 1531, 1532, 1533, 1534, 1535, 1536, 1537, 1538, 1539, // More doors
            11707, 11708, 11709, 11710, 11711, 11712, 11713, 11714, 11715, 11716, // Fancy doors
            9398, 9399, 9400, 9401, 9402, 9403, 9404, 9405, 9406, 9407, // Door variations
            24306, 24307, 24308, 24309, 24310, 24311, 24312, 24313, 24314, 24315, // More door variations
            ObjectID.FAI_VARROCK_DOOR, ObjectID.ELFDOOR, 11780, 50048
        };
        
        for (int doorId : doorIds) {
            if (id == doorId) {
                return true;
            }
        }
        
        // Check object name if we have access to it
        // This is a fallback for doors not in our ID list
        return false;
    }

    /**
     * Helper class to store door information
     */
    private static class DoorInfo {
        final WorldPoint doorLocation;
        final WorldPoint targetLocation;
        final WorldPoint lastUnblockedPoint;
        
        DoorInfo(WorldPoint doorLocation, WorldPoint targetLocation, WorldPoint lastUnblockedPoint) {
            this.doorLocation = doorLocation;
            this.targetLocation = targetLocation;
            this.lastUnblockedPoint = lastUnblockedPoint;
        }
    }

    private void updatePathIndex(WorldPoint currentLocation) {
        // Update path index to current position - be more aggressive about advancing
        int closestIndex = pathIndex;
        int closestDistance = Integer.MAX_VALUE;
        
        log.debug("DEBUG: updatePathIndex - currentLocation: {}, pathIndex: {}, path.size(): {}", currentLocation, pathIndex, path.size());
        
        // Look for the closest point in the path ahead of us (expanded range)
        for (int i = pathIndex; i < Math.min(pathIndex + 15, path.size()); i++) {
            int distance = path.get(i).distanceTo(currentLocation);
            log.debug("DEBUG: Path step {}: {}, distance: {}", i, path.get(i), distance);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }
        
        log.debug("DEBUG: Closest index: {}, closest distance: {}, current pathIndex: {}", closestIndex, closestDistance, pathIndex);
        
        // More aggressive update conditions:
        // 1. If we're very close (distance <= 2) and it's a later step, update
        // 2. If we're reasonably close (distance <= 5) and significantly further in path, update
        if ((closestDistance <= 2 && closestIndex > pathIndex) || 
            (closestDistance <= 5 && closestIndex > pathIndex + 3)) {
            pathIndex = closestIndex;
            log.debug("DEBUG: Updated path index to {}, current location: {}", pathIndex, path.get(pathIndex));
        } else {
            log.debug("DEBUG: No path index update - closestDistance: {}, closestIndex: {}, pathIndex: {}", closestDistance, closestIndex, pathIndex);
        }
    }

    private WorldPoint getNextMinimapTarget() {
        WorldPoint currentLocation = gameService.getPlayerLocation();
        log.info("DEBUG: getNextMinimapTarget - pathIndex: {}, path.size(): {}, currentLocation: {}", pathIndex, path.size(), currentLocation);
        
        // Start from furthest points and work backwards, but skip points we're already very close to
        for (int i = path.size() - 1; i >= pathIndex; i--) {
            WorldPoint point = path.get(i);
            int distanceToPoint = currentLocation.distanceTo(point);
            
            // Skip points we're already very close to (avoid clicking current location)
            if (distanceToPoint <= 1) {
                log.info("DEBUG: Skipping target at index {} (too close, distance: {}): {}", i, distanceToPoint, point);
                continue;
            }
            
            if (isPointOnMinimap(point)) {
                log.info("DEBUG: Selected minimap target at index {} (distance: {}): {}", i, distanceToPoint, point);
                return point;
            }
        }
        
        // Fallback to next path step if we can't find a distant minimap target
        if (pathIndex + 1 < path.size()) {
            WorldPoint fallback = path.get(pathIndex + 1);
            log.info("DEBUG: Using fallback target at pathIndex+1 {}: {}", pathIndex + 1, fallback);
            return fallback;
        }
        
        // Last resort - current path position
        if (pathIndex < path.size()) {
            WorldPoint lastResort = path.get(pathIndex);
            log.info("DEBUG: Using last resort target at pathIndex {}: {}", pathIndex, lastResort);
            return lastResort;
        }
        
        log.warn("DEBUG: No minimap target found - pathIndex: {}, path.size(): {}", pathIndex, path.size());
        return null;
    }

    private boolean isPointOnMinimap(WorldPoint point) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), point);
        if (localPoint == null) {
            return false;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
        if (minimapPoint == null) {
            return false;
        }

        Widget minimapWidget = getMinimapDrawWidget();
        if (minimapWidget == null) {
            return false;
        }

        java.awt.Rectangle bounds = minimapWidget.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        int radius = Math.min(bounds.width / 2, bounds.height / 2);

        int distanceSq = (minimapPoint.getX() - centerX) * (minimapPoint.getX() - centerX) +
                         (minimapPoint.getY() - centerY) * (minimapPoint.getY() - centerY);

        int radiusSq = (radius - 7) * (radius - 7);

        return distanceSq <= radiusSq;
    }

    private Widget getMinimapDrawWidget() {
        Widget minimapWidget = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        }
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        }
        return minimapWidget;
    }

    @Override
    public void onStop() {
        log.info("Stopping enhanced walk task.");
        if (pathfinderFuture != null && !pathfinderFuture.isDone()) {
            pathfinderFuture.cancel(true);
        }
        pathfinderExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

    @Override
    public boolean isFinished() {
        return currentState == WalkState.FINISHED || currentState == WalkState.FAILED;
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    @Override
    public String getTaskName() {
        return "Enhanced Walking to " + destination.toString();
    }

    public void walkTo(WorldPoint worldPoint) {
        if (retries >= RETRY_LIMIT) {
            log.warn("Recalculating path after 5 failures.");
            currentState = WalkState.IDLE;
            retries = 0;
            return;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint != null) {
            net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
            if (minimapPoint != null) {
                log.info("Requesting walk to {} via minimap click at {}", worldPoint, minimapPoint);
                actionService.sendClickRequest(new java.awt.Point(minimapPoint.getX(), minimapPoint.getY()), true);
            } else {
                log.warn("Cannot walk to {}: not visible on minimap.", worldPoint);
                retries++;
            }
        } else {
            log.warn("Cannot walk to {}: not in scene.", worldPoint);
            retries++;
        }
    }

    private void handleDoorOpening() {
        if ((doorToOpen != null && doorToOpen instanceof GameObject) || (doorToOpen != null && doorToOpen instanceof WallObject)) {
            if (doorToOpen instanceof GameObject) {
                actionService.interactWithGameObject((GameObject) doorToOpen, "Open");
            } else {
                actionService.interactWithWallObject((WallObject) doorToOpen, "Open");
            }
            currentState = WalkState.WALKING;
            delayTicks = humanizerService.getRandomDelay(0, 2);
        } else {
            log.warn("Could not open door. Recalculating path.");
            currentState = WalkState.IDLE;
        }
    }

    private void handleStairs() {
        if (stairsToUse != null) {
            if (isLumbridgeStaircase(stairsToUse)) {
                handleLumbridgeStaircase(stairsToUse, actionToTake);
            } else {
                actionService.interactWithGameObject(stairsToUse, actionToTake);
            }
            stairsToUse = null;
            actionToTake = null;
            currentState = WalkState.IDLE;
            delayTicks = humanizerService.getRandomDelay(0, 2);
        } else {
            log.warn("Could not use stairs. Recalculating path.");
            currentState = WalkState.IDLE;
        }
    }

    private void handleTransportWait() {
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Transport wait timed out");
            currentState = WalkState.WALKING;
            pendingTransport = null;
            return;
        }

        // For doors, just wait a short time then continue
        if (System.currentTimeMillis() - transportStartTime > 3000) { // 3 seconds
            log.info("Transport wait complete, resuming walking");
            currentState = WalkState.WALKING;
            pendingTransport = null;
        }
    }
    
    /**
     * Get the node path from the pathfinder for transport detection
     * @return list of nodes in the path
     */
    private List<Node> getNodePath() {
        if (pathfinder == null) {
            log.debug("DEBUG: pathfinder is null in getNodePath");
            return null;
        }
        
        // Build the node path from the pathfinder's best node
        Node lastNode = pathfinder.getStats() != null ? getBestNode() : null;
        if (lastNode == null) {
            log.debug("DEBUG: lastNode is null in getNodePath");
            return null;
        }
        
        List<Node> nodes = new ArrayList<>();
        Node current = lastNode;
        while (current != null) {
            nodes.add(0, current);
            current = current.previous;
        }
        
        log.debug("DEBUG: Built node path with {} nodes", nodes.size());
        return nodes;
    }
    
    /**
     * Get the best node from the pathfinder (simplified version)
     * @return the best node found
     */
    private Node getBestNode() {
        // This is a simplified implementation - in a full implementation,
        // we would need access to the pathfinder's internal bestLastNode
        // For now, we'll return null and rely on legacy detection
        return null;
    }
    
    /**
     * Check if a path step requires transport interaction
     * @param stepIndex the index in the path
     * @return true if this step is a transport step
     */
    private boolean isTransportStep(int stepIndex) {
        if (nodePath == null) {
            log.debug("DEBUG: nodePath is null");
            return false;
        }
        if (stepIndex >= nodePath.size()) {
            log.debug("DEBUG: stepIndex {} >= nodePath size {}", stepIndex, nodePath.size());
            return false;
        }
        
        boolean isTransport = nodePath.get(stepIndex) instanceof TransportNode;
        log.debug("DEBUG: Step {} is TransportNode: {}", stepIndex, isTransport);
        return isTransport;
    }
    
    /**
     * Get transport information for a path step
     * @param stepIndex the index in the path
     * @return the transport info or null if not a transport step
     */
    private Transport getTransportInfo(int stepIndex) {
        if (!isTransportStep(stepIndex)) {
            return null;
        }
        
        if (stepIndex == 0) {
            return null; // First step can't be a transport
        }
        
        WorldPoint origin = path.get(stepIndex - 1);
        WorldPoint destination = path.get(stepIndex);
        
        // Look up transport from PathfinderConfig
        int originPacked = WorldPointUtil.packWorldPoint(origin);
        Set<Transport> transports = pathfinderConfig.getTransportsPacked().getOrDefault(originPacked, new HashSet<>());
        
        int destPacked = WorldPointUtil.packWorldPoint(destination);
        for (Transport transport : transports) {
            if (transport.getDestination() == destPacked) {
                return transport;
            }
        }
        
        return null;
    }
    
    /**
     * Execute a transport interaction (stairs, doors, etc.)
     * @param transport the transport to execute
     * @param location the location to interact at
     */
    private void executeTransportInteraction(Transport transport, WorldPoint location) {
        if (transport == null) {
            log.warn("Cannot execute null transport");
            return;
        }
        
        String displayInfo = transport.getDisplayInfo();
        TransportType type = transport.getType();
        
        log.info("Executing transport interaction: {} (type: {})", displayInfo, type);
        
        if (type == TransportType.TRANSPORT && displayInfo != null) {
            if (displayInfo.contains("Climb-up") || displayInfo.contains("Climb-down")) {
                handleStairsOrLadder(transport, location, displayInfo);
            } else if (displayInfo.contains("Open") || displayInfo.contains("Door") || displayInfo.contains("Gate")) {
                handleDoorInteraction(transport, location, displayInfo);
            } else {
                log.warn("Unknown transport interaction: {}", displayInfo);
                // Fall back to walking
                currentState = WalkState.WALKING;
            }
        } else {
            // For teleports and other transport types, use existing logic
            executeTransport(location, WorldPointUtil.unpackWorldPoint(transport.getDestination()));
        }
    }

    /**
     * Handle stairs or ladder interaction
     * @param transport the transport info
     * @param location the location to interact at
     * @param displayInfo the display info containing the action
     */
    private void handleStairsOrLadder(Transport transport, WorldPoint location, String displayInfo) {
        log.info("Handling stairs/ladder at {}: {}", location, displayInfo);

        // Extract action from display info (e.g., "Climb-up Staircase 16671")
        String action = "Climb-up";
        if (displayInfo.contains("Climb-down")) {
            action = "Climb-down";
        }

        // Try to find the staircase/ladder object
        GameObject stairObject = findStairObject(location, displayInfo);
        if (stairObject != null) {
            log.info("Found stair object {} at {}, using action: {}", stairObject.getId(), location, action);
            actionService.interactWithGameObject(stairObject, action);

            // Set state to wait for transport completion
            currentState = WalkState.WAITING_FOR_TRANSPORT;
            transportStartTime = System.currentTimeMillis();
            pathIndex++; // Move to next step
        } else {
            log.warn("Could not find stair object at {}, falling back to walking", location);
            currentState = WalkState.WALKING;
        }
    }
    
    /**
     * Handle door interaction
     * @param transport the transport info
     * @param location the location to interact at
     * @param displayInfo the display info containing the action
     */
    private void handleDoorInteraction(Transport transport, WorldPoint location, String displayInfo) {
        log.info("Handling door at {}: {}", location, displayInfo);
        
        // Use existing door handling logic
        doorToOpen = findDoorObject(location);
        if (doorToOpen != null) {
            currentState = WalkState.OPENING_DOOR;
            delayTicks = humanizerService.getRandomDelay(1, 3);
        } else {
            log.warn("Could not find door object at {}, falling back to walking", location);
            currentState = WalkState.WALKING;
        }
    }
    
    /**
     * Find a stair object at the given location
     * @param location the location to search
     * @param displayInfo the display info containing object hints
     * @return the stair object or null if not found
     */
    private GameObject findStairObject(WorldPoint location, String displayInfo) {
        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = location.getPlane();
        
        // Convert world coordinates to scene coordinates
        int baseX = client.getTopLevelWorldView().getBaseX();
        int baseY = client.getTopLevelWorldView().getBaseY();
        int sceneX = location.getX() - baseX;
        int sceneY = location.getY() - baseY;
        
        // Check if coordinates are within scene bounds
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || 
            sceneY < 0 || sceneY >= Constants.SCENE_SIZE || 
            z < 0 || z >= Constants.MAX_Z) {
            return null;
        }
        
        // Check tiles around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int checkX = sceneX + dx;
                int checkY = sceneY + dy;
                
                if (checkX >= 0 && checkX < Constants.SCENE_SIZE && 
                    checkY >= 0 && checkY < Constants.SCENE_SIZE) {
                    
                    Tile tile = tiles[z][checkX][checkY];
                    if (tile != null) {
                        for (GameObject obj : tile.getGameObjects()) {
                            if (obj != null && isStairObject(obj, displayInfo)) {
                                return obj;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a game object is a stair object
     * @param obj the game object to check
     * @param displayInfo the display info containing object hints
     * @return true if this is a stair object
     */
    private boolean isStairObject(GameObject obj, String displayInfo) {
        if (obj == null) {
            return false;
        }
        
        // Try to extract object ID from display info
        if (displayInfo != null && displayInfo.matches(".*\\d+.*")) {
            try {
                // Extract the last number from the display info (usually the object ID)
                String[] parts = displayInfo.split(" ");
                for (int i = parts.length - 1; i >= 0; i--) {
                    if (parts[i].matches("\\d+")) {
                        int expectedId = Integer.parseInt(parts[i]);
                        if (obj.getId() == expectedId) {
                            return true;
                        }
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore and fall back to generic check
            }
        }
        
        // Generic stair object IDs (common staircase IDs)
        int[] stairIds = {
            16671, 16672, 16673, 16674, // Common staircases
            9725, 9726, 9727, 9728, // Common ladders
            1738, 1739, 1740, 1741, // More staircases
            11867, 11868, 11869, 11870, // Additional stairs
            56230, 56231, 16672
        };
        
        for (int stairId : stairIds) {
            if (obj.getId() == stairId) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Find a stair object at the given location (generic version for legacy detection)
     * @param location the location to search
     * @return the stair object or null if not found
     */
    private GameObject findStairObjectGeneric(WorldPoint location) {
        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = location.getPlane();
        
        // Convert world coordinates to scene coordinates
        int baseX = client.getTopLevelWorldView().getBaseX();
        int baseY = client.getTopLevelWorldView().getBaseY();
        int sceneX = location.getX() - baseX;
        int sceneY = location.getY() - baseY;
        
        // Check if coordinates are within scene bounds
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || 
            sceneY < 0 || sceneY >= Constants.SCENE_SIZE || 
            z < 0 || z >= Constants.MAX_Z) {
            return null;
        }
        
        // Check tiles around the location for any stair-like objects
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int checkX = sceneX + dx;
                int checkY = sceneY + dy;
                
                if (checkX >= 0 && checkX < Constants.SCENE_SIZE && 
                    checkY >= 0 && checkY < Constants.SCENE_SIZE) {
                    
                    Tile tile = tiles[z][checkX][checkY];
                    if (tile != null) {
                        for (GameObject obj : tile.getGameObjects()) {
                            if (obj != null && isStairObjectGeneric(obj)) {
                                return obj;
                            }
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a game object is a stair object (generic version)
     * @param obj the game object to check
     * @return true if this is a stair object
     */
    private boolean isStairObjectGeneric(GameObject obj) {
        if (obj == null) {
            return false;
        }
        
        // Expanded list of stair object IDs (Lumbridge castle stairs)
        int[] stairIds = {
            16671, 16672, 16673, 16674, // Common staircases
            9725, 9726, 9727, 9728, // Common ladders
            1738, 1739, 1740, 1741, // More staircases
            11867, 11868, 11869, 11870, // Additional stairs
            2466, 2467, 2468, 2469, // More stair types
            1740, 1742, 1744, 1746, // Stone stairs
            11797, 11798, 11799, 11800, 56230, 56231, 16672, // Lumbridge castle specific
            1749, 1750, 1751, 1752 // Additional stair variations
        };
        
        for (int stairId : stairIds) {
            if (obj.getId() == stairId) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if this is a Lumbridge staircase that requires special interaction
     * @param obj the game object to check
     * @return true if this is a Lumbridge staircase
     */
    private boolean isLumbridgeStaircase(GameObject obj) {
        if (obj == null) {
            return false;
        }
        
        // Lumbridge castle staircase IDs that require special handling
        int[] lumbridgeStairIds = {
            16672 // Lumbridge castle staircases
        };
        
        for (int stairId : lumbridgeStairIds) {
            if (obj.getId() == stairId) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Handle Lumbridge staircase interaction with right-click menu or interface
     * @param stairObject the stair object to interact with
     * @param action the desired action (Climb-up or Climb-down)
     */
    private void handleLumbridgeStaircase(GameObject stairObject, String action) {
        log.info("Handling Lumbridge staircase {} with action: {}", stairObject.getId(), action);

        actionService.interactWithGameObject(stairObject, action);
        
        // Schedule key press after interface opens
        scheduler.schedule(() -> {
            if ("Climb-up".equals(action)) {
                log.info("Sending key press '1' for Climb-up");
                sendKeyPress("1");
            } else if ("Climb-down".equals(action)) {
                log.info("Sending key press '2' for Climb-down");
                sendKeyPress("2");
            }
        }, 700, TimeUnit.MILLISECONDS); // Wait 700ms for interface to open
    }
    
    /**
     * Send a single key press
     * @param key the key to press
     */
    private void sendKeyPress(String key) {
        try {
            actionService.sendKeyRequest("/key_hold", key);
            // Schedule key release after short delay
            scheduler.schedule(() -> {
                actionService.sendKeyRequest("/key_release", key);
            }, 50, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Error sending key press {}: {}", key, e.getMessage());
        }
    }

    /**
     * Get all the transport points along a path
     * @param path the path from a pathfinder
     * @return a list of world points that are transport origins along the path
     */
    private List<WorldPoint> getAllTransportPointsInPath(List<WorldPoint> path) {
        List<WorldPoint> transportPoints = new ArrayList<>();
        int packedPoint;
        int i = 0;
        for (WorldPoint point : path) {
            packedPoint = WorldPointUtil.packWorldPoint(point);
            WorldPoint originPoint;
            WorldPoint destinationPoint;
            if (pathfinderConfig.getTransports().containsKey(packedPoint)) {
                Set<Transport> transportSet = pathfinderConfig.getTransports().get(packedPoint);
                for (Transport transport : transportSet) {
                    originPoint = WorldPointUtil.unpackWorldPoint(transport.getOrigin());
                    destinationPoint = WorldPointUtil.unpackWorldPoint(transport.getDestination());
                    if (originPoint.equals(path.get(i)) && destinationPoint.equals(path.get(i+1))) {
                        log.info("Transport from {} to {}, click object: {}", path.get(i), path.get(i+1), transport.getObjectID());
                        transportPoints.add(point);
                    }
                }
            }
            i++;
        }
        log.info("Finding transports done. All transports: {}", transportPoints);
        return transportPoints;
    }
} 