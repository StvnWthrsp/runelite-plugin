package com.example;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.Transport;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class WalkTask implements BotTask {

    private enum WalkState {
        IDLE,
        CALCULATING_PATH,
        WALKING,
        EXECUTING_TELEPORT,
        OPENING_DOOR,
        USING_TRANSPORT,
        FAILED,
        FINISHED,
        INTERACTING_WITH_OBJECT
    }

    private static final int RETRY_LIMIT = 5;

    @Getter
    private final WorldPoint destination;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;
    private final GameService gameService;
    private final HumanizerService humanizerService;

    private WalkState currentState = null;
    private int delayTicks = 0;
    private int retries = 0;
    private List<WorldPoint> path;
    private final List<WorldPoint> transportPoints = new ArrayList<>();
    private final List<Transport> transportsInPath = new ArrayList<>();;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;
    private final ScheduledExecutorService scheduler;
    private final ActionService actionService;
    
    // Transport execution state
    private long transportStartTime;
    private static final long TRANSPORT_TIMEOUT_MS = 30000; // 30 seconds
    private int pathIndex = 0;
    private TileObject doorToOpen;
    private Transport transportToUse;
    private GameObject transportObject;

    public WalkTask(RunepalPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination, ActionService actionService, GameService gameService, HumanizerService humanizerService) {
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
        // Note: WalkTask doesn't need eventService since it uses a simple polling approach for interactions
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
            case USING_TRANSPORT:
                handleTransport();
                break;
            case INTERACTING_WITH_OBJECT:
                // For simplicity, WalkTask continues to use polling approach
                // since interactions are brief and not complex
                if (!actionService.isInteracting()) {
                    log.info("Interacting complete. Resuming walking.");
                    currentState = WalkState.WALKING;
                    delayTicks = humanizerService.getShortDelay();
                    transportObject = null;
                    transportToUse = null;
                }
                break;
            default:
                break;
        }
    }

    private void handleTransport() {
        if (transportObject == null) {
            log.warn("transportObject was null, recalculating path");
            currentState = WalkState.IDLE;
            return;
        }
        if (transportToUse == null) {
            log.warn("transportToUse was null, recalculating path");
            currentState = WalkState.IDLE;
            return;
        }

        if (!actionService.isInteracting()) {
            actionService.interactWithGameObject(transportObject, transportToUse.getMenuOption());
            currentState = WalkState.INTERACTING_WITH_OBJECT;
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

        getAllTransportPointsInPath(this.path);
        
        log.info("Path calculated with {} steps.", this.path.size());
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

        transportToUse = findTransportInPath(currentLocation);
        if (transportToUse != null) {
            log.info("Transport located: {}", transportToUse.getObjectID());
            WorldPoint originPoint = WorldPointUtil.unpackWorldPoint(transportToUse.getOrigin());
            if (currentLocation.distanceTo(originPoint) > 1) {
                walkTo(originPoint);
                return;
            }
            GameObject transportObject = gameService.findNearestGameObject(transportToUse.getObjectID());
            if (transportObject != null) {
                this.transportObject = transportObject;
                currentState = WalkState.USING_TRANSPORT;
                delayTicks = humanizerService.getRandomDelay(1, 5);
                return;
            } else {
                log.warn("Transport detected but could not find object, continuing with normal walking.");
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
            log.debug("DEBUG: Normal walking - pathIndex: {}, target: {}, currentLocation: {}", pathIndex, target, currentLocation);
            walkTo(target);
            delayTicks = humanizerService.getCustomDelay(6, 2, 2);
        } else {
            log.debug("DEBUG: pathIndex {} >= path.size() {}, cannot proceed with walking", pathIndex, path.size());
        }
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
                log.debug("Movement between current location {} and {} is blocked by a door", currentLocation, pathPoint);
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
            CollisionData[] collisionData = client.getTopLevelWorldView().getCollisionMaps();
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

    /**
     * Finds a transport in our path
     * @param currentLocation the current location of the player
     * @return the transport if found, null otherwise
     */
    private Transport findTransportInPath(WorldPoint currentLocation) {
        for (int i = pathIndex; i < Math.min(pathIndex + 15, path.size()); i++) {
            WorldPoint pathPoint = path.get(i);
            log.trace("Checking if movement between {} and {} is a transport", currentLocation, pathPoint);
            // Check if we can move from current point to this path point
            if (transportPoints.contains(pathPoint)) {
                log.debug("Last reachable point: {}", currentLocation);
                return transportsInPath.get(transportPoints.indexOf(pathPoint));
            }
        }

        return null;
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
                if (!actionService.isInteracting()) {
                    actionService.interactWithGameObject((GameObject) doorToOpen, "Open");
                    currentState = WalkState.INTERACTING_WITH_OBJECT;
                }
            // TODO: Handle wall objects similar to game objects
            } else {
                actionService.interactWithWallObject((WallObject) doorToOpen, "Open");
                currentState = WalkState.WALKING;
                delayTicks = humanizerService.getRandomDelay(0, 2);
            }
        } else {
            log.warn("Could not open door. Recalculating path.");
            currentState = WalkState.IDLE;
        }
    }


    /**
     * Get all the transport points along a path, populating the class variables
     * @param path the path from a pathfinder
     */
    private void getAllTransportPointsInPath(List<WorldPoint> path) {
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
                        transportsInPath.add(transport);
                        transportPoints.add(point);
                    }
                }
            }
            i++;
        }
        log.debug("Finding transports done. All transports: {}", transportPoints);
    }
} 