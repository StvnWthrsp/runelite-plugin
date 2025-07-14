package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
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
        DROPPING_FISH,
        WALKING_TO_COOKING,
        COOKING,
        WAIT_COOKING,
        WALKING_TO_BANK,
        DEPOSITING,
        WITHDRAWING,
        INTERACTING_WITH_RANGE, WAITING_FOR_SUBTASK
    }

    // Lumbridge Swamp fishing spot (net fishing)
    private static final WorldPoint LUMBRIDGE_SWAMP_FISHING = new WorldPoint(3241, 3149, 0);
    // Lumbridge Castle kitchen range
    private static final WorldPoint LUMBRIDGE_KITCHEN_RANGE = new WorldPoint(3211, 3215, 0);
    // Barbarian village fishing spot (fly fishing)
    private static final WorldPoint BARBARIAN_VILLAGE = new WorldPoint(3109, 3433, 0);

    // Fishing spot and range object IDs
    private static final int NET_FISHING_SPOT_ID = 1530;
    private static final int ROD_FISHING_SPOT_ID = 1526;
    private static final int KITCHEN_RANGE_ID = 114;
    private static final int BARBARIAN_VILLAGE_FIRE_ID = 43475;

    // Fish item IDs
    private static final int RAW_SHRIMP_ID = ItemID.RAW_SHRIMP;
    private static final int RAW_ANCHOVIES_ID = ItemID.RAW_ANCHOVIES;
    private static final int RAW_TROUT_ID = ItemID.RAW_TROUT;
    private static final int RAW_SALMON_ID = ItemID.RAW_SALMON;

    private FishingState currentState = null;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int delayTicks = 0;
    private int idleTicks = 0;
    private final int retryLimit = 5;
    private int retries = 0;
    private NPC fishingSpot = null;
    private GameObject cookingRange = null;
    private boolean fishingStarted = false;
    private boolean cookingStarted = false;

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
        if (this.currentState == FishingState.WAITING_FOR_SUBTASK) {
            log.info("Returned from subtask. Determining next step.");
        } else {
            log.info("Task just started. Determining next step.");
        }
        determineNextState();
        this.eventService.subscribe(GameTick.class, this::onGameTick);
        eventService.subscribe(InteractionCompletedEvent.class, this::onInteractionCompleted);
    }

    @Override
    public void onStop() {
        log.info("Stopping Fishing Task.");
        this.fishingSpot = null;
        this.cookingRange = null;
        this.eventService.unsubscribe(GameTick.class, this::onGameTick);
        this.eventService.unsubscribe(InteractionCompletedEvent.class, this::onInteractionCompleted);
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
            case DROPPING_FISH:
                if (!actionService.isDropping()) {
                    log.info("Dropping complete. Resuming mining.");
                    currentState = FishingState.FISHING;
                }
                break;
            case WALKING_TO_COOKING:
                doWalkingToCooking();
                break;
            case INTERACTING_WITH_RANGE:
                // State transition handled by InteractionCompletedEvent
                break;
            case COOKING:
                doCooking();
                break;
            case WAIT_COOKING:
                doWaitCooking();
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
                determineNextState();
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

    private void onInteractionCompleted(InteractionCompletedEvent event) {
        if (currentState == FishingState.INTERACTING_WITH_RANGE) {
            if(event.isSuccess()) {
                log.info("Interacting with range complete. Beginning to cook.");
                delayTicks = humanizerService.getShortDelay();
                cookingStarted = false;
                idleTicks = 0;
                currentState = FishingState.WAIT_COOKING;
            } else {
                log.info("Interacting with range failed. Retrying.");
                currentState = FishingState.COOKING;
            }
        }
    }

    private void doWalkingToFishing() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        WorldPoint fishingLocation = getFishingLocation();
        if (playerLocation.distanceTo(fishingLocation) <= 5) {
            log.info("Arrived at fishing area: {}", config.fishingArea());
            currentState = FishingState.FISHING;
        } else {
            log.info("Walking to {} fishing area", config.fishingArea());
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, fishingLocation, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }

    private void doFishing() {
        if (gameService.isInventoryFull()) {
            log.info("Inventory full, determining next action based on configuration");
            if (config.fishingMode() == FishingMode.POWER_DROP) {
                currentState = FishingState.DROPPING_FISH;
                doDroppingFish();
            } else if (config.cookFish()) {
                currentState = FishingState.WALKING_TO_COOKING;
            } else {
                currentState = FishingState.WALKING_TO_BANK;
            }
            return;
        }

        // Find fishing spot using configured spot type
        int fishingSpotId = getFishingSpotId();
        fishingSpot = gameService.findNearestNpc(fishingSpotId);
        if (fishingSpot == null) {
            log.warn("No {} fishing spot found", config.fishingSpot());
            delayTicks = humanizerService.getRandomDelay(2, 5);
            return;
        }

        log.info("Clicking on {} fishing spot", config.fishingSpot());
        actionService.sendClickRequest(gameService.getRandomClickablePoint(fishingSpot), true);
        fishingStarted = false;
        idleTicks = 0;
        currentState = FishingState.WAIT_FISHING;
    }

    private void doWaitFishing() {
        if (retries >= retryLimit) {
            log.warn("Fishing failed 5 times. Restarting.");
            currentState = FishingState.IDLE;
            retries = 0;
        }

        idleTicks++;
        
        // Check if we're actually fishing (player animation or interacting)
        if (plugin.getClient().getLocalPlayer().getInteracting() == fishingSpot) {
            fishingStarted = true;
            retries = 0;
            idleTicks = 0;
        }
        
        if (idleTicks > 10) { // 10 ticks = 6 seconds
            log.warn("Fishing seems to have failed. Retrying.");
            retries++;
            currentState = FishingState.FISHING;
        }
    }

    private void finishFishing() {
        log.info("Finished fishing, inventory full");
        fishingSpot = null;
        fishingStarted = false;
        if (config.fishingMode() == FishingMode.POWER_DROP) {
            currentState = FishingState.DROPPING_FISH;
            doDroppingFish();
        } else if (config.cookFish()) {
            currentState = FishingState.WALKING_TO_COOKING;
        } else {
            currentState = FishingState.WALKING_TO_BANK;
        }
    }

    private void doDroppingFish() {
        log.info("Power dropping fish");
        int[] itemIds = {RAW_TROUT_ID, RAW_SALMON_ID};
        actionService.powerDrop(itemIds);
    }

    private void doWalkingToCooking() {
        if (!config.cookFish()) {
            // Skip cooking if not enabled
            currentState = FishingState.WALKING_TO_BANK;
            return;
        }
        
        WorldPoint playerLocation = gameService.getPlayerLocation();
        WorldPoint cookingLocation = getCookingLocation();
        if (playerLocation.distanceTo(cookingLocation) <= 5) {
            log.info("Arrived at cooking location: {}", config.fishingArea());
            currentState = FishingState.COOKING;
            delayTicks = humanizerService.getMediumDelay();
        } else {
            String locationName = config.fishingArea() == FishingArea.BARBARIAN_VILLAGE ? "Barbarian Village fire" : "Lumbridge Castle kitchen";
            log.info("Walking to {}", locationName);
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, cookingLocation, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }

    private void doCooking() {
        if (!hasRawFish()) {
            log.info("No raw fish to cook, determining next action");
            if (config.fishingMode() == FishingMode.POWER_DROP) {
                currentState = FishingState.FISHING; // Return to fishing
            } else {
                currentState = FishingState.WALKING_TO_BANK;
            }
            return;
        }

        // Find cooking range/fire using configured location
        int cookingRangeId = getCookingRangeId();
        cookingRange = gameService.findNearestGameObject(cookingRangeId);
        if (cookingRange == null) {
            String cookingType = config.fishingArea() == FishingArea.BARBARIAN_VILLAGE ? "fire" : "range";
            log.warn("No cooking {} found", cookingType);
            delayTicks = humanizerService.getRandomDelay(1, 2);
            return;
        }

        log.info("Starting cooking process");
        // First, use raw fish on the range/fire
        int rawFishId = getRawFishId();
        if (rawFishId != -1) {
            // Check if ActionService is already interacting to prevent duplicate interactions
            if (!actionService.isInteracting()) {
                actionService.interactWithGameObject(cookingRange, "Cook");
                currentState = FishingState.INTERACTING_WITH_RANGE;
            }
        }
    }

    private void doWaitCooking() {
        // Check if we're currently performing the cooking animation
        if (gameService.isCurrentlyCooking()) {
            cookingStarted = true;
            idleTicks = 0; // Reset idle counter if we see a cooking animation
        } else {
            idleTicks++; // Only increment idle ticks if not cooking
        }

        int waitForInterfaceTicks = humanizerService.getMediumDelay();
        if (idleTicks > waitForInterfaceTicks && !cookingStarted) {
            actionService.sendSpacebarRequest(); // Space bar to cook all
            cookingStarted = true;
            idleTicks = 0;
            return;
        }
        
        if (idleTicks > 5) { // 30 ticks = 18 seconds timeout
            log.warn("Cooking seems to have failed or finished. Finishing.");
            finishCooking();
        }
    }

    private void finishCooking() {
        log.info("Finished cooking");
        cookingRange = null;
        cookingStarted = false;
        if (hasRawFish()) {
            currentState = FishingState.COOKING; // Continue cooking remaining fish
        } else {
            // All fish cooked, determine next action
            if (config.fishingMode() == FishingMode.POWER_DROP) {
                currentState = FishingState.FISHING; // Return to fishing
            } else {
                currentState = FishingState.WALKING_TO_BANK; // Bank the cooked fish
            }
        }
    }

    private void doWalkingToBank() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        WorldPoint bankLocation = getBankLocation();
        if (playerLocation.distanceTo(bankLocation) <= 5) {
            log.info("Arrived at bank");
            currentState = FishingState.DEPOSITING;
        } else {
            log.info("Walking to bank");
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, bankLocation, actionService, gameService, humanizerService));
            currentState = FishingState.WAITING_FOR_SUBTASK;
        }
    }

    private void doDepositing() {
        log.info("Banking all items");
        taskManager.pushTask(new BankTask(plugin, actionService, gameService, eventService));
        currentState = FishingState.WAITING_FOR_SUBTASK;
    }

    private void doWithdrawing() {
        int requiredTool = getRequiredToolId();
        int requiredBait = getRequiredBaitId();
        String toolName = config.fishingSpot() == FishingSpot.NET ? "small fishing net" : "fly fishing rod";
        log.info("Withdrawing {}", toolName);
        
        // TODO: Move withdrawing logic to BankTask
        ItemContainer bankContainer = plugin.getClient().getItemContainer(InventoryID.BANK);
        if (bankContainer == null) {
            log.warn("Bank container not found. Walking to bank again.");
            currentState = FishingState.WALKING_TO_BANK;
            return;
        }
        int toolIndex = bankContainer.find(requiredTool);
        if (toolIndex != -1) {
            actionService.sendClickRequest(gameService.getBankItemPoint(toolIndex), true);
            currentState = FishingState.WAITING_FOR_SUBTASK;
            delayTicks = humanizerService.getRandomDelay(1, 2);
            return;
        } else {
            log.warn("Required tool {} not found in bank", toolName);
        }
        if (requiredBait != -1) {
            int baitIndex = bankContainer.find(requiredBait);
            if (baitIndex != -1) {
                actionService.sendClickRequest(gameService.getBankItemPoint(baitIndex), true);
            }
        } else {
            log.warn("Required bait not found in bank");
        }
        currentState = FishingState.WAITING_FOR_SUBTASK;
        delayTicks = humanizerService.getRandomDelay(1, 2);
    }

    private void determineNextState() {
        // After completing a subtask, determine what to do next
        int requiredTool = getRequiredToolId();
        int requiredBait = getRequiredBaitId();
        
        if (gameService.isInventoryFull()) {
            if (config.fishingMode() == FishingMode.POWER_DROP) {
                currentState = FishingState.DROPPING_FISH;
                doDroppingFish();
            } else if (config.cookFish() && hasRawFish()) {
                currentState = FishingState.WALKING_TO_COOKING;
            } else {
                currentState = FishingState.WALKING_TO_BANK;
            }
        } else if (!(gameService.hasItem(requiredTool))) {
            currentState = FishingState.WITHDRAWING;
        } else if (requiredBait != -1 && !gameService.hasItem(requiredBait)){
            currentState = FishingState.WITHDRAWING;
        } else {
            currentState = FishingState.WALKING_TO_FISHING;
        }
    }

    private boolean hasRawFish() {
        return gameService.hasItem(RAW_SHRIMP_ID) || gameService.hasItem(RAW_ANCHOVIES_ID) ||
               gameService.hasItem(RAW_TROUT_ID) || gameService.hasItem(RAW_SALMON_ID);
    }

    private int getRawFishId() {
        if (gameService.hasItem(RAW_SHRIMP_ID)) {
            return RAW_SHRIMP_ID;
        } else if (gameService.hasItem(RAW_ANCHOVIES_ID)) {
            return RAW_ANCHOVIES_ID;
        } else if (gameService.hasItem(RAW_TROUT_ID)) {
            return RAW_TROUT_ID;
        } else if (gameService.hasItem(RAW_SALMON_ID)) {
            return RAW_SALMON_ID;
        }
        return -1;
    }

    private WorldPoint getFishingLocation() {
        switch (config.fishingArea()) {
            case LUMBRIDGE_SWAMP:
                return LUMBRIDGE_SWAMP_FISHING;
            case BARBARIAN_VILLAGE:
                return BARBARIAN_VILLAGE;
            default:
                return LUMBRIDGE_SWAMP_FISHING;
        }
    }

    private WorldPoint getCookingLocation() {
        switch (config.fishingArea()) {
            case LUMBRIDGE_SWAMP:
                return LUMBRIDGE_KITCHEN_RANGE;
            case BARBARIAN_VILLAGE:
                return BARBARIAN_VILLAGE; // Cook at the fire
            default:
                return LUMBRIDGE_KITCHEN_RANGE;
        }
    }

    private WorldPoint getBankLocation() {
        switch (config.fishingArea()) {
            case LUMBRIDGE_SWAMP:
                return Banks.LUMBRIDGE.getBankCoordinates();
            case BARBARIAN_VILLAGE:
                return Banks.VARROCK_WEST.getBankCoordinates(); // Use Lumbridge bank for now
            default:
                return Banks.LUMBRIDGE.getBankCoordinates();
        }
    }

    private int getFishingSpotId() {
        switch (config.fishingSpot()) {
            case NET:
                return NET_FISHING_SPOT_ID;
            case LURE:
                return ROD_FISHING_SPOT_ID;
            default:
                return NET_FISHING_SPOT_ID;
        }
    }

    private int getCookingRangeId() {
        switch (config.fishingArea()) {
            case LUMBRIDGE_SWAMP:
                return KITCHEN_RANGE_ID;
            case BARBARIAN_VILLAGE:
                return BARBARIAN_VILLAGE_FIRE_ID;
            default:
                return KITCHEN_RANGE_ID;
        }
    }

    private int getRequiredToolId() {
        switch (config.fishingSpot()) {
            case NET:
                return ItemID.NET;
            case LURE:
                return ItemID.FLY_FISHING_ROD;
            default:
                return ItemID.NET;
        }
    }

    private int getRequiredBaitId() {
        switch (config.fishingSpot()) {
            case LURE:
                return ItemID.FEATHER;
            default:
                return -1;
        }
    }
}