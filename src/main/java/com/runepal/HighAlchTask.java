package com.runepal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.gameval.AnimationID;

import java.util.Objects;
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

    private int delayTicks = 0;
    private int idleTicks = 0;

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
        STARTING_ALCH
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
        actionService.openMagicInterface();
        isStarted = true;
    }

    private void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (gameService.isCurrentAnimation(AnimationID.HIGHLVLALCHEMY)) {
            log.info("High alchemy animation started.");
            return;
        }
        log.info("High alchemy animation ended.");
        currentState = HighAlchState.STARTING_ALCH;
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
        currentState = HighAlchState.WAITING_FOR_CAST;
    }

    @Override
    public void onStop() {
        log.info("Stopping High Alch Task");
        eventService.unsubscribe(AnimationChanged.class, animationChangedHandler);
        isStarted = false;
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
