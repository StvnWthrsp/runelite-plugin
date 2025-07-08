package com.example.services;

import com.example.entity.Interactable;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Random;

/**
 * Service responsible for generating click points within entity bounds.
 * Provides unified click point generation for all entity types.
 */
@Singleton
@Slf4j
public class ClickService {
    private final Random random = new Random();

    /**
     * Gets a random clickable point within the bounds of any interactable entity.
     * This unified method handles all entity types through the Interactable interface.
     * 
     * @param interactable the interactable entity
     * @return a random point within the clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Interactable interactable) {
        if (interactable == null) {
            return new Point(-1, -1);
        }
        
        Shape clickbox = interactable.getClickbox();
        if (clickbox == null) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = clickbox.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }

        // Try to find a point within the actual shape (up to 10 attempts)
        for (int i = 0; i < 10; i++) {
            Point randomPoint = new Point(
                    bounds.x + random.nextInt(bounds.width),
                    bounds.y + random.nextInt(bounds.height)
            );

            if (clickbox.contains(randomPoint)) {
                return randomPoint;
            }
        }
        
        // Fallback to center if we fail to find a point within the shape
        return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
    }

    /**
     * Gets a random clickable point within the bounds of a Widget.
     * This method handles UI widgets like bank buttons, interfaces, etc.
     * 
     * @param widget the widget to get a click point for
     * @return a random point within the widget bounds, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Widget widget) {
        if (widget == null || widget.isHidden()) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = widget.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }
        
        return getRandomPointInBounds(bounds);
    }

    /**
     * Gets a random clickable point for an NPC.
     * Legacy method for backward compatibility.
     * 
     * @param npc the NPC to get a click point for
     * @return a random point within the NPC's clickable area, or null if not available
     */
    public Point getRandomClickablePoint(NPC npc) {
        if (npc == null) {
            return null;
        }
        
        Shape clickbox = npc.getConvexHull();
        if (clickbox != null) {
            return getRandomPointInBounds(clickbox.getBounds());
        }

        return null;
    }

    /**
     * Gets a random clickable point for a GameObject.
     * Legacy method for backward compatibility.
     * 
     * @param gameObject the GameObject to get a click point for
     * @return a random point within the GameObject's clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(GameObject gameObject) {
        if (gameObject == null) {
            return new Point(-1, -1);
        }
        
        Shape clickbox = gameObject.getClickbox();
        if (clickbox == null) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = clickbox.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }

        // In a loop, generate a random x and y within the bounding rectangle.
        for (int i = 0; i < 10; i++) {
            Point randomPoint = new Point(
                    bounds.x + random.nextInt(bounds.width),
                    bounds.y + random.nextInt(bounds.height)
            );

            // Use shape.contains(x, y) to check if the random point is within the actual shape.
            if (clickbox.contains(randomPoint)) {
                return randomPoint;
            }
        }
        // Fallback to center if we fail to find a point
        return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
    }

    /**
     * Gets a random clickable point for a TileObject.
     * Legacy method for backward compatibility.
     * 
     * @param tileObject the TileObject to get a click point for
     * @return a random point within the TileObject's clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(TileObject tileObject) {
        if (tileObject == null) {
            return new Point(-1, -1);
        }
        
        Shape clickbox = tileObject.getClickbox();
        if (clickbox == null) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = clickbox.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }

        // In a loop, generate a random x and y within the bounding rectangle.
        for (int i = 0; i < 10; i++) {
            Point randomPoint = new Point(
                    bounds.x + random.nextInt(bounds.width),
                    bounds.y + random.nextInt(bounds.height)
            );

            // Use shape.contains(x, y) to check if the random point is within the actual shape.
            if (clickbox.contains(randomPoint)) {
                return randomPoint;
            }
        }
        // Fallback to center if we fail to find a point
        return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
    }

    /**
     * Gets a random point within the specified bounds.
     * Helper method for simple rectangular bounds.
     * 
     * @param bounds the rectangle bounds
     * @return a random point within the bounds, or Point(-1, -1) if bounds are empty
     */
    public Point getRandomPointInBounds(Rectangle bounds) {
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }
        int x = bounds.x + random.nextInt(bounds.width);
        int y = bounds.y + random.nextInt(bounds.height);
        return new Point(x, y);
    }
}