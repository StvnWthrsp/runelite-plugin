package com.example;

import java.awt.Point;

/**
 * Event published when a Windmouse movement to a target point completes.
 * This allows ActionService and other systems to react to movement completion
 * and execute follow-up actions like clicking.
 */
public class MouseMovementCompletedEvent {
    private final String movementId;
    private final Point finalPosition;
    private final long duration;
    private final boolean cancelled;
    private final long timestamp;

    public MouseMovementCompletedEvent(String movementId, Point finalPosition, long duration, boolean cancelled) {
        this.movementId = movementId;
        this.finalPosition = finalPosition;
        this.duration = duration;
        this.cancelled = cancelled;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the unique identifier for the movement that completed.
     * @return the movement ID
     */
    public String getMovementId() {
        return movementId;
    }

    /**
     * Gets the final position where the mouse ended up.
     * @return the final mouse position
     */
    public Point getFinalPosition() {
        return finalPosition;
    }

    /**
     * Gets the duration of the movement in milliseconds.
     * @return movement duration in milliseconds
     */
    public long getDuration() {
        return duration;
    }

    /**
     * Indicates whether the movement was cancelled before completion.
     * @return true if the movement was cancelled, false if completed normally
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Gets the timestamp when the movement completed.
     * @return timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("MouseMovementCompletedEvent{movementId='%s', finalPosition=%s, duration=%d, cancelled=%s, timestamp=%d}",
            movementId, finalPosition, duration, cancelled, timestamp);
    }
}