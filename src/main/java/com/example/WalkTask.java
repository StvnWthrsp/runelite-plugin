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
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.Constants;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

@Slf4j
public class WalkTask implements BotTask {

    private enum WalkState {
        IDLE,
        CALCULATING_PATH,
        WALKING,
        EXECUTING_TELEPORT,
        OPENING_DOOR,
        WAITING_FOR_TRANSPORT,
        FAILED,
        FINISHED
    }

    @Getter
    private final WorldPoint destination;
    private final RunepalPlugin plugin;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;
    private final GameService gameService;

    private WalkState currentState = WalkState.IDLE;
    private int delayTicks = 0;
    private List<WorldPoint> path;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;
    private final ActionService actionService;
    
    // Transport execution state
    private Object pendingTransport;
    private long transportStartTime;
    private static final long TRANSPORT_TIMEOUT_MS = 30000; // 30 seconds
    private int pathIndex = 0;
    private TileObject doorToOpen;

    public WalkTask(RunepalPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination, ActionService actionService, GameService gameService) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.pathfinderConfig = pathfinderConfig;
        this.destination = destination;
        this.gameService = gameService;
        ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("walk-task-%d").build();
        this.pathfinderExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
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

        // Check if we're already at the destination
        if (currentLocation.equals(destination)) {
            log.info("Already at destination.");
            currentState = WalkState.FINISHED;
            return;
        }

        // Update path index based on current location
        updatePathIndex(currentLocation);

        if (pathIndex >= path.size()) {
            log.warn("Reached end of path but not at destination. Recalculating...");
            currentState = WalkState.IDLE;
            return;
        }

        // Check for doors blocking our path using collision data
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
                setRandomDelay(1, 5);
                return;
            } else {
                log.warn("Door object detected but is not GameObject or WallObject, continuing with normal walking", doorInfo.doorLocation);
            }
        }

        // Normal walking - proceed to next point in path
        if (pathIndex < path.size()) {
            WorldPoint target = getNextMinimapTarget();
            walkTo(target);
            setRandomDelay(3, 10);
        }
    }

    private void setRandomDelay(int minTicks, int maxTicks) {
        delayTicks = plugin.getRandom().nextInt(maxTicks - minTicks + 1) + minTicks;
    }

    /**
     * Finds a door that is blocking our path by checking collision data
     */
    private DoorInfo findDoorBlockingPath(WorldPoint currentLocation) {
        log.info("Finding door blocking path from {} to {}", currentLocation, path.get(pathIndex));
        // Look ahead in our path to find where we might be blocked
        for (int i = pathIndex; i < Math.min(pathIndex + 10, path.size()); i++) {
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
            ObjectID.FAI_VARROCK_DOOR, 2398, 11780, 50048
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
        // Update path index to current position
        for (int i = pathIndex; i < path.size(); i++) {
            if (path.get(i).distanceTo(currentLocation) < 1) {
                pathIndex = i;
                log.debug("Updated path index to {}, current location: {}", pathIndex, path.get(i));
            } else {
                break;
            }
        }
    }

    private WorldPoint getNextMinimapTarget() {
        for (int i = path.size() - 1; i >= 0; i--) {
            WorldPoint point = path.get(i);
            if (isPointOnMinimap(point)) {
                return point;
            }
        }
        
        // Fallback to current path position
        if (pathIndex < path.size()) {
            return path.get(pathIndex);
        }
        
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

        int radiusSq = (radius - 5) * (radius - 5);

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
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint != null) {
            net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
            if (minimapPoint != null) {
                log.info("Requesting walk to {} via minimap click at {}", worldPoint, minimapPoint);
                actionService.sendClickRequest(new java.awt.Point(minimapPoint.getX(), minimapPoint.getY()), true);
            } else {
                log.warn("Cannot walk to {}: not visible on minimap.", worldPoint);
            }
        } else {
            log.warn("Cannot walk to {}: not in scene.", worldPoint);
        }
    }

    private void handleTeleportExecution() {
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Teleport execution timed out");
            currentState = WalkState.FAILED;
            return;
        }

        if (pendingTransport == null) {
            log.error("No pending transport for teleport execution");
            currentState = WalkState.WALKING;
            return;
        }

        // For now, just continue walking as teleport implementation is complex
        log.info("Teleport execution not fully implemented, continuing walking");
        currentState = WalkState.WALKING;
    }

    private void handleDoorOpening() {
        if ((doorToOpen != null && doorToOpen instanceof GameObject) || (doorToOpen != null && doorToOpen instanceof WallObject)) {
            if (doorToOpen instanceof GameObject) {
                actionService.interactWithGameObject((GameObject) doorToOpen, "Open");
            } else if (doorToOpen instanceof WallObject) {
                actionService.interactWithWallObject((WallObject) doorToOpen, "Open");
            }
            currentState = WalkState.WALKING;
            setRandomDelay(0, 2);
            return;
        } else {
            log.warn("Could not open door. Recalculating path.");
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
} 