package com.runepal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ClientTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class GrindingTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;

    private final ScheduledExecutorService scheduler;

    private static final int PESTLE_AND_MORTAR_ID = 233;

    private int clientDelayTicks;
    private int pestleAndMortarSlot = -1;
    private boolean mortarSelected = false;
    private List<Integer> clickedSlots = new ArrayList<Integer>();
    private int clickCount = 0;
    private boolean isMouseMoving = false;

    // State management variables
    @Getter
    private GrindingTaskState currentState = GrindingTaskState.IDLE;
    private GrindingTaskState prevState;
    private boolean isStarted = false;
    private boolean isCurrentlyGrinding = false;

    // Event consumers
    private Consumer<ClientTick> clientTickHandler;
    private Consumer<MouseMovementCompletedEvent> mouseMovementCompletedHandler;

    private enum GrindingTaskState {
        IDLE,
        GRINDING,
        OPENING_BANK,
        DEPOSITING,
        WITHDRAWING
    }

    public GrindingTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager,
                        ActionService actionService, GameService gameService,
                        EventService eventService, HumanizerService humanizerService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");;
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager cannot be null");;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void onStart() {
        log.info("Starting Grinding Task");
        this.clientTickHandler = this::onClientTick;
        this.mouseMovementCompletedHandler = this::onMouseMovementCompleted;
        eventService.subscribe(ClientTick.class, clientTickHandler);
        eventService.subscribe(MouseMovementCompletedEvent.class, mouseMovementCompletedHandler);

        for (int i = 0; i < 28; i++) {
            int slotItem = gameService.getInventoryItemId(i);
            if (slotItem == PESTLE_AND_MORTAR_ID) {
                pestleAndMortarSlot = i;
            }
        }
        if (pestleAndMortarSlot < 0) {
            log.info("No pestle and mortar item found in inventory. Stopping.");
            plugin.stopBot();
            return;
        }

        actionService.sendMouseMoveRequest(gameService.getInventoryItemPoint(pestleAndMortarSlot));

        clientDelayTicks = 60;
        isStarted = true;
        currentState = GrindingTaskState.IDLE;
    }

    private void onMouseMovementCompleted(MouseMovementCompletedEvent mouseMovementCompletedEvent) {
        isMouseMoving = false;
    }

    @Override
    public void onLoop() {
    }

    private void onClientTick(ClientTick clientTick) {
        if (prevState != currentState) {
            log.info("Transitioning from {} to {}", prevState, currentState);
        }
        prevState = currentState;

        if (isMouseMoving) {
            return;
        }

        if (clientDelayTicks > 0) {
            clientDelayTicks--;
            return;
        }

        switch (currentState) {
            case IDLE:
                determineNextState();
                break;
            case GRINDING:
                doGrinding();
                break;
            case OPENING_BANK:
                doOpeningBank();
                break;
            case DEPOSITING:
                ItemContainer bankContainer = plugin.getClient().getItemContainer(InventoryID.BANK);
                if (bankContainer == null) {
                    log.warn("Bank container not found, trying again to open bank.");
                    actionService.sendClickRequest(new Point(0,0), true);
                    currentState = GrindingTaskState.OPENING_BANK;
                    return;
                }
                if (!gameService.hasItem(235)) {
                    currentState = GrindingTaskState.WITHDRAWING;
                    break;
                }
                doDepositing();
                break;
            case WITHDRAWING:
                if (gameService.hasItem(config.grindingItemId())) {
                    double gaussianValue = humanizerService.getGaussian(1, 0.7, 0);
                    double adjustedGaussian = gaussianValue * 100;
                    int gaussianInt = (int) Math.round(adjustedGaussian);

                    try {
                        actionService.sendKeyRequest("/key_hold", "esc");
                        scheduler.schedule(() -> {
                            actionService.sendKeyRequest("/key_release", "esc");
                        }, gaussianInt, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.warn("onMouseMovementCompleted: Error sending key press {}: {}", "esc", e.getMessage());
                    }
                    currentState = GrindingTaskState.IDLE;
                    clientDelayTicks = 30;
                    break;
                }
                doWithdrawing();
                break;
            default:
                log.error("Unknown state: {}", currentState);
                break;
        }
    }

    private void doWithdrawing() {
        ItemContainer bankContainer = plugin.getClient().getItemContainer(InventoryID.BANK);
        if (bankContainer == null) {
            log.warn("Bank container not found.");
            currentState = GrindingTaskState.IDLE;
            return;
        }
        int itemIndex = bankContainer.find(config.grindingItemId());
        if (itemIndex != -1) {
            actionService.sendClickRequest(gameService.getBankItemPoint(itemIndex), true);
            isMouseMoving = true;
            clientDelayTicks = 30;
        } else {
            log.warn("Item {} not found in bank", config.grindingItemId());
        }
    }

    private void doDepositing() {
        for (int i = 0; i < 28; i++) {
            ItemContainer inventory = plugin.getClient().getItemContainer(InventoryID.INV);
            if (inventory == null) {
                log.warn("Inventory not visible");
                return;
            }
            Item item = inventory.getItem(i);
            if (item == null) {
                log.warn("Item not in inventory");
                return;
            }
            int slotItem = item.getId();
            if (slotItem == 235) {
                log.info("Item found at slot {}", i);
                Widget inventoryWidget = plugin.getClient().getWidget(InterfaceID.Bankside.ITEMS);
                if (inventoryWidget == null || inventoryWidget.isHidden()) {
                    log.warn("Could not find inventory widget");
                    currentState = GrindingTaskState.IDLE;
                    return;
                }
                Widget itemWidget = inventoryWidget.getChild(i);
                if (itemWidget == null) {
                    log.warn("Could not find item widget");
                    currentState = GrindingTaskState.IDLE;
                    return;
                }
                Point clickPoint = gameService.getRandomPointInBounds(itemWidget.getBounds());
                actionService.sendClickRequest(clickPoint, true);
                isMouseMoving = true;
                clientDelayTicks = 30;
                return;
            }
        }
        log.warn("Item {} not found in inventory", 	"235");
    }

    private void doOpeningBank() {
        GameObject bankBooth = gameService.findNearestGameObject(10583);
        if (bankBooth != null) {
            log.info("Found bank booth. Clicking it.");
            actionService.interactWithGameObject(bankBooth, "Bank");
            currentState = GrindingTaskState.DEPOSITING;
            clientDelayTicks = 90;
        } else {
            log.warn("No bank booth found. Cannot proceed with banking.");
            currentState = GrindingTaskState.IDLE;
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Grinding Task");
        eventService.unsubscribe(ClientTick.class, clientTickHandler);
        eventService.unsubscribe(MouseMovementCompletedEvent.class, mouseMovementCompletedHandler);
        isStarted = false;
        currentState = GrindingTaskState.IDLE;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public String getTaskName() {
        return "Grinding Task";
    }

    private void doGrinding() {
        if (gameService.getInventoryItemId(26) != config.grindingItemId() || clickCount > 26) {
            log.info("Item {} not found, banking.", config.grindingItemId());
            isCurrentlyGrinding = false;
            clickCount = 0;
            currentState = GrindingTaskState.OPENING_BANK;
            return;
        }

        double gaussianValue = humanizerService.getGaussian(1, 0.7, 0);
        int clientTickDelay = (int) Math.round(gaussianValue) * 3;

        if (mortarSelected) {
            actionService.sendClickRequest(gameService.getInventoryItemPoint(26), true);
            isMouseMoving = true;
            mortarSelected = false;
            clickCount++;
        } else {
            actionService.sendClickRequest(gameService.getInventoryItemPoint(27), true);
            isMouseMoving = true;
            mortarSelected = true;
        }
        clientDelayTicks = clientTickDelay;

//        for (int i = 0; i < 28; i++) {
//            if (clickedSlots.contains(i)) {
//                continue;
//            }
//            int slotItem = gameService.getInventoryItemId(i);
//            if (slotItem == config.grindingItemId()) {
//                if (mortarSelected) {
//                    actionService.sendClickRequest(gameService.getInventoryItemPoint(i), true);
//                    isMouseMoving = true;
//                    mortarSelected = false;
//                    if (i == 27) {
//                        clickedSlots.clear();
//                        isCurrentlyGrinding = false;
//                        currentState = GrindingTaskState.OPENING_BANK;
//                    } else {
//                        clickedSlots.add(i);
//                    }
//                } else {
//                    actionService.sendClickRequest(gameService.getInventoryItemPoint(pestleAndMortarSlot), true);
//                    isMouseMoving = true;
//                    mortarSelected = true;
//                }
//                clientDelayTicks = clientTickDelay;
//                return;
//            }
//        }
    }

    private void determineNextState() {
        double gaussianValue = humanizerService.getGaussian(1, 0.7, 0);
        double adjustedGaussian = gaussianValue * 100;
        int gaussianInt = (int) Math.round(adjustedGaussian);

        try {
            actionService.sendKeyRequest("/key_hold", "esc");
            scheduler.schedule(() -> {
                actionService.sendKeyRequest("/key_release", "esc");
            }, gaussianInt, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Error sending key press {}: {}", "esc", e.getMessage());
        }

        if (!gameService.hasItem(config.grindingItemId())) {
            currentState = GrindingTaskState.OPENING_BANK;
        }
        if (gameService.isInventoryFull()) {
            currentState = GrindingTaskState.OPENING_BANK;
        }
        if (!isCurrentlyGrinding) {
            isCurrentlyGrinding = true;
            currentState = GrindingTaskState.GRINDING;
        }
    }
}
