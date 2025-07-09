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

import java.awt.Rectangle;
import java.util.Objects;

/**
 * Utility class for walking operations shared across all walking handlers.
 * Provides common walking functionality to avoid code duplication.
 */
@Slf4j
public class WalkingUtil {
    private final Client client;
    private final ActionService actionService;

    public WalkingUtil(Client client, ActionService actionService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
    }

    /**
     * Walk to a specific world point using the minimap.
     * 
     * @param target the target world point
     */
    public void walkTo(WorldPoint target) {
        if (target == null) {
            log.warn("Cannot walk to null target");
            return;
        }
        
        log.info("Walking to target: {}", target);
        
        // Check if the target is on the minimap
        if (isPointOnMinimap(target)) {
            // Click on minimap
            Point minimapPoint = getMinimapPoint(target);
            if (minimapPoint != null) {
                actionService.sendClickRequest(new java.awt.Point(minimapPoint.getX(), minimapPoint.getY()), true);
            }
        } else {
            log.warn("Target {} is not visible on minimap, cannot walk directly", target);
        }
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
     * Get the minimap point for a world point.
     * 
     * @param worldPoint the world point
     * @return minimap point or null if not available
     */
    private Point getMinimapPoint(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint == null) {
            return null;
        }

        return Perspective.localToMinimap(client, localPoint);
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

    /**
     * Check if the player is currently walking to a destination.
     * 
     * @return true if player is walking, false otherwise
     */
    public boolean isPlayerWalking() {
        return client.getLocalDestinationLocation() != null;
    }

    /**
     * Get the distance between two world points.
     * 
     * @param from starting point
     * @param to ending point
     * @return distance between points
     */
    public int getDistance(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return Integer.MAX_VALUE;
        }
        return from.distanceTo(to);
    }
}