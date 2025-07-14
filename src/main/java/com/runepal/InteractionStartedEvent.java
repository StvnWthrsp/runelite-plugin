package com.runepal;

import net.runelite.api.GameObject;

/**
 * Event published when an interaction with a game object is initiated.
 * This allows tasks and other systems to be notified when an interaction begins.
 */
public class InteractionStartedEvent {
    private final GameObject gameObject;
    private final String action;
    private final long timestamp;

    public InteractionStartedEvent(GameObject gameObject, String action) {
        this.gameObject = gameObject;
        this.action = action;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the game object being interacted with.
     * @return the game object
     */
    public GameObject getGameObject() {
        return gameObject;
    }

    /**
     * Gets the action being performed on the game object.
     * @return the action string (e.g., "Mine", "Bank", "Open")
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
        return String.format("InteractionStartedEvent{gameObject=%d, action='%s', timestamp=%d}", 
            gameObject != null ? gameObject.getId() : -1, action, timestamp);
    }
}