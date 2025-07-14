package com.runepal.services;

import com.runepal.entity.Interactable;
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
        if (clickbox != null) {
            return getRandomPointInShape(clickbox);
        }
        
        return new Point(-1, -1);
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
        
        // First try to use the convex hull for more accurate clicking
        Shape convexHull = npc.getConvexHull();
        if (convexHull != null) {
            return getRandomPointInShape(convexHull);
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
        
        // First try to use the convex hull for more accurate clicking
        Shape convexHull = gameObject.getConvexHull();
        if (convexHull != null) {
            return getRandomPointInShape(convexHull);
        }
        
        // Fallback to clickbox if convex hull is not available
        Shape clickbox = gameObject.getClickbox();
        if (clickbox != null) {
            return getRandomPointInShape(clickbox);
        }
        
        return new Point(-1, -1);
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
        if (clickbox != null) {
            return getRandomPointInShape(clickbox);
        }
        
        return new Point(-1, -1);
    }

    /**
     * Gets a random point within the specified shape using convex hull or shape bounds.
     * This method provides more accurate clicking by respecting the actual shape geometry.
     * 
     * @param shape the shape to get a random point within
     * @return a random point within the shape, or Point(-1, -1) if not available
     */
    public Point getRandomPointInShape(Shape shape) {
        if (shape == null) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = shape.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }

        // Try to find a point within the actual shape (up to 20 attempts for better accuracy)
        for (int i = 0; i < 20; i++) {
            Point randomPoint = new Point(
                    bounds.x + random.nextInt(bounds.width),
                    bounds.y + random.nextInt(bounds.height)
            );

            if (shape.contains(randomPoint)) {
                return randomPoint;
            }
        }
        
        // If we can't find a point in the shape, try a more systematic approach
        // by dividing the bounding box into smaller regions
        int gridSize = 5;
        int stepX = Math.max(1, bounds.width / gridSize);
        int stepY = Math.max(1, bounds.height / gridSize);
        
        for (int x = bounds.x; x < bounds.x + bounds.width; x += stepX) {
            for (int y = bounds.y; y < bounds.y + bounds.height; y += stepY) {
                Point gridPoint = new Point(x, y);
                if (shape.contains(gridPoint)) {
                    // Add some randomness within this grid cell
                    int offsetX = random.nextInt(Math.min(stepX, bounds.x + bounds.width - x));
                    int offsetY = random.nextInt(Math.min(stepY, bounds.y + bounds.height - y));
                    Point randomGridPoint = new Point(x + offsetX, y + offsetY);
                    
                    if (shape.contains(randomGridPoint)) {
                        return randomGridPoint;
                    }
                    return gridPoint; // Fallback to grid point if random offset fails
                }
            }
        }
        
        // Last resort: return center of bounding box
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