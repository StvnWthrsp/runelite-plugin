package com.runepal;

import com.runepal.entity.Interactable;
import com.runepal.entity.NpcEntity;
import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.InteractingChanged;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

@Slf4j
public class GemstoneCrabTask implements BotTask {

    private static final int GEMSTONE_CRAB_ID = 14779;
    private static final int CAVE_ID = 57631;

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
    private GemstoneCrabState currentState = GemstoneCrabState.IDLE;
    private GemstoneCrabState prevState;
    private boolean isStarted = false;

    // Event consumers
    private Consumer<InteractingChanged> interactingHandler;
    private Consumer<InteractionCompletedEvent> interactionCompletedHandler;

    private enum GemstoneCrabState {
        IDLE,
        COMBAT_ACTIVE,
        WAITING_FOR_SUBTASK,
        ENTERING_CAVE,
        CLICKING_CRAB,
        WAIT_FOR_CAVE_TRANSPORT
    }

    public GemstoneCrabTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager,
                            ActionService actionService, GameService gameService,
                            EventService eventService, HumanizerService humanizerService) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Gemstone Crab Task");
        this.interactingHandler = this::onInteractingChanged;
        this.interactionCompletedHandler = this::onInteractionCompletedEvent;
        eventService.subscribe(InteractingChanged.class, interactingHandler);
        eventService.subscribe(InteractionCompletedEvent.class, interactionCompletedHandler);
        isStarted = true;
    }

    private void onInteractingChanged(InteractingChanged interactingChanged) {
        if (delayTicks > 0) return;

        if (currentState == GemstoneCrabState.COMBAT_ACTIVE) {
            if (!isInCombat()) {
                log.info("Combat ended, entering cave after a delay");
                currentState = GemstoneCrabState.ENTERING_CAVE;
                delayTicks = humanizerService.getCustomDelay(20,  10, 10);
            }
        }
    }

    private void onInteractionCompletedEvent(InteractionCompletedEvent interactionCompletedEvent) {

    }

    @Override
    public void onLoop() {
        if (prevState != currentState) {
            log.info("Transitioning from {} to {}", prevState, currentState);
        }
        prevState = currentState;

        // Handle subtask execution
        if (taskManager.getCurrentTask() != this) {
            if (currentState != GemstoneCrabState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing {}.", getTaskName());
                currentState = GemstoneCrabState.WAITING_FOR_SUBTASK;
            }
            return;
        } else {
            if (currentState == GemstoneCrabState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task finished. Resuming {}.", getTaskName());
                currentState = GemstoneCrabState.IDLE;
            }
        }

        if (delayTicks > 0) {
            log.debug("Delay remaining: {}", delayTicks);
            delayTicks--;
            return;
        }

        switch (currentState) {
            case IDLE:
                currentState = determineNextState();
                break;
            case COMBAT_ACTIVE: {
                doCombatActive();
                break;
            }
            case ENTERING_CAVE:
                doEnteringCave();
                break;
            case CLICKING_CRAB:
                doClickingCrab();
                break;
            case WAIT_FOR_CAVE_TRANSPORT:
                doWaitForCaveTransport();
                break;
            default: {
                log.warn("Unhandled state ({})", currentState);
                break;
            }
        }
    }

    private void doWaitForCaveTransport() {
        idleTicks++;
        if (idleTicks >= 20) {
            log.warn("Idled for 20 ticks while waiting for cave transport, transitioning to IDLE to get new state");
            currentState = GemstoneCrabState.IDLE;
            return;
        }
        NPC crabNpc = gameService.findNearestNpc(GEMSTONE_CRAB_ID);
        if (crabNpc != null) {
            delayTicks = humanizerService.getMediumDelay();
            idleTicks = 0;
            currentState = GemstoneCrabState.CLICKING_CRAB;
        }
    }

    private void doClickingCrab() {
        Interactable selectedEntity = gameService.findNearest(interactable -> {
            if (!(interactable instanceof NpcEntity)) {
                return false;
            }
            NpcEntity npcEntity = (NpcEntity) interactable;
            return npcEntity.getId() == GEMSTONE_CRAB_ID;
        });
        if (selectedEntity == null) {
            log.warn("Crab not detected, switching to IDLE to get new state");
            currentState = GemstoneCrabState.IDLE;
            return;
        }
        actionService.interactWithEntity(selectedEntity, "Attack");
        delayTicks = 5;
        idleTicks = 0;
        currentState = GemstoneCrabState.COMBAT_ACTIVE;
    }

    private void doCombatActive() {
        if (isInCombat()) {
            return;
        }
        idleTicks++;
        if (idleTicks >= 10) {
            log.warn("Idled for 10 ticks when we should be in combat, transitioning to IDLE to get new state");
            currentState = GemstoneCrabState.IDLE;
        }
    }

    private void doEnteringCave() {
        GameObject caveObject = gameService.findNearestGameObject(CAVE_ID);
        actionService.interactWithGameObject(caveObject, "Crawl-through");
        idleTicks = 0;
        currentState = GemstoneCrabState.WAIT_FOR_CAVE_TRANSPORT;
    }

    @Override
    public void onStop() {
        log.info("Stopping Gemstone Crab Task");
        eventService.unsubscribe(InteractingChanged.class, interactingHandler);
        eventService.unsubscribe(InteractionCompletedEvent.class, interactionCompletedHandler);
        interactingHandler = null;
        interactionCompletedHandler = null;
        isStarted = false;
    }

    @Override
    public boolean isFinished() {
        // Runs until stopped
        return false;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public String getTaskName() {
        return "Gemstone Crab Task";
    }

    private boolean isInCombat() {
        Player localPlayer = plugin.getClient().getLocalPlayer();
        Actor interactingActor = localPlayer.getInteracting();
        if (!(interactingActor instanceof NPC)) {
            return false;
        }
        NPC npc = (NPC) interactingActor;
        return npc.getId() == GEMSTONE_CRAB_ID;
    }

    private GemstoneCrabState determineNextState() {
        if (isInCombat()) {
            log.info("Combat active");
            return currentState = GemstoneCrabState.COMBAT_ACTIVE;
        }
        NPC crab = gameService.findNearestNpc(GEMSTONE_CRAB_ID);
        if (crab != null) {
            log.info("Clicking crab");
            return currentState = GemstoneCrabState.CLICKING_CRAB;
        }
        log.info("Entering cave");
        return currentState = GemstoneCrabState.ENTERING_CAVE;
    }

}
