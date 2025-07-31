package com.runepal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class HighAlchTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;

    private int clientDelayTicks;
    private int itemIndex = -1;

    // State management variables
    @Getter
    private HighAlchState currentState = HighAlchState.IDLE;
    private HighAlchState prevState;
    private boolean isStarted = false;

    // Event consumers
    private Consumer<ClientTick> clientTickHandler;

    private enum HighAlchState {
        IDLE,
        WAITING_FOR_CAST,
        STARTING_ALCH,
        CLICKING_ALCH_ITEM
    }

    public HighAlchTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager,
                            ActionService actionService, GameService gameService,
                            EventService eventService, HumanizerService humanizerService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");;
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager cannot be null");;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting High Alch Task");
        this.clientTickHandler = this::onClientTick;
        eventService.subscribe(ClientTick.class, clientTickHandler);

        // Find the item's inventory location here so we don't have to check constantly
        for (int i = 0; i < 28; i++) {
            int itemId = gameService.getInventoryItemId(i);
            if (itemId == config.highAlchItemId()) {
                itemIndex = i;
            }
        }
        if (itemIndex == -1) {
            log.error("Requested item not found. Stopping.");
            plugin.stopBot();
        }

        actionService.openMagicInterface();
        // Wait for a little over a tick to ensure the spellbook has time to open
        clientDelayTicks = 40;
        isStarted = true;
        currentState = HighAlchState.IDLE;
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

        // We need to look for the item again if it moves, if the config changes, or we run out of items to alch
        if (gameService.getInventoryItemId(itemIndex) != config.highAlchItemId()) {
            for (int i = 0; i < 28; i++) {
                int itemId = gameService.getInventoryItemId(i);
                if (itemId == config.highAlchItemId()) {
                    itemIndex = i;
                }
            }
            if (itemIndex == -1) {
                log.error("Could not find item to continue alching. Stopping.");
                plugin.stopBot();
            }
        }

        switch (currentState) {
            case IDLE:
                determineNextState();
                break;
            case WAITING_FOR_CAST:
                doWaitingForCast();
                break;
            case STARTING_ALCH:
                doStartingAlch();
                break;
            case CLICKING_ALCH_ITEM:
                doClickingAlchItem();
                break;
            default:
                log.error("Unknown state: {}", currentState);
                break;
        }
    }

    private void doWaitingForCast() {
        Widget highLevelAlchemySpell = plugin.getClient().getWidget(InterfaceID.MagicSpellbook.HIGH_ALCHEMY);
        if (highLevelAlchemySpell != null && !highLevelAlchemySpell.isHidden()) {
            currentState = HighAlchState.STARTING_ALCH;

            // Get a gaussian distribution value
            double gaussianValue = humanizerService.getGaussian(1, 0.7, 0);
            log.info("onAnimationChanged: Got gaussian value = {}", gaussianValue);
            // Adjust gaussian to represent a number of client ticks
            double adjustedGaussian = gaussianValue * 20;
            log.info("onAnimationChanged: Adjusted gaussian value = {}", adjustedGaussian);
            // Round the value to get the integer value
            int gaussianInt = (int) Math.round(adjustedGaussian);
            double gameTickVal = gaussianInt / 20.0;
            log.info("Delaying spell cast for {} client ticks, {} game ticks", gaussianInt, gameTickVal);
            clientDelayTicks = gaussianInt;
        }
    }

    private void doStartingAlch() {
        log.info("Starting alch");
        actionService.castSpell("high level alchemy");
        currentState = HighAlchState.CLICKING_ALCH_ITEM;

        // Get a gaussian distribution value
        double gaussianValue = humanizerService.getGaussian(1, 0.7, 0);
        log.info("doStartingAlch: Got gaussian value = {}", gaussianValue);
        // Adjust gaussian to represent a number of client ticks
        double adjustedGaussian = gaussianValue * 40;
        log.info("doStartingAlch: Adjusted gaussian value = {}", adjustedGaussian);
        // Round the value to get the integer value
        int gaussianInt = (int) Math.round(adjustedGaussian);
        double gameTickVal = gaussianInt / 20.0;
        log.info("Delaying item click for {} client ticks, {} game ticks", gaussianInt, gameTickVal);
        clientDelayTicks = gaussianInt;
    }

    private void doClickingAlchItem() {
        log.trace("Clicking alch item");
        if (gameService.getInventoryItemPoint(itemIndex).getX() < 0 || gameService.getInventoryItemPoint(itemIndex).getY() < 0) {
            return;
        }
        Widget inventoryWidget = plugin.getClient().getWidget(InterfaceID.Inventory.ITEMS);
        if (inventoryWidget == null) return;
        Widget itemWidget = inventoryWidget.getChild(itemIndex);
        if (itemWidget == null) return;
        if (itemWidget.getBounds().contains(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY())) {
            actionService.sendClickRequest(null, false);
        } else {
            actionService.sendClickRequest(gameService.getInventoryItemPoint(itemIndex), true);
        }
        currentState = HighAlchState.WAITING_FOR_CAST;
    }

    @Override
    public void onStop() {
        log.info("Stopping High Alch Task");
        eventService.unsubscribe(ClientTick.class, clientTickHandler);
        isStarted = false;
        currentState = HighAlchState.IDLE;
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
        return "High Alch Task";
    }

    private void determineNextState() {
        if (gameService.isCurrentAnimation(AnimationID.HIGHLVLALCHEMY)) {
            currentState = HighAlchState.WAITING_FOR_CAST;
            return;
        };
        currentState = HighAlchState.STARTING_ALCH;
    }
}
