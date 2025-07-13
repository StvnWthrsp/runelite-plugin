package com.example;

import net.runelite.api.GameObject;

/**
 * Event published when an interaction with a game object completes.
 * This allows tasks and other systems to react to the success or failure of interactions.
 */
public class InteractionCompletedEvent {
    private final GameObject gameObject;
    private final String action;
    private final boolean success;
    private final long timestamp;
    private final String failureReason;

    public InteractionCompletedEvent(GameObject gameObject, String action, boolean success) {
        this(gameObject, action, success, null);
    }

    public InteractionCompletedEvent(GameObject gameObject, String action, boolean success, String failureReason) {
        this.gameObject = gameObject;
        this.action = action;
        this.success = success;
        this.failureReason = failureReason;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the game object that was interacted with.
     * @return the game object
     */
    public GameObject getGameObject() {
        return gameObject;
    }

    /**
     * Gets the action that was performed on the game object.
     * @return the action string (e.g., "Mine", "Bank", "Open")
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
        return String.format("InteractionCompletedEvent{gameObject=%d, action='%s', success=%s, failureReason='%s', timestamp=%d}", 
            gameObject != null ? gameObject.getId() : -1, action, success, failureReason, timestamp);
    }
}