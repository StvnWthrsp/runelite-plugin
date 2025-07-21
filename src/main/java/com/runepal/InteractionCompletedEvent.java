package com.runepal;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import com.runepal.entity.Interactable;
import com.runepal.entity.GameObjectEntity;
import com.runepal.entity.NpcEntity;

/**
 * Event published when an interaction with an interactable entity completes.
 * This allows tasks and other systems to react to the success or failure of interactions.
 */
public class InteractionCompletedEvent {
    private final Interactable entity;
    private final String action;
    private final boolean success;
    private final long timestamp;
    private final String failureReason;

    public InteractionCompletedEvent(Interactable entity, String action, boolean success) {
        this(entity, action, success, null);
    }

    public InteractionCompletedEvent(Interactable entity, String action, boolean success, String failureReason) {
        this.entity = entity;
        this.action = action;
        this.success = success;
        this.failureReason = failureReason;
        this.timestamp = System.currentTimeMillis();
    }

    public InteractionCompletedEvent(GameObject gameObject, String action, boolean success) {
        this(new GameObjectEntity(gameObject), action, success, null);
    }

    public InteractionCompletedEvent(GameObject gameObject, String action, boolean success, String failureReason) {
        this(new GameObjectEntity(gameObject), action, success, failureReason);
    }

    public InteractionCompletedEvent(NPC npc, String action, boolean success) {
        this(new NpcEntity(npc), action, success, null);
    }

    public InteractionCompletedEvent(NPC npc, String action, boolean success, String failureReason) {
        this(new NpcEntity(npc), action, success, failureReason);
    }

    /**
     * Gets the interactable entity that was interacted with.
     * @return the interactable entity
     */
    public Interactable getEntity() {
        return entity;
    }

    /**
     * Gets the game object that was interacted with (for backward compatibility).
     * @return the game object, or null if the entity is not a game object
     * @deprecated Use getEntity() instead
     */
    @Deprecated
    public GameObject getGameObject() {
        return entity instanceof GameObjectEntity ? ((GameObjectEntity) entity).getGameObject() : null;
    }

    /**
     * Gets the action that was performed on the interactable entity.
     * @return the action string (e.g., "Mine", "Bank", "Open", "Attack")
     */
    public String getAction() {
        return action;
    }

    /**
     * Indicates whether the interaction was successful.
     * @return true if the interaction succeeded, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the reason for failure, if the interaction was unsuccessful.
     * @return failure reason string, or null if the interaction was successful
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Gets the timestamp when the interaction completed.
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("InteractionCompletedEvent{entity=%s, action='%s', success=%s, failureReason='%s', timestamp=%d}", 
            entity != null ? entity.getName() : "null", action, success, failureReason, timestamp);
    }
}