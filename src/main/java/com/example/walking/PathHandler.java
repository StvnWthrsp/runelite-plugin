package com.example.walking;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import com.example.ActionService;
import com.example.GameService;
import com.example.HumanizerService;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;

/**
 * Handles normal path navigation and minimap-based walking.
 */
@Slf4j
public class PathHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    private final HumanizerService humanizerService;
    private final WalkingUtil walkingUtil;

    public PathHandler(Client client, GameService gameService, ActionService actionService, HumanizerService humanizerService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.walkingUtil = new WalkingUtil(client, actionService);
    }

    /**
     * Handle normal walking along the path.
     * 
     * @param path the complete walking path
     * @param pathIndex current index in the path
     * @param currentLocation player's current location
     * @return true if walking was handled, false otherwise
     */
    public boolean handleNormalWalking(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        if (pathIndex >= path.size()) {
            log.warn("DEBUG: pathIndex {} >= path.size() {}, cannot proceed with walking", pathIndex, path.size());
            return false;
        }

        WorldPoint target = getNextMinimapTarget(path, pathIndex);
        log.info("DEBUG: Normal walking - pathIndex: {}, target: {}, currentLocation: {}", pathIndex, target, currentLocation);
        walkingUtil.walkTo(target);
        return true;
    }

    /**
     * Update the path index based on current location.
     * 
     * @param currentLocation player's current location
     * @param path the walking path
     * @param pathIndex current path index
     * @return updated path index
     */
    public int updatePathIndex(WorldPoint currentLocation, List<WorldPoint> path, int pathIndex) {
        log.info("DEBUG: Before updatePathIndex - pathIndex: {}, currentLocation: {}", pathIndex, currentLocation);
        
        if (path.isEmpty()) {
            return pathIndex;
        }

        // Check if we're close to any upcoming waypoints and can skip ahead
        for (int i = pathIndex; i < Math.min(pathIndex + 3, path.size()); i++) {
            WorldPoint waypoint = path.get(i);
            if (currentLocation.distanceTo(waypoint) <= 1) {
                log.info("DEBUG: Skipping to waypoint {} (distance: {})", i, currentLocation.distanceTo(waypoint));
                pathIndex = i + 1; // Move to next waypoint
                break;
            }
        }
        
        log.info("DEBUG: After updatePathIndex - pathIndex: {}", pathIndex);
        return pathIndex;
    }

    /**
     * Get the next target point on the minimap for walking.
     * 
     * @param path the walking path
     * @param pathIndex current path index
     * @return target world point for minimap walking
     */
    private WorldPoint getNextMinimapTarget(List<WorldPoint> path, int pathIndex) {
        if (pathIndex >= path.size()) {
            return null;
        }

        WorldPoint target = path.get(pathIndex);
        
        // Look ahead to find the furthest point we can walk to on the minimap
        for (int i = pathIndex + 1; i < path.size(); i++) {
            WorldPoint candidate = path.get(i);
            if (isPointOnMinimap(candidate)) {
                target = candidate;
            } else {
                break; // Stop at first point not on minimap
            }
        }
        
        return target;
    }

    /**
     * Check if a world point is visible on the minimap.
     * 
     * @param worldPoint the point to check
     * @return true if point is on minimap, false otherwise
     */
    private boolean isPointOnMinimap(WorldPoint worldPoint) {
        Widget minimapDrawWidget = getMinimapDrawWidget();
        if (minimapDrawWidget == null) {
            return false;
        }

        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint == null) {
            return false;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
        if (minimapPoint == null) {
            return false;
        }

        Rectangle minimapBounds = minimapDrawWidget.getBounds();
        return minimapBounds.contains(minimapPoint.getX(), minimapPoint.getY());
    }

    /**
     * Get the minimap drawing widget.
     * 
     * @return minimap widget or null if not found
     */
    private Widget getMinimapDrawWidget() {
        Widget minimap;
        minimap = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        if (minimap == null) {
            minimap = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        }
        if (minimap == null) {
            minimap = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        }
        return minimap;
    }

}