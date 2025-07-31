package com.runepal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;

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
    private int itemIndex = -1;

    // State management variables
    @Getter
    private GrindingTaskState currentState = GrindingTaskState.IDLE;
    private GrindingTaskState prevState;
    private boolean isStarted = false;
    private boolean isCurrentlyGrinding = false;

    // Event consumers
    private Consumer<ClientTick> clientTickHandler;

    private enum GrindingTaskState {
        IDLE,
        GRINDING,
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
        eventService.subscribe(ClientTick.class, clientTickHandler);

        // Find the item's inventory location here so we don't have to check constantly
        for (int i = 0; i < 28; i++) {
            int itemId = gameService.getInventoryItemId(i);
            if (itemId == config.grindingItemId()) {
                itemIndex = i;
            }
        }
        if (itemIndex == -1) {
            log.error("Requested item not found. Stopping.");
            plugin.stopBot();
        }

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

        isStarted = true;
        currentState = GrindingTaskState.IDLE;
    }

    @Override
    public void onLoop() {
    }

    private void onClientTick(ClientTick clientTick) {
        if (prevState != currentState) {
            log.info("Transitioning from {} to {}", prevState, currentState);
        }
        prevState = currentState;

        if (clientDelayTicks > 0) {
            clientDelayTicks--;
            return;
        }

        switch (currentState) {
            case IDLE:
                determineNextState();
                break;
            case GRINDING:
                powerGrind(config.grindingItemId());
                break;
            default:
                log.error("Unknown state: {}", currentState);
                break;
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Grinding Task");
        eventService.unsubscribe(ClientTick.class, clientTickHandler);
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

    private void determineNextState() {
        if (gameService.hasItem(config.grindingItemId())) {
            plugin.stopBot();
        }
        if (!isCurrentlyGrinding) {
            currentState = GrindingTaskState.GRINDING;
        }
    }

    private void powerGrind(int itemId) {
        if (!gameService.hasItem(itemId)) {
            isCurrentlyGrinding = false;
            currentState = GrindingTaskState.IDLE;
            return;
        }
        if (isCurrentlyGrinding) return;

        isCurrentlyGrinding = true;
        log.info("Starting to grind items.");

        int pestleAndMortarSlot = gameService.getInventoryItemIndex(PESTLE_AND_MORTAR_ID);
        for (int i = 0; i < 28; i++) {
            if (i == pestleAndMortarSlot) continue;
            int itemInSlot = gameService.getInventoryItemId(i);
            if (itemId == itemInSlot) {
                // Delay for 1 client tick, sometimes longer
                double gaussianValue = humanizerService.getGaussian(1, 1, 0);
                int gaussianInt = (int) Math.round(gaussianValue);
                log.info("Delaying pestle click for {} client ticks", gaussianInt);
                clientDelayTicks = gaussianInt;
                actionService.sendClickRequest(gameService.getInventoryItemPoint(pestleAndMortarSlot), true);
                gaussianValue = humanizerService.getGaussian(1, 1, 0);
                gaussianInt = (int) Math.round(gaussianValue);
                log.info("Delaying item click for {} client ticks", gaussianInt);
                clientDelayTicks = gaussianInt;
                actionService.sendClickRequest(gameService.getInventoryItemPoint(i), true);
            }
        }
    }
}
