package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * CameraRotationTask - An atomic, high-priority FSM-based BotTask for camera rotation.
 * 
 * This task rotates the game camera by 30-90 degrees using target angle monitoring 
 * for AFK prevention. It uses event-driven angle monitoring via GameTick events
 * and supports both standalone and remote automation modes.
 */
@Slf4j
public class CameraRotationTask implements BotTask {

    private enum CameraRotationState {
        IDLE,
        CALCULATING_TARGET,
        ROTATING,
        MONITORING_ANGLE,
        FINISHED
    }

    private final Client client;
    private final ActionService actionService;
    private final EventService eventService;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    
    private CameraRotationState currentState;
    private boolean isStarted = false;
    private int targetYaw;
    private int initialYaw;
    private boolean rotatingLeft;
    private int timeoutTicks = 0;
    private static final int MAX_TIMEOUT_TICKS = 8; // ~5 seconds at 600ms per tick
    private static final int ANGLE_TOLERANCE = 5; // ±5 degrees tolerance
    private static final int JAGEX_ANGLE_MAX = 2048; // Jagex angle units for full circle
    
    // Event handler stored as instance variable for proper unsubscription
    private Consumer<GameTick> gameTickHandler;

    @Inject
    public CameraRotationTask(RunepalPlugin plugin, ActionService actionService, EventService eventService) {
        this.client = Objects.requireNonNull(plugin.getClient(), "client cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.currentState = CameraRotationState.IDLE;
    }

    @Override
    public void onStart() {
        log.info("Starting camera rotation task");
        isStarted = true;
        timeoutTicks = 0;
        
        // Store event handler reference for proper unsubscription
        this.gameTickHandler = this::onGameTick;
        eventService.subscribe(GameTick.class, gameTickHandler);
        
        // Initialize state transition via ActionQueue
        actionQueue.add(() -> currentState = CameraRotationState.CALCULATING_TARGET);
    }

    @Override
    public void onLoop() {
        // Process ActionQueue first - critical for proper state transitions
        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        switch (currentState) {
            case IDLE:
                // Should not reach this state during normal execution
                log.debug("Camera rotation task in IDLE state");
                break;
                
            case CALCULATING_TARGET:
                calculateTargetAngle();
                break;
                
            case ROTATING:
                startRotation();
                break;
                
            case MONITORING_ANGLE:
                // Angle monitoring handled by GameTick event
                // Check for timeout here
                if (timeoutTicks >= MAX_TIMEOUT_TICKS) {
                    log.warn("Camera rotation timed out after {} ticks", timeoutTicks);
                    handleTimeout();
                }
                break;
                
            case FINISHED:
                // Task completed, will be removed by TaskManager
                break;
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping camera rotation task");
        
        // Ensure any held keys are released to prevent stuck input
        forceKeyRelease();
        
        // Unsubscribe from GameTick events
        if (gameTickHandler != null) {
            eventService.unsubscribe(GameTick.class, gameTickHandler);
            gameTickHandler = null;
        }
        
        isStarted = false;
    }

    @Override
    public boolean isFinished() {
        return currentState == CameraRotationState.FINISHED;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public String getTaskName() {
        return "Camera Rotation";
    }

    /**
     * GameTick event handler for precise angle monitoring every ~600ms
     */
    public void onGameTick(GameTick gameTick) {
        timeoutTicks++;
        
        if (currentState == CameraRotationState.MONITORING_ANGLE) {
            int currentYaw = client.getCameraYaw();
            
            // Check if target angle has been reached within tolerance
            if (isAngleWithinTolerance(currentYaw, targetYaw, ANGLE_TOLERANCE)) {
                log.debug("Target angle reached: current={}, target={}", currentYaw, targetYaw);
                
                // Release key and finish task
                actionQueue.add(() -> {
                    releaseRotationKey();
                    currentState = CameraRotationState.FINISHED;
                });
            }
        }
    }

    /**
     * Calculate random target angle with 30-90 degree offset
     */
    private void calculateTargetAngle() {
        initialYaw = client.getCameraYaw();
        
        // Generate random offset between 30-90 degrees
        int offsetDegrees = ThreadLocalRandom.current().nextInt(30, 91);
        int offsetAngleUnits = degreesToJagexAngle(offsetDegrees);
        
        // Randomly choose direction (left = negative, right = positive)
        boolean rotateLeft = ThreadLocalRandom.current().nextBoolean();
        this.rotatingLeft = rotateLeft;
        
        if (rotateLeft) {
            targetYaw = initialYaw - offsetAngleUnits;
        } else {
            targetYaw = initialYaw + offsetAngleUnits;
        }
        
        // Handle 360° wraparound
        targetYaw = normalizeJagexAngle(targetYaw);
        
        log.debug("Camera rotation calculated: initial={}, target={}, offset={}°, direction={}", 
                  initialYaw, targetYaw, offsetDegrees, rotateLeft ? "left" : "right");
        
        // Transition to rotation state
        actionQueue.add(() -> currentState = CameraRotationState.ROTATING);
    }

    /**
     * Start camera rotation by holding the appropriate arrow key
     */
    private void startRotation() {
        String keyToHold = rotatingLeft ? "left" : "right";
        
        log.debug("Starting camera rotation: holding {} arrow key", keyToHold);
        actionService.sendKeyRequest("/key_hold", keyToHold);
        
        // Reset timeout counter and transition to monitoring state
        timeoutTicks = 0;
        actionQueue.add(() -> currentState = CameraRotationState.MONITORING_ANGLE);
    }

    /**
     * Release the currently held rotation key
     */
    private void releaseRotationKey() {
        String keyToRelease = rotatingLeft ? "left" : "right";
        
        log.debug("Releasing {} arrow key", keyToRelease);
        actionService.sendKeyRequest("/key_release", keyToRelease);
    }

    /**
     * Force release both arrow keys (safety mechanism)
     */
    private void forceKeyRelease() {
        log.debug("Force releasing all arrow keys");
        actionService.sendKeyRequest("/key_release", "left");
        actionService.sendKeyRequest("/key_release", "right");
    }

    /**
     * Handle timeout by releasing keys and finishing task
     */
    private void handleTimeout() {
        log.warn("Camera rotation timeout - releasing keys and finishing task");
        actionQueue.add(() -> {
            forceKeyRelease();
            currentState = CameraRotationState.FINISHED;
        });
    }

    /**
     * Check if current angle is within tolerance of target angle
     * Handles 360° wraparound correctly
     */
    private boolean isAngleWithinTolerance(int currentAngle, int targetAngle, int tolerance) {
        int diff = Math.abs(currentAngle - targetAngle);
        
        // Handle wraparound case (e.g., current=10, target=2040)
        int wraparoundDiff = JAGEX_ANGLE_MAX - diff;
        
        return Math.min(diff, wraparoundDiff) <= tolerance;
    }

    /**
     * Convert degrees to Jagex angle units
     * Jagex uses 2048 units for a full 360° circle
     */
    private int degreesToJagexAngle(int degrees) {
        return (degrees * JAGEX_ANGLE_MAX) / 360;
    }

    /**
     * Normalize Jagex angle to 0-2047 range
     */
    private int normalizeJagexAngle(int angle) {
        while (angle < 0) {
            angle += JAGEX_ANGLE_MAX;
        }
        while (angle >= JAGEX_ANGLE_MAX) {
            angle -= JAGEX_ANGLE_MAX;
        }
        return angle;
    }
}