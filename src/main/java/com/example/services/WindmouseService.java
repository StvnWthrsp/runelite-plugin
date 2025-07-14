package com.example.services;

import com.example.BotConfig;
import com.example.EventService;
import com.example.MouseMovementCompletedEvent;
import com.example.RunepalPlugin;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that provides human-like mouse movement using the Windmouse algorithm.
 * This physics-based approach simulates realistic mouse movement patterns
 * with gravity, wind forces, and natural acceleration/deceleration.
 */
@Singleton
@Slf4j
public class WindmouseService {
    
    private final RunepalPlugin plugin;
    private final EventService eventService;
    private final BotConfig config;
    
    // Movement state
    private final AtomicBoolean isMoving = new AtomicBoolean(false);
    private final AtomicReference<String> currentMovementId = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> currentMovement = new AtomicReference<>();
    
    // Mathematical constants
    private static final double SQRT_3 = Math.sqrt(3.0);
    private static final double SQRT_5 = Math.sqrt(5.0);
    
    @Inject
    public WindmouseService(RunepalPlugin plugin, EventService eventService, BotConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }
    
    /**
     * Start asynchronous movement to target point using Windmouse algorithm.
     * @param start starting position
     * @param destination target position
     * @param movementId unique identifier for this movement
     */
    public void moveToPoint(Point start, Point destination, String movementId) {
        if (start == null || destination == null || movementId == null) {
            log.warn("Invalid parameters for moveToPoint: start={}, destination={}, movementId={}", start, destination, movementId);
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
     * @return true if movement is active, false otherwise
     */
    public boolean isMoving() {
        return isMoving.get();
    }
    
    /**
     * Get the current movement ID if one is active.
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
        
        // Initialize physics state
        double currentX = start.x;
        double currentY = start.y;
        double vX = 0.0;
        double vY = 0.0;
        double wX = 0.0;
        double wY = 0.0;
        
        // Track actual mouse position for event dispatch
        int lastMouseX = start.x;
        int lastMouseY = start.y;
        
        log.debug("Starting Windmouse movement {} from {} to {}", movementId, start, destination);
        
        // Main physics loop
        while (isMoving.get() && movementId.equals(currentMovementId.get())) {
            // Calculate distance to destination
            double deltaX = destination.x - currentX;
            double deltaY = destination.y - currentY;
            double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            
            // Stop if we're close enough
            if (distance < 1.0) {
                break;
            }
            
            // Update wind forces based on distance
            if (distance >= D_0) {
                // Far from target - add random wind perturbation
                double wMag = Math.min(W_0, distance);
                wX = wX / SQRT_3 + (2 * ThreadLocalRandom.current().nextDouble() - 1) * wMag / SQRT_5;
                wY = wY / SQRT_3 + (2 * ThreadLocalRandom.current().nextDouble() - 1) * wMag / SQRT_5;
            } else {
                // Near target - dampen wind
                wX /= SQRT_3;
                wY /= SQRT_3;
            }
            
            // Apply gravity and wind to velocity
            vX += wX + G_0 * deltaX / distance;
            vY += wY + G_0 * deltaY / distance;
            
            // Clip velocity if exceeding maximum
            double vMag = Math.sqrt(vX * vX + vY * vY);
            if (vMag > M_0) {
                double vClip = M_0 / 2.0 + ThreadLocalRandom.current().nextDouble() * M_0 / 2.0;
                vX = (vX / vMag) * vClip;
                vY = (vY / vMag) * vClip;
            }
            
            // Update position
            currentX += vX;
            currentY += vY;
            
            // Convert to integer coordinates
            int mouseX = (int) Math.round(currentX);
            int mouseY = (int) Math.round(currentY);
            
            // Dispatch mouse event only if position changed
            if (mouseX != lastMouseX || mouseY != lastMouseY) {
                dispatchMouseMoveEvent(mouseX, mouseY);
                lastMouseX = mouseX;
                lastMouseY = mouseY;
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
        
        // Calculate final results
        long duration = System.currentTimeMillis() - startTime;
        boolean cancelled = !movementId.equals(currentMovementId.get()) || !isMoving.get();
        Point finalPosition = new Point(lastMouseX, lastMouseY);
        
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
     * Dispatch a mouse move event to the game canvas.
     * @param x target X coordinate
     * @param y target Y coordinate
     */
    private void dispatchMouseMoveEvent(int x, int y) {
        try {
            MouseEvent mouseMoved = new MouseEvent(
                plugin.getClient().getCanvas(),
                MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(),
                0,
                x,
                y,
                0,
                false
            );
            plugin.getClient().getCanvas().dispatchEvent(mouseMoved);
        } catch (Exception e) {
            log.warn("Error dispatching mouse move event to ({}, {}): {}", x, y, e.getMessage());
        }
    }
}