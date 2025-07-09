package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.AnimationID;

import java.util.Objects;

@Slf4j
public class CookingTask implements BotTask {

    private enum CookingState {
        WALKING_TO_RANGE,
        COOKING,
        WAIT_COOKING,
        FINISHED
    }

    private final RunepalPlugin plugin;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private final WorldPoint rangeLocation;
    private final int rangeObjectId;
    private final int[] rawFishIds;

    private CookingState currentState = CookingState.WALKING_TO_RANGE;
    private boolean cookingStarted = false;
    private int idleTicks = 0;
    private GameObject cookingRange = null;
    private int delayTicks = 0;

    // Animation IDs
    private static final int COOKING_ANIMATION_ID = AnimationID.HUMAN_COOKING;
    private static final int COOKING_ANIMATION_LOOP_ID = AnimationID.HUMAN_COOKING_LOOP;

    public CookingTask(RunepalPlugin plugin, ActionService actionService, GameService gameService,
                      EventService eventService, HumanizerService humanizerService, 
                      WorldPoint rangeLocation, int rangeObjectId, int[] rawFishIds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.rangeLocation = Objects.requireNonNull(rangeLocation, "rangeLocation cannot be null");
        this.rangeObjectId = rangeObjectId;
        this.rawFishIds = Objects.requireNonNull(rawFishIds, "rawFishIds cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Cooking Task at location: {}", rangeLocation);
        this.currentState = CookingState.WALKING_TO_RANGE;
        this.eventService.subscribe(GameTick.class, this::onGameTick);
    }

    @Override
    public void onStop() {
        log.info("Stopping Cooking Task.");
        this.cookingRange = null;
        this.eventService.unsubscribe(GameTick.class, this::onGameTick);
    }

    @Override
    public boolean isFinished() {
        return currentState == CookingState.FINISHED;
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    @Override
    public String getTaskName() {
        return "Cooking";
    }

    @Override
    public void onLoop() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        switch (currentState) {
            case WALKING_TO_RANGE:
                doWalkingToRange();
                break;
            case COOKING:
                doCooking();
                break;
            case WAIT_COOKING:
                doWaitCooking();
                break;
            case FINISHED:
                // Task is finished, do nothing
                break;
        }
        plugin.setCurrentState(currentState.toString());
    }

    public void onGameTick(GameTick gameTick) {
        if (currentState == CookingState.WAIT_COOKING) {
            if (!gameService.isCurrentAnimation(COOKING_ANIMATION_ID)) {
                finishCooking();
            }
        }
    }

    private void doWalkingToRange() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        if (playerLocation.distanceTo(rangeLocation) <= 10) {
            log.info("Arrived at cooking range");
            currentState = CookingState.COOKING;
        } else {
            log.info("Player not close enough to range. Distance: {}", playerLocation.distanceTo(rangeLocation));
            currentState = CookingState.FINISHED; // Let calling task handle walking
        }
    }

    private void doCooking() {
        if (!hasRawFish()) {
            log.info("No raw fish to cook, task finished");
            currentState = CookingState.FINISHED;
            return;
        }

        // Find cooking range
        cookingRange = gameService.findNearestGameObject(rangeObjectId);
        if (cookingRange == null) {
            log.warn("No cooking range found");
            delayTicks = humanizerService.getRandomDelay(1, 2);
            return;
        }

        log.info("Starting cooking process");
        // First, use raw fish on the range
        int rawFishId = getRawFishId();
        if (rawFishId != -1) {
            actionService.interactWithGameObject(cookingRange, "Cook");
            cookingStarted = false;
            idleTicks = 0;
            currentState = CookingState.WAIT_COOKING;
        }
    }

    private void doWaitCooking() {
        // Check if we're currently performing the cooking animation
        if (gameService.isCurrentAnimation(COOKING_ANIMATION_ID)) {
            cookingStarted = true;
            idleTicks = 0; // Reset idle counter if we see a cooking animation
        } else {
            idleTicks++; // Only increment idle ticks if not cooking
        }
        
        // Check if cooking interface appeared or we're in cooking animation
        if (idleTicks > 3 && !cookingStarted) {
            // After a few ticks, click to start cooking all
            log.info("Clicking to cook all fish");
            actionService.sendSpacebarRequest(); // Space bar to cook all
            idleTicks = 0;
        }
        
        if (idleTicks > 30) { // 30 ticks = 18 seconds timeout
            log.warn("Cooking seems to have failed or finished. Checking inventory.");
            finishCooking();
        }
    }

    private void finishCooking() {
        log.info("Finished cooking");
        cookingRange = null;
        cookingStarted = false;
        if (hasRawFish()) {
            // Still have raw fish, continue cooking
            currentState = CookingState.COOKING;
        } else {
            // No more raw fish, task is complete
            currentState = CookingState.FINISHED;
        }
    }

    /**
     * Check if the player has any raw fish that can be cooked.
     * 
     * @return true if player has raw fish, false otherwise
     */
    private boolean hasRawFish() {
        for (int rawFishId : rawFishIds) {
            if (gameService.hasItem(rawFishId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the ID of the first raw fish found in inventory.
     * 
     * @return raw fish ID if found, -1 otherwise
     */
    private int getRawFishId() {
        for (int rawFishId : rawFishIds) {
            if (gameService.hasItem(rawFishId)) {
                return rawFishId;
            }
        }
        return -1;
    }
}