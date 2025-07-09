package com.example.walking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

import com.example.ActionService;
import com.example.GameService;
import com.example.HumanizerService;

import java.util.List;
import java.util.Objects;

/**
 * Handles door-related walking logic including door detection and opening.
 */
@Slf4j
public class DoorHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final WalkingUtil walkingUtil;

    public DoorHandler(Client client, GameService gameService, ActionService actionService, HumanizerService humanizerService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.walkingUtil = new WalkingUtil(client, actionService);
    }

    /**
     * Handles door step in the walking path.
     * 
     * @param path the complete walking path
     * @param pathIndex current index in the path
     * @param currentLocation player's current location
     * @return true if door was handled, false to continue with normal walking
     */
    public boolean handleDoorStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        // Check for doors blocking our path using collision data
        DoorInfo doorInfo = findDoorBlockingPath(currentLocation, path, pathIndex);
        if (doorInfo != null) {
            log.info("Door detected at {} blocking path to {}", doorInfo.doorLocation, doorInfo.targetLocation);
            
            // Walk to the door first if we're not adjacent
            if (currentLocation.distanceTo(doorInfo.doorLocation) > 1) {
                log.info("Walking to door at {}, stopping at {}", doorInfo.doorLocation, doorInfo.lastUnblockedPoint);
                walkingUtil.walkTo(doorInfo.lastUnblockedPoint);
                return true;
            }
            
            // We're adjacent to the door, try to interact with it
            TileObject doorObject = findDoorObject(doorInfo.doorLocation);
            if ((doorObject != null && doorObject instanceof GameObject) || (doorObject != null && doorObject instanceof WallObject)) {
                log.debug("Found door object {} at {}, attempting to open", doorObject.getId(), doorInfo.doorLocation);
                return handleDoorInteraction(doorObject);
            } else {
                log.warn("Door object detected but is not GameObject or WallObject, continuing with normal walking.");
            }
        }
        
        return false;
    }

    /**
     * Information about a door blocking the path.
     */
    public static class DoorInfo {
        public final WorldPoint doorLocation;
        public final WorldPoint targetLocation;
        public final WorldPoint lastUnblockedPoint;

        public DoorInfo(WorldPoint doorLocation, WorldPoint targetLocation, WorldPoint lastUnblockedPoint) {
            this.doorLocation = doorLocation;
            this.targetLocation = targetLocation;
            this.lastUnblockedPoint = lastUnblockedPoint;
        }
    }

    /**
     * Find a door that's blocking the path to the next waypoint.
     * 
     * @param currentLocation player's current location
     * @param path the walking path
     * @param pathIndex current path index
     * @return DoorInfo if door found, null otherwise
     */
    private DoorInfo findDoorBlockingPath(WorldPoint currentLocation, List<WorldPoint> path, int pathIndex) {
        if (pathIndex >= path.size()) {
            return null;
        }

        WorldPoint targetLocation = path.get(pathIndex);
        
        // Check if movement is blocked by a door
        if (isMovementBlockedByDoor(currentLocation, targetLocation)) {
            // Find the specific door location between current and target
            WorldPoint doorLocation = findDoorLocationBetween(currentLocation, targetLocation);
            if (doorLocation != null) {
                // Find the last unblocked point we can walk to (adjacent to door)
                WorldPoint lastUnblockedPoint = findLastUnblockedPoint(currentLocation, doorLocation);
                return new DoorInfo(doorLocation, targetLocation, lastUnblockedPoint);
            }
        }
        
        return null;
    }

    /**
     * Check if movement from start to end is blocked by a door.
     * 
     * @param start starting location
     * @param end ending location
     * @return true if blocked by door, false otherwise
     */
    private boolean isMovementBlockedByDoor(WorldPoint start, WorldPoint end) {
        // Simple check - if locations are adjacent and on same plane, check for wall/door
        if (start.getPlane() == end.getPlane() && start.distanceTo(end) == 1) {
            // Get collision data from the client
            CollisionData[] collisionData = client.getCollisionMaps();
            if (collisionData == null) {
                return false;
            }
            
            int plane = start.getPlane();
            if (plane < 0 || plane >= collisionData.length) {
                return false;
            }
            
            CollisionData collision = collisionData[plane];
            if (collision == null) {
                return false;
            }
            
            // Convert to scene coordinates
            int baseX = client.getWorldView(-1).getBaseX();
            int baseY = client.getWorldView(-1).getBaseY();
            int sceneX = start.getX() - baseX;
            int sceneY = start.getY() - baseY;
            
            if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE) {
                return false;
            }
            
            int[][] flags = collision.getFlags();
            int flag = flags[sceneX][sceneY];
            
            // Check for wall flags that might indicate a door
            int dx = end.getX() - start.getX();
            int dy = end.getY() - start.getY();
            
            if (dx == 1 && (flag & CollisionDataFlag.BLOCK_MOVEMENT_EAST) != 0) {
                return true;
            }
            if (dx == -1 && (flag & CollisionDataFlag.BLOCK_MOVEMENT_WEST) != 0) {
                return true;
            }
            if (dy == 1 && (flag & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) != 0) {
                return true;
            }
            if (dy == -1 && (flag & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) != 0) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Find the door location between two adjacent points.
     * 
     * @param start starting location
     * @param end ending location
     * @return door location or null if not found
     */
    private WorldPoint findDoorLocationBetween(WorldPoint start, WorldPoint end) {
        // For adjacent tiles, the door is typically at the start location
        // but we should verify by checking for door objects
        TileObject doorObject = findDoorObject(start);
        if (doorObject != null) {
            return start;
        }
        
        // Check the end location as well
        doorObject = findDoorObject(end);
        if (doorObject != null) {
            return end;
        }
        
        return null;
    }

    /**
     * Find the last point we can walk to before hitting the door.
     * 
     * @param currentLocation player's current location
     * @param doorLocation door location
     * @return last unblocked point adjacent to door
     */
    private WorldPoint findLastUnblockedPoint(WorldPoint currentLocation, WorldPoint doorLocation) {
        // For doors, we usually want to be adjacent to the door to interact with it
        // Find an adjacent point to the door that's accessible
        
        WorldPoint[] adjacentPoints = {
            new WorldPoint(doorLocation.getX() + 1, doorLocation.getY(), doorLocation.getPlane()),
            new WorldPoint(doorLocation.getX() - 1, doorLocation.getY(), doorLocation.getPlane()),
            new WorldPoint(doorLocation.getX(), doorLocation.getY() + 1, doorLocation.getPlane()),
            new WorldPoint(doorLocation.getX(), doorLocation.getY() - 1, doorLocation.getPlane())
        };
        
        // Return the closest adjacent point to current location
        WorldPoint closest = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (WorldPoint point : adjacentPoints) {
            int distance = currentLocation.distanceTo(point);
            if (distance < minDistance) {
                minDistance = distance;
                closest = point;
            }
        }
        
        return closest != null ? closest : doorLocation;
    }

    /**
     * Find a door object at the specified location.
     * 
     * @param location the location to check for doors
     * @return door object or null if not found
     */
    private TileObject findDoorObject(WorldPoint location) {
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
        
        // Check for wall objects (doors are often wall objects)
        WallObject wallObject = tile.getWallObject();
        if (wallObject != null && isClosedDoor(wallObject)) {
            return wallObject;
        }
        
        // Check for game objects that might be doors
        for (GameObject gameObject : tile.getGameObjects()) {
            if (gameObject != null && isClosedDoor(gameObject)) {
                return gameObject;
            }
        }
        
        return null;
    }

    /**
     * Check if a tile object is a closed door.
     * 
     * @param object the tile object to check
     * @return true if it's a closed door, false otherwise
     */
    private boolean isClosedDoor(TileObject object) {
        if (object == null) {
            return false;
        }
        
        int id = object.getId();
        
        // Common door IDs - this list should be expanded based on actual game content
        return id == ObjectID.FAI_VARROCK_DOOR || id == ObjectID.FAI_VARROCK_DOOR_TALLER;
    }

    /**
     * Handle interaction with a door object.
     * 
     * @param doorObject the door to interact with
     * @return true if interaction was handled, false otherwise
     */
    private boolean handleDoorInteraction(TileObject doorObject) {
        log.info("Attempting to open door with ID: {}", doorObject.getId());
        
        if (doorObject instanceof GameObject) {
            actionService.interactWithGameObject((GameObject) doorObject, "Open");
        } else if (doorObject instanceof WallObject) {
            // Handle wall object interaction - would need specific implementation
            log.info("Door is WallObject, attempting interaction");
            // actionService.interactWithWallObject((WallObject) doorObject, "Open");
        }
        
        return true;
    }

}