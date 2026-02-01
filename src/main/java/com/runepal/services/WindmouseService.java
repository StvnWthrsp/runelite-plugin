package com.runepal.services;

import com.runepal.BotConfig;
import com.runepal.EventService;
import com.runepal.MouseMovementCompletedEvent;
import com.runepal.RunepalPlugin;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that provides human-like mouse movement using the Windmouse
 * algorithm.
 * This physics-based approach simulates realistic mouse movement patterns
 * with gravity, wind forces, and natural acceleration/deceleration.
 */
@Singleton
@Slf4j
public class WindmouseService {

    private final RunepalPlugin plugin;
    private final EventService eventService;
    private final BotConfig config;
    private final RemoteInputService remoteInputService;

    // Movement state
    private final AtomicBoolean isMoving = new AtomicBoolean(false);
    private final AtomicReference<String> currentMovementId = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> currentMovement = new AtomicReference<>();

    // Mathematical constants
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double SQRT_5 = Math.sqrt(5.0);

    @Inject
    public WindmouseService(RunepalPlugin plugin, EventService eventService, BotConfig config,
            RemoteInputService remoteInputService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.remoteInputService = Objects.requireNonNull(remoteInputService, "remoteInputService cannot be null");
    }

    /**
     * Start asynchronous movement to target point using Windmouse algorithm.
     * 
     * @param start       starting position
     * @param destination target position
     * @param movementId  unique identifier for this movement
     */
    public void moveToPoint(Point start, Point destination, String movementId) {
        if (start == null || destination == null || movementId == null) {
            log.warn("Invalid parameters for moveToPoint: start={}, destination={}, movementId={}", start, destination,
                    movementId);
            return;
        }

        // Cancel any existing movement
        cancelMovement();

        // Start new movement
        currentMovementId.set(movementId);
        isMoving.set(true);

        CompletableFuture<Void> movement = CompletableFuture.runAsync(() -> {
            try {
                executeWindmouseMovement(start, destination, movementId);
            } catch (Exception e) {
                log.error("Error during Windmouse movement {}: {}", movementId, e.getMessage(), e);
                // Publish completion event with error
                eventService.publish(new MouseMovementCompletedEvent(movementId, destination, 0, true));
            }
        });

        currentMovement.set(movement);
    }

    /**
     * Cancel the current movement if one is in progress.
     */
    public void cancelMovement() {
        String movementId = currentMovementId.get();
        if (movementId != null) {
            cancelMovement(movementId);
        }
    }

    /**
     * Cancel a specific movement by ID.
     * 
     * @param movementId the movement ID to cancel
     */
    public void cancelMovement(String movementId) {
        if (movementId == null) {
            return;
        }

        String currentId = currentMovementId.get();
        if (movementId.equals(currentId)) {
            log.debug("Cancelling movement: {}", movementId);
            isMoving.set(false);

            CompletableFuture<Void> movement = currentMovement.get();
            if (movement != null) {
                movement.cancel(true);
            }

            currentMovementId.set(null);
            currentMovement.set(null);

            // Publish cancellation event
            eventService.publish(new MouseMovementCompletedEvent(movementId, null, 0, true));
        }
    }

    /**
     * Check if a movement is currently in progress.
     * 
     * @return true if movement is active, false otherwise
     */
    public boolean isMoving() {
        return isMoving.get();
    }

    /**
     * Get the current movement ID if one is active.
     * 
     * @return current movement ID or null
     */
    public String getCurrentMovementId() {
        return currentMovementId.get();
    }

    /**
     * Core Windmouse physics algorithm implementation.
     * This method runs in a background thread and incrementally moves the mouse
     * using physics-based calculations with gravity and wind forces.
     */
    private void executeWindmouseMovement(Point start, Point destination, String movementId) {
        long startTime = System.currentTimeMillis();

        // Physics parameters from config
        double G_0 = config.windmouseGravity();
        double W_0 = config.windmouseWind();
        double M_0 = config.windmouseMaxVel();
        double D_0 = config.windmouseTargetArea();

        // Initialize physics state - match Python variable names exactly
        double loop_start_x = start.x;
        double loop_start_y = start.y;
        double v_x = 0.0;
        double v_y = 0.0;
        double W_x = 0.0;
        double W_y = 0.0;

        // Track actual mouse position for event dispatch
        int current_x = start.x;
        int current_y = start.y;

        log.debug("Starting Windmouse movement {} from {} to {}", movementId, start, destination);

        // Main physics loop - match Python logic exactly
        while (isMoving.get() && movementId.equals(currentMovementId.get())) {
            // Calculate distance to destination using loop_start position (Python line 241)
            double deltaX = destination.x - loop_start_x;
            double deltaY = destination.y - loop_start_y;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

            // Stop if we're close enough
            if (distance < 1.0) {
                break;
            }

            // Update wind forces based on distance (Python lines 242-252)
            double W_mag = Math.min(W_0, distance);
            if (distance >= D_0) {
                // Far from target - add random wind perturbation
                W_x = W_x / SQRT_3 + (2 * ThreadLocalRandom.current().nextDouble() - 1) * W_mag / SQRT_5;
                W_y = W_y / SQRT_3 + (2 * ThreadLocalRandom.current().nextDouble() - 1) * W_mag / SQRT_5;
            } else {
                // Near target - dampen wind
                W_x /= SQRT_3;
                W_y /= SQRT_3;
                // Critical M_0 adjustment logic from Python (lines 249-252)
                if (M_0 < 3) {
                    M_0 = ThreadLocalRandom.current().nextDouble() * 3 + 3;
                } else {
                    M_0 /= SQRT_5;
                }
            }

            // Apply gravity and wind to velocity (Python lines 254-255)
            v_x += W_x + G_0 * deltaX / distance;
            v_y += W_y + G_0 * deltaY / distance;

            // Clip velocity if exceeding maximum (Python lines 256-261)
            double v_mag = Math.sqrt(v_x * v_x + v_y * v_y);
            if (v_mag > M_0) {
                double v_clip = M_0 / 2.0 + ThreadLocalRandom.current().nextDouble() * M_0 / 2.0;
                v_x = (v_x / v_mag) * v_clip;
                v_y = (v_y / v_mag) * v_clip;
            }

            // Update physics position (Python lines 263-264)
            loop_start_x += v_x;
            loop_start_y += v_y;

            // Convert to integer coordinates for mouse dispatch
            int move_x = (int) Math.round(loop_start_x);
            int move_y = (int) Math.round(loop_start_y);

            // Dispatch mouse event only if position changed (Python lines 269-271)
            if (current_x != move_x || current_y != move_y) {
                dispatchMouseMoveEvent(move_x, move_y);
                current_x = move_x;
                current_y = move_y;
            }

            // Sleep for realistic timing
            try {
                int minDelay = config.windmouseMinStepDelay();
                int maxDelay = config.windmouseMaxStepDelay();
                int delay = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Windmouse movement {} interrupted", movementId);
                break;
            }
        }

        // Final move to ensure we are at the destination (Python lines 273-274)
        dispatchMouseMoveEvent(destination.x, destination.y);

        // Calculate final results
        long duration = System.currentTimeMillis() - startTime;
        boolean cancelled = !movementId.equals(currentMovementId.get()) || !isMoving.get();
        Point finalPosition = new Point(destination.x, destination.y);

        // Clean up state
        if (movementId.equals(currentMovementId.get())) {
            isMoving.set(false);
            currentMovementId.set(null);
            currentMovement.set(null);
        }

        // Publish completion event
        eventService.publish(new MouseMovementCompletedEvent(movementId, finalPosition, duration, cancelled));

        log.debug("Windmouse movement {} completed in {}ms, cancelled={}", movementId, duration, cancelled);
    }

    /**
     * Move the mouse cursor using RemoteInput.
     * This replaces the direct dispatchEvent mechanism with native input.
     * 
     * @param x target X coordinate
     * @param y target Y coordinate
     */
    private void dispatchMouseMoveEvent(int x, int y) {
        try {
            remoteInputService.moveMouse(x, y);
        } catch (Exception e) {
            log.warn("Error moving mouse to ({}, {}): {}", x, y, e.getMessage());
        }
    }
}