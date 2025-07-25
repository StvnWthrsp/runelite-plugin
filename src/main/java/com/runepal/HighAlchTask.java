package com.runepal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public class HighAlchTask implements BotTask {

    private static final int ITEM_ID = ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED;

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;

    private int delayTicks = 0;
    private int idleTicks = 0;
    private int itemIndex = 0;

    // State management variables
    @Getter
    private HighAlchState currentState = HighAlchState.IDLE;
    private HighAlchState prevState;
    private boolean isStarted = false;

    // Event consumers
    private Consumer<AnimationChanged> animationChangedHandler;

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
        this.animationChangedHandler = this::onAnimationChanged;
        eventService.subscribe(AnimationChanged.class, animationChangedHandler);

        // Find the item's inventory location here so we don't have to check constantly
        for (int i = 0; i < 28; i++) {
            int itemId = gameService.getInventoryItemId(i);
            if (itemId == ITEM_ID) {
                itemIndex = i;
            }
        }

        actionService.openMagicInterface();
        isStarted = true;
    }

    private void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        int newAnimation = plugin.getClient().getLocalPlayer().getAnimation();
        log.trace("New animation ID: {}", newAnimation);
        if (gameService.isCurrentAnimation(AnimationID.HUMAN_CASTHIGHLVLALCHEMY)) {
            log.debug("High alchemy animation started.");
            return;
        }
        if (currentState == HighAlchState.WAITING_FOR_CAST) {
            log.debug("High alchemy animation ended.");
            currentState = HighAlchState.STARTING_ALCH;
        }
    }

    @Override
    public void onLoop() {
        if (prevState != currentState) {
            log.info("Transitioning from {} to {}", prevState, currentState);
        }
        prevState = currentState;

        if (delayTicks > 0) {
            delayTicks--;
            return;
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
        log.info("Waiting for cast");
    }

    private void doStartingAlch() {
        log.info("Starting alch");
        actionService.castSpell("high level alchemy");
        currentState = HighAlchState.CLICKING_ALCH_ITEM;
    }

    private void doClickingAlchItem() {
        log.info("Clicking alch item");
        if (gameService.getInventoryItemPoint(itemIndex).getX() < 0 || gameService.getInventoryItemPoint(itemIndex).getY() < 0) {
            return;
        }
        actionService.sendClickRequest(gameService.getInventoryItemPoint(itemIndex), true);
        currentState = HighAlchState.WAITING_FOR_CAST;
    }

    @Override
    public void onStop() {
        log.info("Stopping High Alch Task");
        eventService.unsubscribe(AnimationChanged.class, animationChangedHandler);
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
