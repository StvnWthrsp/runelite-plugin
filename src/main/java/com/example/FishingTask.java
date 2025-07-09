package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.*;

@Slf4j
public class FishingTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;

    private enum FishingState {
        IDLE,
        WALKING_TO_FISHING,
        FISHING,
        WAIT_FISHING,
        WALKING_TO_COOKING,
        WALKING_TO_BANK,
        DEPOSITING,
        WITHDRAWING,
        WAITING_FOR_SUBTASK
    }

    // Lumbridge Swamp fishing spot (net fishing)
    private static final WorldPoint LUMBRIDGE_SWAMP_FISHING = new WorldPoint(3241, 3149, 0);
    // Lumbridge Castle kitchen range
    private static final WorldPoint LUMBRIDGE_KITCHEN_RANGE = new WorldPoint(3211, 3215, 0);
    // Lumbridge Castle bank (upstairs)
    private static final WorldPoint LUMBRIDGE_BANK = new WorldPoint(3208, 3220, 2);

    // Fishing spot and range object IDs
    private static final int FISHING_SPOT_ID = 1530; // Net fishing spot
    private static final int KITCHEN_RANGE_ID = 114; // Cooking range

    // Fish item IDs
    private static final int RAW_SHRIMP_ID = ItemID.RAW_SHRIMPS;
    private static final int RAW_ANCHOVIES_ID = ItemID.RAW_ANCHOVIES;
    private static final int COOKED_SHRIMP_ID = ItemID.SHRIMPS;
    private static final int COOKED_ANCHOVIES_ID = ItemID.ANCHOVIES;

    // Animation IDs
    private static final int COOKING_ANIMATION_ID = AnimationID.HUMAN_COOKING;
    private static final int COOKING_ANIMATION_LOOP_ID = AnimationID.HUMAN_COOKING_LOOP;

    private FishingState currentState = null;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int delayTicks = 0;
    private int idleTicks = 0;
    private NPC fishingSpot = null;
    private boolean fishingStarted = false;

    public FishingTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, 
                      PathfinderConfig pathfinderConfig, ActionService actionService, 
                      GameService gameService, EventService eventService, HumanizerService humanizerService) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.pathfinderConfig = pathfinderConfig;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Fishing Task.");
        determineNextState();
        this.eventService.subscribe(GameTick.class, this::onGameTick);
    }

    @Override
    public void onStop() {
        log.info("Stopping Fishing Task.");
        this.fishingSpot = null;
        this.eventService.unsubscribe(GameTick.class, this::onGameTick);
    }

    @Override
    public boolean isFinished() {
        return false; // Runs indefinitely until stopped
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    @Override
    public String getTaskName() {
        return "Fishing";
    }

    @Override
    public void onLoop() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (taskManager.getCurrentTask() != this) {
            if (currentState != FishingState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing FishingTask.");
                currentState = FishingState.WAITING_FOR_SUBTASK;
            }
            return;
        } else {
            if (currentState == FishingState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task finished. Resuming FishingTask.");
                determineNextState();
            }
        }

        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        switch (currentState) {
            case WALKING_TO_FISHING:
                doWalkingToFishing();
                break;
            case FISHING:
                doFishing();
                break;
            case WAIT_FISHING:
                doWaitFishing();
                break;
            case WALKING_TO_COOKING:
                doWalkingToCooking();
                break;
            case WALKING_TO_BANK:
                doWalkingToBank();
                break;
            case DEPOSITING:
                doDepositing();
                break;
            case WITHDRAWING:
                doWithdrawing();
                break;
            case IDLE:
                currentState = FishingState.WALKING_TO_FISHING;
                break;
            case WAITING_FOR_SUBTASK:
                // Handled above
                break;
        }
        plugin.setCurrentState(currentState.toString());
    }

    public void onGameTick(GameTick gameTick) {
        // Handle fishing completion detection
        if (currentState == FishingState.WAIT_FISHING && fishingStarted) {
            if (gameService.isInventoryFull()) {
                finishFishing();
            }
        }
        
    }


    private void doWalkingToFishing() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        if (playerLocation.distanceTo(LUMBRIDGE_SWAMP_FISHING) <= 5) {
            log.info("Arrived at fishing area");
            currentState = FishingState.FISHING;
        } else {
            log.info("Walking to Lumbridge Swamp fishing area");
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, LUMBRIDGE_SWAMP_FISHING, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }

    private void doFishing() {
        if (gameService.isInventoryFull()) {
            log.info("Inventory full, moving to cooking");
            currentState = FishingState.WALKING_TO_COOKING;
            return;
        }

        // Find fishing spot
        fishingSpot = gameService.findNearestNpc(FISHING_SPOT_ID);
        if (fishingSpot == null) {
            log.warn("No fishing spot found");
            delayTicks = humanizerService.getRandomDelay(2, 5);
            return;
        }

        log.info("Clicking on fishing spot");
        actionService.sendClickRequest(gameService.getRandomClickablePoint(fishingSpot), true);
        fishingStarted = false;
        idleTicks = 0;
        currentState = FishingState.WAIT_FISHING;
    }

    private void doWaitFishing() {
        idleTicks++;
        
        // Check if we're actually fishing (player animation or interacting)
        if (plugin.getClient().getLocalPlayer().getInteracting() == fishingSpot) {
            fishingStarted = true;
            idleTicks = 0;
        }
        
        if (idleTicks > 10) { // 10 ticks = 6 seconds
            log.warn("Fishing seems to have failed. Retrying.");
            currentState = FishingState.FISHING;
        }
    }

    private void finishFishing() {
        log.info("Finished fishing, inventory full");
        fishingSpot = null;
        fishingStarted = false;
        currentState = FishingState.WALKING_TO_COOKING;
    }

    private void doWalkingToCooking() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        if (playerLocation.distanceTo(LUMBRIDGE_KITCHEN_RANGE) <= 10) {
            log.info("Arrived at kitchen, starting cooking task");
            CookingTask cookingTask = new CookingTask(plugin, actionService, gameService, eventService, humanizerService,
                    LUMBRIDGE_KITCHEN_RANGE, KITCHEN_RANGE_ID,
                    new int[]{RAW_SHRIMP_ID, RAW_ANCHOVIES_ID});
            taskManager.pushTask(cookingTask);
            currentState = FishingState.WAITING_FOR_SUBTASK;
        } else {
            log.info("Walking to Lumbridge Castle kitchen");
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, LUMBRIDGE_KITCHEN_RANGE, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }


    private void doWalkingToBank() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        if (playerLocation.distanceTo(LUMBRIDGE_BANK) <= 5) {
            log.info("Arrived at bank");
            currentState = FishingState.DEPOSITING;
        } else {
            log.info("Walking to Lumbridge Castle bank");
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, LUMBRIDGE_BANK, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }

    private void doDepositing() {
        log.info("Banking all items");
        taskManager.pushTask(new BankTask(plugin, actionService, gameService));
        currentState = FishingState.WAITING_FOR_SUBTASK;
    }

    private void doWithdrawing() {
        log.info("Withdrawing small fishing net");
        // TODO: Move withdrawing logic to BankTask
        ItemContainer bankContainer = plugin.getClient().getItemContainer(InventoryID.BANK);
        int smallFishingNetIndex = bankContainer.find(ItemID.SMALL_FISHING_NET);
        actionService.sendClickRequest(gameService.getBankItemPoint(smallFishingNetIndex), true);
        currentState = FishingState.WAITING_FOR_SUBTASK;
        delayTicks = humanizerService.getRandomDelay(1, 2);
    }

    private void determineNextState() {
        // After completing a subtask, determine what to do next
        if (this.currentState == FishingState.WAITING_FOR_SUBTASK) {
            log.debug("DEBUG: Returned from subtask. Determining next step.");
        } else {
            log.debug("DEBUG: Task just started. Determining next step.");
        }
        if (gameService.isInventoryFull()) {
            if (hasRawFish()) {
                currentState = FishingState.WALKING_TO_COOKING;
            } else {
                currentState = FishingState.WALKING_TO_BANK;
            }
        } else if (!gameService.hasItem(ItemID.SMALL_FISHING_NET)) {
            currentState = FishingState.WITHDRAWING;
        } else {
            currentState = FishingState.WALKING_TO_FISHING;
        }
    }

    private boolean hasRawFish() {
        return gameService.hasItem(RAW_SHRIMP_ID) || gameService.hasItem(RAW_ANCHOVIES_ID);
    }

    private int getRawFishId() {
        if (gameService.hasItem(RAW_SHRIMP_ID)) {
            return RAW_SHRIMP_ID;
        } else if (gameService.hasItem(RAW_ANCHOVIES_ID)) {
            return RAW_ANCHOVIES_ID;
        }
        return -1;
    }
}