package com.runepal.entity;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import java.awt.Shape;
import java.util.Objects;

/**
 * Adapter class that wraps a RuneLite GameObject and implements the Interactable interface.
 * This allows GameObjects to be used polymorphically with other interactable entities.
 */
public class GameObjectEntity implements Interactable {
    
    private final GameObject gameObject;
    
    public GameObjectEntity(GameObject gameObject) {
        this.gameObject = Objects.requireNonNull(gameObject, "GameObject cannot be null");
    }
    
    @Override
    public String getName() {
        // GameObjects don't have names in the same way NPCs do
        // We could return a description based on ID or just return a generic name
        return "GameObject[" + gameObject.getId() + "]";
    }
    
    @Override
    public WorldPoint getWorldLocation() {
        return gameObject.getWorldLocation();
    }
    
    @Override
    public Shape getClickbox() {
        return gameObject.getClickbox();
    }
    
    /**
     * Gets the underlying GameObject.
     * @return the wrapped GameObject
     */
    public GameObject getGameObject() {
        return gameObject;
    }
    
    /**
     * Gets the ID of the underlying GameObject.
     * @return the GameObject ID
     */
    public int getId() {
        return gameObject.getId();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GameObjectEntity that = (GameObjectEntity) obj;
        return Objects.equals(gameObject, that.gameObject);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(gameObject);
    }
    
    @Override
    public String toString() {
        return "GameObjectEntity{" +
               "id=" + gameObject.getId() +
               ", location=" + gameObject.getWorldLocation() +
               '}';
    }
} 