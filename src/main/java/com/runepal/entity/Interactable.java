package com.runepal.entity;

import net.runelite.api.coords.WorldPoint;
import java.awt.Shape;

/**
 * Common interface for all interactable entities in the game.
 * This abstracts away the differences between GameObjects, NPCs, and other interactable entities.
 */
public interface Interactable {
    
    /**
     * Gets the name of this interactable entity.
     * @return the name, or null if not available
     */
    String getName();
    
    /**
     * Gets the world location of this interactable entity.
     * @return the world location
     */
    WorldPoint getWorldLocation();
    
    /**
     * Gets the clickable area (clickbox) of this interactable entity.
     * @return the clickable shape, or null if not available
     */
    Shape getClickbox();
}
