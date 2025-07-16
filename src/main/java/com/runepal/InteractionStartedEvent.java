package com.runepal;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import com.runepal.entity.Interactable;
import com.runepal.entity.GameObjectEntity;
import com.runepal.entity.NpcEntity;

/**
 * Event published when an interaction with an interactable entity is initiated.
 * This allows tasks and other systems to be notified when an interaction begins.
 */
public class InteractionStartedEvent {
    private final Interactable entity;
    private final String action;
    private final long timestamp;

    public InteractionStartedEvent(Interactable entity, String action) {
        this.entity = entity;
        this.action = action;
        this.timestamp = System.currentTimeMillis();
    }

    public InteractionStartedEvent(GameObject gameObject, String action) {
        this(new GameObjectEntity(gameObject), action);
    }

    public InteractionStartedEvent(NPC npc, String action) {
        this(new NpcEntity(npc), action);
    }

    /**
     * Gets the interactable entity being interacted with.
     * @return the interactable entity
     */
    public Interactable getEntity() {
        return entity;
    }

    /**
     * Gets the game object being interacted with (for backward compatibility).
     * @return the game object, or null if the entity is not a game object
     * @deprecated Use getEntity() instead
     */
    @Deprecated
    public GameObject getGameObject() {
        return entity instanceof GameObjectEntity ? ((GameObjectEntity) entity).getGameObject() : null;
    }

    /**
     * Gets the action being performed on the interactable entity.
     * @return the action string (e.g., "Mine", "Bank", "Open", "Attack")
     */
    public String getAction() {
        return action;
    }

    /**
     * Gets the timestamp when the interaction was started.
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("InteractionStartedEvent{entity=%s, action='%s', timestamp=%d}", 
            entity != null ? entity.getName() : "null", action, timestamp);
    }
}