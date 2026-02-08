package com.runepal;

import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractGatheringTask implements BotTask {
    private enum GatheringState {
        FINDING_TARGET,
        GATHERING,
        WAIT_GATHERING,
        HOVER_NEXT_TARGET,
        CHECK_INVENTORY,
        DROPPING,
        WAITING_FOR_SUBTASK
    }

    protected final RunepalPlugin plugin;
    protected final BotConfig config;
    protected final TaskManager taskManager;
    protected final PathfinderConfig pathfinderConfig;
    protected final ActionService actionService;
    protected final GameService gameService;
    protected final EventService eventService;
    protected final HumanizerService humanizerService;

    private Consumer<AnimationChanged> animationHandler;
    private Consumer<StatChanged> statHandler;
    private Consumer<InteractingChanged> interactingHandler;
    private Consumer<GameTick> gameTickHandler;

    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private GatheringState currentState;
    private int idleTicks;
    private int delayTicks;
    private GameObject targetObject;
    private GameObject nextObject;
    private long lastSkillXp;
    private long xpGainedThisGather;
    private boolean gatheringStarted;

    protected AbstractGatheringTask(RunepalPlugin plugin,
                                    BotConfig config,
                                    TaskManager taskManager,
                                    PathfinderConfig pathfinderConfig,
                                    ActionService actionService,
                                    GameService gameService,
                                    EventService eventService,
                                    HumanizerService humanizerService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager cannot be null");
        this.pathfinderConfig = Objects.requireNonNull(pathfinderConfig, "pathfinderConfig cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting {} Task.", getTaskName());
        this.lastSkillXp = plugin.getClient().getSkillExperience(getTrackedSkill());

        this.animationHandler = this::onAnimationChanged;
        this.statHandler = this::onStatChanged;
        this.interactingHandler = this::onInteractingChanged;
        this.gameTickHandler = this::onGameTick;

        eventService.subscribe(AnimationChanged.class, animationHandler);
        eventService.subscribe(StatChanged.class, statHandler);
        eventService.subscribe(InteractingChanged.class, interactingHandler);
        eventService.subscribe(GameTick.class, gameTickHandler);

        WorldPoint initialDestination = getInitialDestination();
        if (initialDestination != null && gameService.getPlayerLocation().distanceTo(initialDestination) > 10) {
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, initialDestination, actionService, gameService, humanizerService));
            this.currentState = GatheringState.WAITING_FOR_SUBTASK;
            return;
        }

        this.currentState = GatheringState.FINDING_TARGET;
    }

    @Override
    public void onStop() {
        log.info("Stopping {} Task.", getTaskName());
        this.targetObject = null;
        this.nextObject = null;
        setTargetOverlay(null);

        eventService.unsubscribe(AnimationChanged.class, animationHandler);
        eventService.unsubscribe(StatChanged.class, statHandler);
        eventService.unsubscribe(InteractingChanged.class, interactingHandler);
        eventService.unsubscribe(GameTick.class, gameTickHandler);

        this.animationHandler = null;
        this.statHandler = null;
        this.interactingHandler = null;
        this.gameTickHandler = null;
    }

    @Override
    public boolean isFinished() {
        return false;
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    @Override
    public void onLoop() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (taskManager.getCurrentTask() != this) {
            if (currentState != GatheringState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing {}.", getTaskName());
                currentState = GatheringState.WAITING_FOR_SUBTASK;
            }
            return;
        }

        if (currentState == GatheringState.WAITING_FOR_SUBTASK) {
            log.info("Sub-task finished. Resuming {}.", getTaskName());
            currentState = GatheringState.FINDING_TARGET;
        }

        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        switch (currentState) {
            case FINDING_TARGET:
                doFindingTarget();
                break;
            case GATHERING:
                doGathering();
                break;
            case WAIT_GATHERING:
                doWaitGathering();
                break;
            case HOVER_NEXT_TARGET:
                doHoverNextTarget();
                break;
            case CHECK_INVENTORY:
                doCheckInventory();
                break;
            case DROPPING:
                if (!actionService.isDropping()) {
                    log.info("Dropping complete. Resuming {}.", getTaskName().toLowerCase());
                    currentState = GatheringState.FINDING_TARGET;
                }
                break;
            case WAITING_FOR_SUBTASK:
                break;
            default:
                break;
        }

        plugin.setCurrentState(getTaskName().toUpperCase() + ": " + currentState);
    }

    private void doFindingTarget() {
        if (gameService.isInventoryFull()) {
            currentState = GatheringState.CHECK_INVENTORY;
            return;
        }

        if (nextObject != null && isTargetStillValid(nextObject)) {
            targetObject = nextObject;
            nextObject = null;
        } else {
            int[] objectIds = getTargetObjectIds();
            targetObject = gameService.findNearestGameObject(objectIds);
            nextObject = null;
        }

        setTargetOverlay(targetObject);

        if (targetObject != null) {
            currentState = GatheringState.GATHERING;
            doGathering();
            return;
        }

        log.warn("No {} found. Configured IDs: {}", getTargetLabelPlural(), Arrays.toString(getTargetObjectIds()));
        delayTicks = humanizerService.getRandomDelay(1, 3);
    }

    private void doGathering() {
        if (targetObject == null) {
            currentState = GatheringState.FINDING_TARGET;
            return;
        }

        actionService.interactWithGameObject(targetObject, getInteractAction());
        gatheringStarted = false;
        xpGainedThisGather = 0;
        idleTicks = 0;
        lastSkillXp = plugin.getClient().getSkillExperience(getTrackedSkill());
        currentState = GatheringState.WAIT_GATHERING;
    }

    private void doWaitGathering() {
        if (isGatheringAnimationActive()) {
            gatheringStarted = true;
            idleTicks = 0;
            if (nextObject == null) {
                currentState = GatheringState.HOVER_NEXT_TARGET;
                return;
            }
        } else {
            idleTicks++;
        }

        if (idleTicks > 5) {
            log.warn("{} seems to have failed or {} depleted.", getTaskName(), getTargetLabel());
            finishGathering();
        }
    }

    private void finishGathering() {
        log.info("Finished {}. XP gained: {}", getTargetLabel(), xpGainedThisGather);
        targetObject = null;
        setTargetOverlay(null);
        gatheringStarted = false;
        currentState = GatheringState.CHECK_INVENTORY;
        doCheckInventory();
    }

    private void doCheckInventory() {
        if (!gameService.isInventoryFull()) {
            currentState = GatheringState.FINDING_TARGET;
            return;
        }

        if (shouldBankWhenInventoryFull()) {
            log.info("Inventory full. Banking.");
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, gameService.getPlayerLocation(), actionService, gameService, humanizerService));
            taskManager.pushTask(new BankTask(plugin, actionService, gameService, eventService));
            WorldPoint bankCoordinates = plugin.getBankCoordinates();
            log.info("Banking to: {}", bankCoordinates);
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, bankCoordinates, actionService, gameService, humanizerService));
            currentState = GatheringState.WAITING_FOR_SUBTASK;
            return;
        }

        delayTicks = humanizerService.getRandomDelay(1, 3);
        doDropping();
    }

    private void doDropping() {
        currentState = GatheringState.DROPPING;
        int[] dropItemIds = getDropItemIds();
        if (dropItemIds.length == 0) {
            log.warn("No {} ids found to drop. Stopping bot.", getDropItemLabel());
            plugin.stopBot();
            return;
        }

        actionService.powerDrop(dropItemIds);
    }

    private void doHoverNextTarget() {
        if (nextObject == null) {
            nextObject = findNextBestTarget();
        }

        if (nextObject != null) {
            actionService.sendMouseMoveRequest(gameService.getRandomClickablePoint(nextObject));
            log.debug("Pre-targeting next {} at {}", getTargetLabel(), nextObject.getWorldLocation());
        }

        currentState = GatheringState.WAIT_GATHERING;
    }

    private GameObject findNextBestTarget() {
        int[] objectIds = getTargetObjectIds();
        if (objectIds.length == 0) {
            return null;
        }

        WorldPoint playerLocation = gameService.getPlayerLocation();
        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();

        List<GameObject> availableTargets = new ArrayList<>();
        List<GameObject> adjacentTargets = new ArrayList<>();

        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) {
                    continue;
                }

                for (GameObject gameObject : tile.getGameObjects()) {
                    if (gameObject == null || gameObject == targetObject) {
                        continue;
                    }

                    if (!containsId(objectIds, gameObject.getId())) {
                        continue;
                    }

                    availableTargets.add(gameObject);

                    WorldPoint targetLocation = gameObject.getWorldLocation();
                    int dx = Math.abs(targetLocation.getX() - playerLocation.getX());
                    int dy = Math.abs(targetLocation.getY() - playerLocation.getY());
                    if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
                        adjacentTargets.add(gameObject);
                    }
                }
            }
        }

        if (!adjacentTargets.isEmpty()) {
            return adjacentTargets.stream()
                    .min(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerLocation)))
                    .orElse(null);
        }

        if (availableTargets.size() >= 2) {
            List<GameObject> sortedTargets = availableTargets.stream()
                    .sorted(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerLocation)))
                    .collect(Collectors.toList());
            return sortedTargets.get(1);
        }

        if (!availableTargets.isEmpty()) {
            return availableTargets.get(0);
        }

        return null;
    }

    private boolean isTargetStillValid(GameObject object) {
        if (object == null) {
            return false;
        }

        int[] objectIds = getTargetObjectIds();
        if (objectIds.length == 0 || !containsId(objectIds, object.getId())) {
            return false;
        }

        WorldPoint objectLocation = object.getWorldLocation();
        WorldPoint playerLocation = gameService.getPlayerLocation();
        if (objectLocation.distanceTo(playerLocation) > 20) {
            return false;
        }

        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();
        int baseX = plugin.getClient().getWorldView(-1).getBaseX();
        int baseY = plugin.getClient().getWorldView(-1).getBaseY();
        int sceneX = objectLocation.getX() - baseX;
        int sceneY = objectLocation.getY() - baseY;

        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE) {
            return false;
        }

        Tile tile = tiles[z][sceneX][sceneY];
        if (tile == null) {
            return false;
        }

        for (GameObject gameObject : tile.getGameObjects()) {
            if (gameObject != null
                    && gameObject.getId() == object.getId()
                    && gameObject.getWorldLocation().equals(objectLocation)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsId(int[] ids, int id) {
        for (int value : ids) {
            if (value == id) {
                return true;
            }
        }
        return false;
    }

    private void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (currentState != GatheringState.WAIT_GATHERING) {
            return;
        }
        int newAnimation = plugin.getClient().getLocalPlayer().getAnimation();
        if (gameService.isCurrentAnimation(newAnimation)) {
            log.debug("{} animation started: {}", getTaskName(), newAnimation);
        }
    }

    private void onStatChanged(StatChanged statChanged) {
        if (statChanged.getSkill() != getTrackedSkill()) {
            return;
        }

        long currentXp = statChanged.getXp();
        if (currentState == GatheringState.WAIT_GATHERING && gatheringStarted && currentXp > lastSkillXp) {
            long xpGained = currentXp - lastSkillXp;
            xpGainedThisGather += xpGained;
            lastSkillXp = currentXp;
            actionQueue.add(this::finishGathering);
            return;
        }

        lastSkillXp = currentXp;
    }

    private void onInteractingChanged(InteractingChanged interactingChanged) {
        if (interactingChanged.getSource() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (currentState == GatheringState.GATHERING && interactingChanged.getTarget() != null) {
            log.debug("{} interacting with {}", getTaskName(), interactingChanged.getTarget().getName());
        }
    }

    private void onGameTick(GameTick gameTick) {
        // no-op for now
    }

    protected abstract Skill getTrackedSkill();

    protected abstract boolean isGatheringAnimationActive();

    protected abstract int[] getTargetObjectIds();

    protected abstract int[] getDropItemIds();

    protected abstract String getInteractAction();

    protected abstract String getTargetLabel();

    protected abstract String getTargetLabelPlural();

    protected abstract String getDropItemLabel();

    protected abstract void setTargetOverlay(GameObject gameObject);

    protected abstract boolean shouldBankWhenInventoryFull();

    protected WorldPoint getInitialDestination() {
        return null;
    }
}
