package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.function.Consumer;

@Slf4j
public class MiningTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    
    // Event handler references to maintain identity
    private Consumer<AnimationChanged> animationHandler;
    private Consumer<StatChanged> statHandler;
    private Consumer<InteractingChanged> interactingHandler;
    private Consumer<GameTick> gameTickHandler;
    
    // Internal state for this task only
    private enum MiningState {
        IDLE,
        FINDING_ROCK,
        MINING,
        WAIT_MINING,
        HOVER_NEXT_ROCK,
        CHECK_INVENTORY,
        DROPPING,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_MINE,
        WAITING_FOR_SUBTASK
    }

    private static final WorldPoint VARROCK_EAST_MINE = new WorldPoint(3285, 3365, 0);
    private static final WorldPoint VARROCK_EAST_BANK = new WorldPoint(3253, 3420, 0);

    private MiningState currentState = null;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int idleTicks = 0;
    private int delayTicks = 0;
    private GameObject targetRock = null;
    private GameObject nextRock = null;
    private volatile boolean droppingFinished = false;

    // Mining completion detection variables
    private long lastMiningXp = 0;
    private long xpGainedThisMine = 0;
    private boolean miningStarted = false;

    public MiningTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, PathfinderConfig pathfinderConfig, ActionService actionService, GameService gameService, EventService eventService, HumanizerService humanizerService) {
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
        log.info("Starting Mining Task.");
        this.lastMiningXp = plugin.getClient().getSkillExperience(Skill.MINING);
        
        // Store event handler references to maintain identity
        this.animationHandler = this::onAnimationChanged;
        this.statHandler = this::onStatChanged;
        this.interactingHandler = this::onInteractingChanged;
        this.gameTickHandler = this::onGameTick;
        
        this.eventService.subscribe(AnimationChanged.class, animationHandler);
        this.eventService.subscribe(StatChanged.class, statHandler);
        this.eventService.subscribe(InteractingChanged.class, interactingHandler);
        this.eventService.subscribe(GameTick.class, gameTickHandler);

        if( gameService.getPlayerLocation().distanceTo(VARROCK_EAST_MINE) > 10 ) {
            taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, VARROCK_EAST_MINE, actionService, gameService, humanizerService));
            this.currentState = MiningState.WAITING_FOR_SUBTASK;
            return;
        }
        this.currentState = MiningState.FINDING_ROCK;
    }

    @Override
    public void onStop() {
        log.info("Stopping Mining Task.");
        this.targetRock = null;
        this.nextRock = null;
        plugin.setTargetRock(null); // Clear overlay
        
        // Unsubscribe using the stored handler references
        this.eventService.unsubscribe(AnimationChanged.class, animationHandler);
        this.eventService.unsubscribe(StatChanged.class, statHandler);
        this.eventService.unsubscribe(InteractingChanged.class, interactingHandler);
        this.eventService.unsubscribe(GameTick.class, gameTickHandler);
        
        // Clear handler references
        this.animationHandler = null;
        this.statHandler = null;
        this.interactingHandler = null;
        this.gameTickHandler = null;
    }

    @Override
    public boolean isFinished() {
        // This task runs indefinitely until stopped by the user via the TaskManager.
        return false;
    }

    @Override
    public boolean isStarted() {
        if (currentState == null) {
            return false;
        }
        return true;
    }

    @Override
    public String getTaskName() {
        return "Mining";
    }

    @Override
    public void onLoop() {
        if (droppingFinished) {
            droppingFinished = false;
            currentState = MiningState.FINDING_ROCK;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (taskManager.getCurrentTask() != this) {
            if (currentState != MiningState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing MiningTask.");
                currentState = MiningState.WAITING_FOR_SUBTASK;
            }
            return;
        } else {
            if (currentState == MiningState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task finished. Resuming MiningTask.");
                currentState = MiningState.FINDING_ROCK; // Or whatever is appropriate
            }
        }

        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        switch (currentState) {
            case FINDING_ROCK:
                doFindingRock();
                break;
            case MINING:
                doMining();
                break;
            case WAIT_MINING:
                doWaitMining();
                break;
            case HOVER_NEXT_ROCK:
                doHoverNextRock();
                break;
            case CHECK_INVENTORY:
                doCheckInventory();
                break;
            case DROPPING:
                if (!actionService.isDropping()) {
                    log.info("Dropping complete. Resuming mining.");
                    currentState = MiningState.FINDING_ROCK;
                }
                break;
            case IDLE:
                currentState = MiningState.FINDING_ROCK;
                break;
            case WAITING_FOR_SUBTASK:
                // Handled above, do nothing here
                break;
            default:
                break;
        }
        plugin.setCurrentState(currentState.toString());
    }

    // Event handlers, called by the main plugin class
    public void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (currentState != MiningState.WAIT_MINING) {
            return;
        }
        int newAnimation = plugin.getClient().getLocalPlayer().getAnimation();
        if (gameService.isCurrentAnimation(newAnimation)) {
            log.info("Mining animation started. Animation: {}", newAnimation);
        }
    }

    public void onStatChanged(StatChanged statChanged) {
        if (statChanged.getSkill() != Skill.MINING) {
            return;
        }
        long currentXp = statChanged.getXp();
        if (currentState == MiningState.WAIT_MINING && miningStarted) {
            if (currentXp > lastMiningXp) {
                long xpGained = currentXp - lastMiningXp;
                xpGainedThisMine += xpGained;
                lastMiningXp = currentXp;
                log.info("Gained {} mining XP (total this mine: {})", xpGained, xpGainedThisMine);
                actionQueue.add(this::finishMining);
            }
        } else {
            lastMiningXp = currentXp;
        }
    }

    public void onInteractingChanged(InteractingChanged interactingChanged) {
        if (interactingChanged.getSource() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (currentState == MiningState.MINING) {
            log.info("Interacting changed. Mining: {}", interactingChanged.getTarget().getName());
        }
    }

    public void onGameTick(GameTick gameTick) {
    }

    // --- FSM LOGIC ---

    private void doFindingRock() {
        if (gameService.isInventoryFull()) {
            currentState = MiningState.CHECK_INVENTORY;
            return;
        }
        
        log.info("=== FINDING ROCK DEBUG ===");
        log.info("nextRock is: {}", nextRock != null ? nextRock.getWorldLocation() : "null");
        
        // First, check if we have a pre-targeted nextRock that's still valid
        if (nextRock != null) {
            log.info("Checking if pre-targeted rock at {} is still valid...", nextRock.getWorldLocation());
            boolean isValid = isRockStillValid(nextRock);
            log.info("Pre-targeted rock validation result: {}", isValid);
            
            if (isValid) {
                log.info("✓ USING PRE-TARGETED ROCK at location: {} (was hovering over this rock)", nextRock.getWorldLocation());
                targetRock = nextRock;
                nextRock = null; // Clear it since we're now using it as target
            } else {
                log.info("✗ Pre-targeted rock at {} no longer exists, finding new nearest rock", nextRock.getWorldLocation());
                // Find a new rock if no valid pre-targeted rock
                int[] rockIds = plugin.getRockIds();
                targetRock = gameService.findNearestGameObject(rockIds);
                nextRock = null; // Clear next rock when finding a new target
                log.info("Found new nearest rock at: {}", targetRock != null ? targetRock.getWorldLocation() : "null");
            }
        } else {
            log.info("No pre-targeted rock, finding nearest rock");
            // Find a new rock if no valid pre-targeted rock
            int[] rockIds = plugin.getRockIds();
            targetRock = gameService.findNearestGameObject(rockIds);
            nextRock = null; // Clear next rock when finding a new target
            log.info("Found nearest rock at: {}", targetRock != null ? targetRock.getWorldLocation() : "null");
        }
        
        log.info("Final targetRock: {}", targetRock != null ? targetRock.getWorldLocation() : "null");
        log.info("=== END FINDING ROCK DEBUG ===");
        
        plugin.setTargetRock(targetRock);

        if (targetRock != null) {
            currentState = MiningState.MINING;
            doMining();
        } else {
            log.warn("No rocks found to mine. Checking rock configuration and player location.");
            int[] rockIds = plugin.getRockIds();
            log.warn("Configured rock IDs: {}", Arrays.toString(rockIds));
            log.warn("Player location: {}", gameService.getPlayerLocation());
            // Wait a bit before trying again to avoid spamming
            delayTicks = humanizerService.getRandomDelay(1, 3);
        }
    }

    private void doMining() {
        if (targetRock == null) {
            currentState = MiningState.FINDING_ROCK;
            return;
        }
        actionService.interactWithGameObject(targetRock, "Mine");
        miningStarted = false;
        xpGainedThisMine = 0;
        idleTicks = 0;
        lastMiningXp = plugin.getClient().getSkillExperience(Skill.MINING);
        currentState = MiningState.WAIT_MINING;
    }

    private void doWaitMining() {
        // Check if we're currently mining
        if (gameService.isCurrentlyMining()) {
            miningStarted = true;
            idleTicks = 0; // Reset idle counter if we see a mining animation
            
            // After mining animation starts, hover over the next rock to prepare
            if (nextRock == null) {
                currentState = MiningState.HOVER_NEXT_ROCK;
                return;
            }
        } else {
            idleTicks++; // Only increment idle ticks if not mining
        }
        
        if (idleTicks > 5) { // 5 ticks = 3 seconds
            log.warn("Mining seems to have failed or rock depleted. Finishing.");
            finishMining();
        }
    }

    private void finishMining() {
        log.info("Finished mining rock. XP gained: {}", xpGainedThisMine);
        targetRock = null;
        // Don't clear nextRock here - we want to use it if it's still valid
        plugin.setTargetRock(null);
        miningStarted = false;
        currentState = MiningState.CHECK_INVENTORY;
        doCheckInventory();
    }


    private void doCheckInventory() {
        if (gameService.isInventoryFull()) {
            switch (config.miningMode()) {
                case BANK:
                    log.info("Inventory full. Banking.");
                    // Order is reversed because we push to the top of the stack
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, gameService.getPlayerLocation(), actionService, gameService, humanizerService));
                    taskManager.pushTask(new BankTask(plugin, actionService, gameService, eventService));
                    log.info("Banking to: {}", plugin.getBankCoordinates());
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, plugin.getBankCoordinates(), actionService, gameService, humanizerService));
                    currentState = MiningState.WAITING_FOR_SUBTASK;
                    break;
                case POWER_MINE:
                    delayTicks = humanizerService.getRandomDelay(1, 3);
                    doDropping();
                    break;
            }
        } else {
            currentState = MiningState.FINDING_ROCK;
        }
    }

    private void doDropping() {
        currentState = MiningState.DROPPING;
        int[] oreIds = plugin.getOreIds();
        if (oreIds.length == 0) {
            log.info("No ore ids found. Cannot drop inventory. Stopping bot.");
            plugin.stopBot();
            return;
        }
        log.debug("Inventory contains ore ids: {}", Arrays.toString(oreIds));
        actionService.powerDrop(oreIds);
    }

    private void doHoverNextRock() {
        log.info("=== HOVER NEXT ROCK DEBUG ===");
        log.info("Current nextRock: {}", nextRock != null ? nextRock.getWorldLocation() : "null");
        
        // Find the next best rock to hover over
        if (nextRock == null) {
            log.info("Finding next best rock to hover over...");
            nextRock = findNextBestRock();
            log.info("Found next best rock: {}", nextRock != null ? nextRock.getWorldLocation() : "null");
        } else {
            log.info("Already have nextRock set, keeping it: {}", nextRock.getWorldLocation());
        }

        if (nextRock != null) {
            // Move mouse to hover over the next rock
            actionService.sendMouseMoveRequest(gameService.getRandomClickablePoint(nextRock));
            log.info("✓ PRE-TARGETING and hovering over next rock at location: {}", nextRock.getWorldLocation());
        } else {
            log.info("✗ No suitable next rock found to hover over");
        }
        
        log.info("=== END HOVER DEBUG ===");

        // Transition back to WAIT_MINING since this is just a positioning action
        currentState = MiningState.WAIT_MINING;
    }

    /**
     * Finds the next best rock to hover over, prioritizing rocks that are adjacent (not diagonal)
     * to the player and then falling back to the second nearest rock overall.
     */
    private GameObject findNextBestRock() {
        int[] rockIds = plugin.getRockIds();
        if (rockIds.length == 0) {
            return null;
        }

        WorldPoint playerLocation = gameService.getPlayerLocation();
        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();

        List<GameObject> availableRocks = new ArrayList<>();
        List<GameObject> adjacentRocks = new ArrayList<>();

        // Collect all matching rocks
        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) {
                    continue;
                }
                for (GameObject gameObject : tile.getGameObjects()) {
                    if (gameObject != null && gameObject != targetRock) {
                        for (int id : rockIds) {
                            if (gameObject.getId() == id) {
                                availableRocks.add(gameObject);
                                
                                // Check if this rock is adjacent (not diagonal) to player
                                WorldPoint rockLocation = gameObject.getWorldLocation();
                                int dx = Math.abs(rockLocation.getX() - playerLocation.getX());
                                int dy = Math.abs(rockLocation.getY() - playerLocation.getY());
                                
                                // Adjacent means exactly 1 tile away in X or Y direction (but not both)
                                if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
                                    adjacentRocks.add(gameObject);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Prioritize adjacent rocks first
        if (!adjacentRocks.isEmpty()) {
            // If multiple adjacent rocks, pick the closest one
            return adjacentRocks.stream()
                    .min(Comparator.comparingInt(rock -> rock.getWorldLocation().distanceTo(playerLocation)))
                    .orElse(null);
        }

        // If no adjacent rocks, find the second nearest rock
        if (availableRocks.size() >= 2) {
            List<GameObject> sortedRocks = availableRocks.stream()
                    .sorted(Comparator.comparingInt(rock -> rock.getWorldLocation().distanceTo(playerLocation)))
                    .collect(Collectors.toList());
            return sortedRocks.get(1); // Second nearest
        } else if (!availableRocks.isEmpty()) {
            // If only one rock available, return it
            return availableRocks.get(0);
        }

        return null; // No suitable rocks found
    }
    
    /**
     * Checks if a rock is still valid (exists in the scene and matches our target IDs).
     * This prevents us from trying to click on depleted rocks.
     */
    private boolean isRockStillValid(GameObject rock) {
        if (rock == null) {
            log.debug("Rock validation failed: rock is null");
            return false;
        }
        
        int[] rockIds = plugin.getRockIds();
        if (rockIds.length == 0) {
            log.debug("Rock validation failed: no rock IDs configured");
            return false;
        }
        
        log.debug("Validating rock at {} with ID {}", rock.getWorldLocation(), rock.getId());
        
        // Check if the rock ID is still one of our target IDs
        boolean matchesTargetIds = false;
        for (int id : rockIds) {
            if (rock.getId() == id) {
                matchesTargetIds = true;
                break;
            }
        }
        
        if (!matchesTargetIds) {
            log.debug("Rock validation failed: rock ID {} no longer matches target IDs (likely depleted)", rock.getId());
            return false;
        }
        
        // Direct scene tile validation - check if the specific rock still exists at the exact location
        WorldPoint rockLocation = rock.getWorldLocation();
        WorldPoint playerLocation = gameService.getPlayerLocation();
        
        // Check if rock is within reasonable distance (not too far away)
        int distance = rockLocation.distanceTo(playerLocation);
        if (distance > 20) {
            log.debug("Rock validation failed: rock is too far away (distance: {})", distance);
            return false;
        }
        
        // Get the scene and check the specific tile for our rock
        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();
        
        // Convert world coordinates to scene coordinates
        int baseX = plugin.getClient().getWorldView(-1).getBaseX();
        int baseY = plugin.getClient().getWorldView(-1).getBaseY();
        int sceneX = rockLocation.getX() - baseX;
        int sceneY = rockLocation.getY() - baseY;
        
        // Check bounds
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE) {
            log.debug("Rock validation failed: rock coordinates out of scene bounds");
            return false;
        }
        
        Tile tile = tiles[z][sceneX][sceneY];
        if (tile == null) {
            log.debug("Rock validation failed: no tile at expected location");
            return false;
        }
        
        // Check if our specific rock still exists on this tile
        for (GameObject gameObject : tile.getGameObjects()) {
            if (gameObject != null && gameObject.getId() == rock.getId() && 
                gameObject.getWorldLocation().equals(rockLocation)) {
                log.debug("Rock validation SUCCESS: found matching rock at expected location");
                return true;
            }
        }
        
        log.debug("Rock validation failed: could not find matching rock at expected location");
        return false;
    }
}