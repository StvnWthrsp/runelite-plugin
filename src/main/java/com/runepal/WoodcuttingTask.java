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
import com.runepal.shortestpath.pathfinder.PathfinderConfig;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.function.Consumer;

@Slf4j
public class WoodcuttingTask implements BotTask {

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
    private enum WoodcuttingState {
        IDLE,
        FINDING_TREE,
        CUTTING,
        WAIT_CUTTING,
        HOVER_NEXT_TREE,
        CHECK_INVENTORY,
        DROPPING,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_TREES,
        WAITING_FOR_SUBTASK
    }

    private static final WorldPoint VARROCK_EAST_TREES = new WorldPoint(3290, 3360, 0);
    private static final WorldPoint VARROCK_EAST_BANK = new WorldPoint(3253, 3420, 0);

    private WoodcuttingState currentState = null;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int idleTicks = 0;
    private int delayTicks = 0;
    private GameObject targetTree = null;
    private GameObject nextTree = null;
    private volatile boolean droppingFinished = false;

    // Woodcutting completion detection variables
    private long lastWoodcuttingXp = 0;
    private long xpGainedThisCut = 0;
    private boolean cuttingStarted = false;

    public WoodcuttingTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, PathfinderConfig pathfinderConfig, ActionService actionService, GameService gameService, EventService eventService, HumanizerService humanizerService) {
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
        log.info("Starting Woodcutting Task.");
        this.lastWoodcuttingXp = plugin.getClient().getSkillExperience(Skill.WOODCUTTING);
        
        // Store event handler references to maintain identity
        this.animationHandler = this::onAnimationChanged;
        this.statHandler = this::onStatChanged;
        this.interactingHandler = this::onInteractingChanged;
        this.gameTickHandler = this::onGameTick;
        
        this.eventService.subscribe(AnimationChanged.class, animationHandler);
        this.eventService.subscribe(StatChanged.class, statHandler);
        this.eventService.subscribe(InteractingChanged.class, interactingHandler);
        this.eventService.subscribe(GameTick.class, gameTickHandler);
        this.currentState = WoodcuttingState.FINDING_TREE;
    }

    @Override
    public void onStop() {
        log.info("Stopping Woodcutting Task.");
        this.targetTree = null;
        this.nextTree = null;
        plugin.setTargetTree(null); // Clear overlay
        
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
        return "Woodcutting";
    }

    @Override
    public void onLoop() {
        if (droppingFinished) {
            droppingFinished = false;
            currentState = WoodcuttingState.FINDING_TREE;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (taskManager.getCurrentTask() != this) {
            if (currentState != WoodcuttingState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing WoodcuttingTask.");
                currentState = WoodcuttingState.WAITING_FOR_SUBTASK;
            }
            return;
        } else {
            if (currentState == WoodcuttingState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task finished. Resuming WoodcuttingTask.");
                currentState = WoodcuttingState.FINDING_TREE; // Or whatever is appropriate
            }
        }

        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        switch (currentState) {
            case FINDING_TREE:
                doFindingTree();
                break;
            case CUTTING:
                doCutting();
                break;
            case WAIT_CUTTING:
                doWaitCutting();
                break;
            case HOVER_NEXT_TREE:
                doHoverNextTree();
                break;
            case CHECK_INVENTORY:
                doCheckInventory();
                break;
            case DROPPING:
                if (!actionService.isDropping()) {
                    log.info("Dropping complete. Resuming woodcutting.");
                    currentState = WoodcuttingState.FINDING_TREE;
                }
                break;
            case IDLE:
                currentState = WoodcuttingState.FINDING_TREE;
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
        if (currentState != WoodcuttingState.WAIT_CUTTING) {
            return;
        }
        int newAnimation = plugin.getClient().getLocalPlayer().getAnimation();
        if (gameService.isCurrentAnimation(newAnimation)) {
            log.info("Woodcutting animation started. Animation: {}", newAnimation);
        }
    }

    public void onStatChanged(StatChanged statChanged) {
        if (statChanged.getSkill() != Skill.WOODCUTTING) {
            return;
        }
        long currentXp = statChanged.getXp();
        if (currentState == WoodcuttingState.WAIT_CUTTING && cuttingStarted) {
            if (currentXp > lastWoodcuttingXp) {
                long xpGained = currentXp - lastWoodcuttingXp;
                xpGainedThisCut += xpGained;
                lastWoodcuttingXp = currentXp;
                log.info("Gained {} woodcutting XP (total this cut: {})", xpGained, xpGainedThisCut);
                actionQueue.add(this::finishCutting);
            }
        } else {
            lastWoodcuttingXp = currentXp;
        }
    }

    public void onInteractingChanged(InteractingChanged interactingChanged) {
        if (interactingChanged.getSource() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        if (currentState == WoodcuttingState.CUTTING) {
            log.info("Interacting changed. Woodcutting: {}", interactingChanged.getTarget().getName());
        }
    }

    public void onGameTick(GameTick gameTick) {
    }

    // --- FSM LOGIC ---

    private void doFindingTree() {
        if (gameService.isInventoryFull()) {
            currentState = WoodcuttingState.CHECK_INVENTORY;
            return;
        }
        
        log.info("=== FINDING TREE DEBUG ===");
        log.info("nextTree is: {}", nextTree != null ? nextTree.getWorldLocation() : "null");
        
        // First, check if we have a pre-targeted nextTree that's still valid
        if (nextTree != null) {
            log.info("Checking if pre-targeted tree at {} is still valid...", nextTree.getWorldLocation());
            boolean isValid = isTreeStillValid(nextTree);
            log.info("Pre-targeted tree validation result: {}", isValid);
            
            if (isValid) {
                log.info("✓ USING PRE-TARGETED TREE at location: {} (was hovering over this tree)", nextTree.getWorldLocation());
                targetTree = nextTree;
                nextTree = null; // Clear it since we're now using it as target
            } else {
                log.info("✗ Pre-targeted tree at {} no longer exists, finding new nearest tree", nextTree.getWorldLocation());
                // Find a new tree if no valid pre-targeted tree
                int[] treeIds = plugin.getTreeIds();
                targetTree = gameService.findNearestGameObject(treeIds);
                nextTree = null; // Clear next tree when finding a new target
                log.info("Found new nearest tree at: {}", targetTree != null ? targetTree.getWorldLocation() : "null");
            }
        } else {
            log.info("No pre-targeted tree, finding nearest tree");
            // Find a new tree if no valid pre-targeted tree
            int[] treeIds = plugin.getTreeIds();
            targetTree = gameService.findNearestGameObject(treeIds);
            nextTree = null; // Clear next tree when finding a new target
            log.info("Found nearest tree at: {}", targetTree != null ? targetTree.getWorldLocation() : "null");
        }
        
        log.info("Final targetTree: {}", targetTree != null ? targetTree.getWorldLocation() : "null");
        log.info("=== END FINDING TREE DEBUG ===");
        
        plugin.setTargetTree(targetTree);

        if (targetTree != null) {
            currentState = WoodcuttingState.CUTTING;
            doCutting();
        } else {
            log.warn("No trees found to cut. Checking tree configuration and player location.");
            int[] treeIds = plugin.getTreeIds();
            log.warn("Configured tree IDs: {}", Arrays.toString(treeIds));
            log.warn("Player location: {}", gameService.getPlayerLocation());
            // Wait a bit before trying again to avoid spamming
            delayTicks = humanizerService.getRandomDelay(1, 3);
        }
    }

    private void doCutting() {
        if (targetTree == null) {
            currentState = WoodcuttingState.FINDING_TREE;
            return;
        }

        actionService.interactWithGameObject(targetTree, "Chop down");
        cuttingStarted = false;
        xpGainedThisCut = 0;
        idleTicks = 0;
        lastWoodcuttingXp = plugin.getClient().getSkillExperience(Skill.WOODCUTTING);
        currentState = WoodcuttingState.WAIT_CUTTING;
    }

    private void doWaitCutting() {
        // Check if we're currently woodcutting
        if (gameService.isCurrentlyWoodcutting()) {
            cuttingStarted = true;
            idleTicks = 0; // Reset idle counter if we see a woodcutting animation
            
            // After woodcutting animation starts, hover over the next tree to prepare
            if (nextTree == null) {
                currentState = WoodcuttingState.HOVER_NEXT_TREE;
                return;
            }
        } else {
            idleTicks++; // Only increment idle ticks if not woodcutting
        }
        
        if (idleTicks > 5) { // 5 ticks = 3 seconds
            log.warn("Woodcutting seems to have failed or tree depleted. Finishing.");
            finishCutting();
        }
    }

    private void finishCutting() {
        log.info("Finished cutting tree. XP gained: {}", xpGainedThisCut);
        targetTree = null;
        // Don't clear nextTree here - we want to use it if it's still valid
        plugin.setTargetTree(null);
        cuttingStarted = false;
        currentState = WoodcuttingState.CHECK_INVENTORY;
        doCheckInventory();
    }

    private void doCheckInventory() {
        if (gameService.isInventoryFull()) {
            switch (config.woodcuttingMode()) {
                case BANK:
                    log.info("Inventory full. Banking.");
                    // Order is reversed because we push to the top of the stack
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, gameService.getPlayerLocation(), actionService, gameService, humanizerService));
                    taskManager.pushTask(new BankTask(plugin, actionService, gameService, eventService));
                    log.info("Banking to: {}", plugin.getBankCoordinates());
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, plugin.getBankCoordinates(), actionService, gameService, humanizerService));
                    currentState = WoodcuttingState.WAITING_FOR_SUBTASK;
                    break;
                case POWER_CHOP:
                    delayTicks = humanizerService.getRandomDelay(1, 3);
                    doDropping();
                    break;
            }
        } else {
            currentState = WoodcuttingState.FINDING_TREE;
        }
    }

    private void doDropping() {
        currentState = WoodcuttingState.DROPPING;
        int[] logIds = plugin.getLogIds();
        if (logIds.length == 0) {
            log.info("No log ids found. Cannot drop inventory. Stopping bot.");
            plugin.stopBot();
            return;
        }
        log.debug("Inventory contains log ids: {}", Arrays.toString(logIds));
        actionService.powerDrop(logIds);
    }

    private void doHoverNextTree() {
        log.info("=== HOVER NEXT TREE DEBUG ===");
        log.info("Current nextTree: {}", nextTree != null ? nextTree.getWorldLocation() : "null");
        
        // Find the next best tree to hover over
        if (nextTree == null) {
            log.info("Finding next best tree to hover over...");
            nextTree = findNextBestTree();
            log.info("Found next best tree: {}", nextTree != null ? nextTree.getWorldLocation() : "null");
        } else {
            log.info("Already have nextTree set, keeping it: {}", nextTree.getWorldLocation());
        }

        if (nextTree != null) {
            // Move mouse to hover over the next tree
            actionService.sendMouseMoveRequest(gameService.getRandomClickablePoint(nextTree));
            log.info("✓ PRE-TARGETING and hovering over next tree at location: {}", nextTree.getWorldLocation());
        } else {
            log.info("✗ No suitable next tree found to hover over");
        }
        
        log.info("=== END HOVER DEBUG ===");

        // Transition back to WAIT_CUTTING since this is just a positioning action
        currentState = WoodcuttingState.WAIT_CUTTING;
    }

    /**
     * Finds the next best tree to hover over, prioritizing trees that are adjacent (not diagonal)
     * to the player and then falling back to the second nearest tree overall.
     */
    private GameObject findNextBestTree() {
        int[] treeIds = plugin.getTreeIds();
        if (treeIds.length == 0) {
            return null;
        }

        WorldPoint playerLocation = gameService.getPlayerLocation();
        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();

        List<GameObject> availableTrees = new ArrayList<>();
        List<GameObject> adjacentTrees = new ArrayList<>();

        // Collect all matching trees
        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) {
                    continue;
                }
                for (GameObject gameObject : tile.getGameObjects()) {
                    if (gameObject != null && gameObject != targetTree) {
                        for (int id : treeIds) {
                            if (gameObject.getId() == id) {
                                availableTrees.add(gameObject);
                                
                                // Check if this tree is adjacent (not diagonal) to player
                                WorldPoint treeLocation = gameObject.getWorldLocation();
                                int dx = Math.abs(treeLocation.getX() - playerLocation.getX());
                                int dy = Math.abs(treeLocation.getY() - playerLocation.getY());
                                
                                // Adjacent means exactly 1 tile away in X or Y direction (but not both)
                                if ((dx == 1 && dy == 0) || (dx == 0 && dy == 1)) {
                                    adjacentTrees.add(gameObject);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Prioritize adjacent trees first
        if (!adjacentTrees.isEmpty()) {
            // If multiple adjacent trees, pick the closest one
            return adjacentTrees.stream()
                    .min(Comparator.comparingInt(tree -> tree.getWorldLocation().distanceTo(playerLocation)))
                    .orElse(null);
        }

        // If no adjacent trees, find the second nearest tree
        if (availableTrees.size() >= 2) {
            List<GameObject> sortedTrees = availableTrees.stream()
                    .sorted(Comparator.comparingInt(tree -> tree.getWorldLocation().distanceTo(playerLocation)))
                    .collect(Collectors.toList());
            return sortedTrees.get(1); // Second nearest
        } else if (!availableTrees.isEmpty()) {
            // If only one tree available, return it
            return availableTrees.get(0);
        }

        return null; // No suitable trees found
    }
    
    /**
     * Checks if a tree is still valid (exists in the scene and matches our target IDs).
     * This prevents us from trying to click on depleted trees.
     */
    private boolean isTreeStillValid(GameObject tree) {
        if (tree == null) {
            log.debug("Tree validation failed: tree is null");
            return false;
        }
        
        int[] treeIds = plugin.getTreeIds();
        if (treeIds.length == 0) {
            log.debug("Tree validation failed: no tree IDs configured");
            return false;
        }
        
        log.debug("Validating tree at {} with ID {}", tree.getWorldLocation(), tree.getId());
        
        // Check if the tree ID is still one of our target IDs
        boolean matchesTargetIds = false;
        for (int id : treeIds) {
            if (tree.getId() == id) {
                matchesTargetIds = true;
                break;
            }
        }
        
        if (!matchesTargetIds) {
            log.debug("Tree validation failed: tree ID {} no longer matches target IDs (likely depleted)", tree.getId());
            return false;
        }
        
        // Direct scene tile validation - check if the specific tree still exists at the exact location
        WorldPoint treeLocation = tree.getWorldLocation();
        WorldPoint playerLocation = gameService.getPlayerLocation();
        
        // Check if tree is within reasonable distance (not too far away)
        int distance = treeLocation.distanceTo(playerLocation);
        if (distance > 20) {
            log.debug("Tree validation failed: tree is too far away (distance: {})", distance);
            return false;
        }
        
        // Get the scene and check the specific tile for our tree
        Scene scene = plugin.getClient().getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = plugin.getClient().getWorldView(-1).getPlane();
        
        // Convert world coordinates to scene coordinates
        int baseX = plugin.getClient().getWorldView(-1).getBaseX();
        int baseY = plugin.getClient().getWorldView(-1).getBaseY();
        int sceneX = treeLocation.getX() - baseX;
        int sceneY = treeLocation.getY() - baseY;
        
        // Check bounds
        if (sceneX < 0 || sceneX >= Constants.SCENE_SIZE || sceneY < 0 || sceneY >= Constants.SCENE_SIZE) {
            log.debug("Tree validation failed: tree coordinates out of scene bounds");
            return false;
        }
        
        Tile tile = tiles[z][sceneX][sceneY];
        if (tile == null) {
            log.debug("Tree validation failed: no tile at expected location");
            return false;
        }
        
        // Check if our specific tree still exists on this tile
        for (GameObject gameObject : tile.getGameObjects()) {
            if (gameObject != null && gameObject.getId() == tree.getId() && 
                gameObject.getWorldLocation().equals(treeLocation)) {
                log.debug("Tree validation SUCCESS: found matching tree at expected location");
                return true;
            }
        }
        
        log.debug("Tree validation failed: could not find matching tree at expected location");
        return false;
    }
}