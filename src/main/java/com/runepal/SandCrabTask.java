package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import com.runepal.entity.Interactable;
import com.runepal.entity.NpcEntity;
import com.runepal.shortestpath.pathfinder.PathfinderConfig;

@Slf4j
public class SandCrabTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private final PotionService potionService;
    private final SupplyManager supplyManager;
    private ScheduledExecutorService scheduler;

    // Event handler references to maintain identity
    private Consumer<AnimationChanged> animationHandler;
    private Consumer<StatChanged> statHandler;
    private Consumer<InteractingChanged> interactingHandler;
    private Consumer<GameTick> gameTickHandler;
    private Consumer<ItemContainerChanged> itemContainerHandler;

    // Internal state for sand crab FSM
    private enum SandCrabState {
        IDLE,
        WALKING_TO_SPOT,
        WAITING_FOR_AGGRESSION,
        COMBAT_ACTIVE,
        EATING,
        DRINKING_POTION,
        RESETTING_AGGRESSION,
        WALKING_TO_RESET,
        BANKING,
        WORLD_HOPPING,
        WAITING_FOR_SUBTASK
    }

    // CrabSpot data structure for positioning
    private static class CrabSpot {
        private final WorldPoint combatPoint;
        private final WorldPoint resetPoint;
        private final int maxCrabs;
        private final String description;

        public CrabSpot(WorldPoint combatPoint, WorldPoint resetPoint, int maxCrabs, String description) {
            this.combatPoint = combatPoint;
            this.resetPoint = resetPoint;
            this.maxCrabs = maxCrabs;
            this.description = description;
        }

        public WorldPoint getCombatPoint() { return combatPoint; }
        public WorldPoint getResetPoint() { return resetPoint; }
        public int getMaxCrabs() { return maxCrabs; }
        public String getDescription() { return description; }
    }

    // Sand crab spot configurations (Varlamore south of agility course)
    private static final Map<Integer, CrabSpot> CRAB_SPOTS = new HashMap<>();
    static {
        CRAB_SPOTS.put(1, new CrabSpot(
            new WorldPoint(1600, 2935, 0), // Combat spot
            new WorldPoint(1620, 2941, 0), // Reset spot
            1, "Single crab spot"
        ));
        CRAB_SPOTS.put(2, new CrabSpot(
            new WorldPoint(1586, 2918, 0), // Combat spot
            new WorldPoint(1620, 2941, 0), // Reset spot
            2, "Double crab spot"
        ));
        CRAB_SPOTS.put(3, new CrabSpot(
            new WorldPoint(1597, 2941, 0), // Combat spot
            new WorldPoint(1620, 2941, 0), // Reset spot
            3, "Triple crab spot"
        ));
        CRAB_SPOTS.put(4, new CrabSpot(
            new WorldPoint(1612, 2891, 0),
            new WorldPoint(1620, 2941, 0),
            4, "Quad crab spot"
        ));
    }

    // Constants
    private static final int HUNTER_GUILD_BANK_CHEST_ID = 53015;
    private static final long AGGRESSION_TIMER_MS = 10 * 60 * 1000; // 10 minutes
    private static final int PLAYER_DETECTION_RANGE = 5; // tiles
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int TIMEOUT_TICKS = 50; // ~30 seconds

    // State management
    private SandCrabState currentState;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int delayTicks = 0;
    private int idleTicks = 0;
    private int retryCount = 0;
    private boolean isStarted = false;

    // Sand crab specific state
    private CrabSpot currentSpot;
    private long lastAggressionResetTime = 0;
    private long cameraRotateTimerMs = 0;
    private long lastRotateTime = 0;
    private WorldPoint targetPosition;
    private boolean needsAggression = false;
    private boolean playersDetected = false;

    // XP tracking
    private int lastAttackXp = 0;
    private int lastStrengthXp = 0;
    private int lastDefenceXp = 0;
    private int lastMagicXp = 0;
    private int lastRangedXp = 0;

    // Food types enum for dropdown selection
    public enum FoodType {
        COOKED_KARAMBWAN("Cooked Karambwan", 3144),
        MONKFISH("Monkfish", 7946),
        LOBSTER("Lobster", 379),
        SHARK("Shark", 385),
        SWORDFISH("Swordfish", 373),
        TUNA("Tuna", 361),
        SALMON("Salmon", 329);
        
        private final String displayName;
        private final int itemId;
        
        FoodType(String displayName, int itemId) {
            this.displayName = displayName;
            this.itemId = itemId;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getItemId() {
            return itemId;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
        
        public static FoodType fromString(String text) {
            for (FoodType food : FoodType.values()) {
                if (food.name().equals(text)) {
                    return food;
                }
            }
            return COOKED_KARAMBWAN; // Default fallback
        }
    }
    
    // Enhanced potion types for dropdown selection (includes NONE option)
    public enum PotionType {
        NONE("None"),
        SUPER_COMBAT("Super Combat"),
        PRAYER_POTION("Prayer Potion"),
        SUPER_STRENGTH("Super Strength"),
        SUPER_ATTACK("Super Attack"),
        SUPER_DEFENCE("Super Defence"),
        ANTIPOISON("Antipoison"),
        ENERGY("Energy");
        
        private final String displayName;
        
        PotionType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
        
        public static PotionType fromString(String text) {
            for (PotionType potion : PotionType.values()) {
                if (potion.name().equals(text)) {
                    return potion;
                }
            }
            return NONE; // Default fallback
        }
        
        public PotionService.PotionType toPotionServiceType() {
            switch (this) {
                case SUPER_COMBAT:
                    return PotionService.PotionType.SUPER_COMBAT;
                case PRAYER_POTION:
                    return PotionService.PotionType.PRAYER_POTION;
                case SUPER_STRENGTH:
                    return PotionService.PotionType.SUPER_STRENGTH;
                case SUPER_ATTACK:
                    return PotionService.PotionType.SUPER_ATTACK;
                case SUPER_DEFENCE:
                    return PotionService.PotionType.SUPER_DEFENCE;
                case ANTIPOISON:
                    return PotionService.PotionType.ANTIPOISON;
                case ENERGY:
                    return PotionService.PotionType.ENERGY;
                default:
                    return null;
            }
        }
    }

    public SandCrabTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, 
                       PathfinderConfig pathfinderConfig,
                       ActionService actionService, GameService gameService, 
                       EventService eventService, HumanizerService humanizerService, 
                       PotionService potionService, SupplyManager supplyManager) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.pathfinderConfig = pathfinderConfig;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.potionService = Objects.requireNonNull(potionService, "potionService cannot be null");
        this.supplyManager = Objects.requireNonNull(supplyManager, "supplyManager cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Sand Crab Task.");
        this.isStarted = true;
        
        // Initialize XP tracking
        Client client = plugin.getClient();
        this.lastAttackXp = client.getSkillExperience(Skill.ATTACK);
        this.lastStrengthXp = client.getSkillExperience(Skill.STRENGTH);
        this.lastDefenceXp = client.getSkillExperience(Skill.DEFENCE);
        this.lastMagicXp = client.getSkillExperience(Skill.MAGIC);
        this.lastRangedXp = client.getSkillExperience(Skill.RANGED);
        
        // Store event handler references to maintain identity
        this.animationHandler = this::onAnimationChanged;
        this.statHandler = this::onStatChanged;
        this.interactingHandler = this::onInteractingChanged;
        this.gameTickHandler = this::onGameTick;
        this.itemContainerHandler = this::onItemContainerChanged;
        
        // Subscribe to events
        this.eventService.subscribe(AnimationChanged.class, animationHandler);
        this.eventService.subscribe(StatChanged.class, statHandler);
        this.eventService.subscribe(InteractingChanged.class, interactingHandler);
        this.eventService.subscribe(GameTick.class, gameTickHandler);
        this.eventService.subscribe(ItemContainerChanged.class, itemContainerHandler);
        
        // Initialize scheduler
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        // Select optimal crab spot based on configuration
        this.currentSpot = selectOptimalCrabSpot();
        log.info("Selected crab spot: {}", currentSpot.getDescription());
        
        // Initialize aggression timer
        this.lastAggressionResetTime = System.currentTimeMillis();

        // Initialize camera rotation timer
        this.cameraRotateTimerMs = Math.round(humanizerService.getGaussian(5, 1.2, 0.0) * 60 * 1000);
        this.lastRotateTime = System.currentTimeMillis();
        log.info("Camera will rotate after {} ms", cameraRotateTimerMs);
        
        // Determine starting state
        if (needsToBank()) {
            pushBankingTask();
        } else if (needsToWalkToSpot()) {
            pushWalkToSpotTask();
        } else {
            this.currentState = SandCrabState.WAITING_FOR_AGGRESSION;
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Sand Crab Task.");
        this.isStarted = false;
        this.targetPosition = null;
        plugin.setTargetNpc(null); // Clear overlay
        
        // Unsubscribe from events
        this.eventService.unsubscribe(AnimationChanged.class, animationHandler);
        this.eventService.unsubscribe(StatChanged.class, statHandler);
        this.eventService.unsubscribe(InteractingChanged.class, interactingHandler);
        this.eventService.unsubscribe(GameTick.class, gameTickHandler);
        this.eventService.unsubscribe(ItemContainerChanged.class, itemContainerHandler);
        
        // Clear handler references
        this.animationHandler = null;
        this.statHandler = null;
        this.interactingHandler = null;
        this.gameTickHandler = null;
        this.itemContainerHandler = null;
        
        // Shutdown scheduler
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            this.scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isFinished() {
        // This task runs indefinitely until stopped by the user
        return false;
    }

    @Override
    public boolean isStarted() {
        return this.isStarted;
    }

    @Override
    public String getTaskName() {
        return "Sand Crab";
    }

    @Override
    public void onLoop() {
        // Handle subtask execution
        if (taskManager.getCurrentTask() != this) {
            if (currentState != SandCrabState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task is running. Pausing {}.", getTaskName());
                currentState = SandCrabState.WAITING_FOR_SUBTASK;
            }
            return;
        } else {
            if (currentState == SandCrabState.WAITING_FOR_SUBTASK) {
                log.info("Sub-task finished. Resuming {}.", getTaskName());
                currentState = determineNextStateAfterSubtask();
            }
        }

        // Process delays
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Process action queue
        if (!actionQueue.isEmpty()) {
            actionQueue.poll().run();
            return;
        }

        // Check for critical needs first
        if (shouldEat()) {
            if (currentState != SandCrabState.EATING) {
                log.info("Health is low, switching to eating state");
                currentState = SandCrabState.EATING;
            }
        } else if (shouldDrinkPotion()) {
            if (currentState != SandCrabState.DRINKING_POTION) {
                log.info("Need potion, switching to drinking potion state");
                currentState = SandCrabState.DRINKING_POTION;
            }
        } else if (needsToBank()) {
            log.info("Need to bank, switching to banking state");
            currentState = SandCrabState.BANKING;
        } else if (needsAggressionReset()) {
            log.info("Need to reset aggression, switching to reset state");
            currentState = SandCrabState.RESETTING_AGGRESSION;
        } else if (detectPlayersNearby()) {
            log.info("Players detected nearby, switching to world hopping");
            currentState = SandCrabState.WORLD_HOPPING;
        } else if (System.currentTimeMillis() - lastRotateTime > cameraRotateTimerMs) {
            taskManager.pushTask(new CameraRotationTask(plugin, actionService, eventService));
            lastRotateTime = System.currentTimeMillis();
            cameraRotateTimerMs = Math.round(humanizerService.getGaussian(5, 1.2, 0.0) * 60 * 1000);
            log.info("Rotating camera, next rotation at {}", cameraRotateTimerMs);
            currentState = SandCrabState.WAITING_FOR_SUBTASK;
        }

        // FSM state machine
        switch (currentState) {
            case IDLE:
                doIdle();
                break;
            case WALKING_TO_SPOT:
                doWalkingToSpot();
                break;
            case WAITING_FOR_AGGRESSION:
                doWaitingForAggression();
                break;
            case COMBAT_ACTIVE:
                doCombatActive();
                break;
            case EATING:
                doEating();
                break;
            case DRINKING_POTION:
                doDrinkingPotion();
                break;
            case RESETTING_AGGRESSION:
                doResettingAggression();
                break;
            case WALKING_TO_RESET:
                doWalkingToReset();
                break;
            case BANKING:
                doBanking();
                break;
            case WORLD_HOPPING:
                doWorldHopping();
                break;
            case WAITING_FOR_SUBTASK:
                // Handled above
                break;
            default:
                log.warn("Unknown state: {}", currentState);
                currentState = SandCrabState.IDLE;
                break;
        }
        
        // Update idle tracking
        updateIdleTracking();
        
        // Update UI
        plugin.setCurrentState("Sand Crab: " + currentState.toString());
    }

    // Event handlers
    public void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        
        int animationId = animationChanged.getActor().getAnimation();
        log.debug("Player animation changed: {}", animationId);
        
        // Reset idle tracking on any animation
        this.idleTicks = 0;
    }

    public void onStatChanged(StatChanged statChanged) {
        Skill skill = statChanged.getSkill();
        int currentXp = statChanged.getXp();
        int gainedXp = 0;
        
        switch (skill) {
            case ATTACK:
                gainedXp = currentXp - lastAttackXp;
                lastAttackXp = currentXp;
                break;
            case STRENGTH:
                gainedXp = currentXp - lastStrengthXp;
                lastStrengthXp = currentXp;
                break;
            case DEFENCE:
                gainedXp = currentXp - lastDefenceXp;
                lastDefenceXp = currentXp;
                break;
            case MAGIC:
                gainedXp = currentXp - lastMagicXp;
                lastMagicXp = currentXp;
                break;
            case RANGED:
                gainedXp = currentXp - lastRangedXp;
                lastRangedXp = currentXp;
                break;
        }
        
        if (gainedXp > 0) {
            log.trace("Gained {} {} XP", gainedXp, skill.getName());
            // Reset idle tracking on XP gain
            this.idleTicks = 0;
            
            // Update state if waiting for combat
            if (currentState == SandCrabState.WAITING_FOR_AGGRESSION) {
                actionQueue.add(() -> {
                    log.info("Combat detected through XP gain");
                    currentState = SandCrabState.COMBAT_ACTIVE;
                });
            }
        }
    }

    public void onInteractingChanged(InteractingChanged interactingChanged) {
        if (interactingChanged.getSource() != plugin.getClient().getLocalPlayer()) {
            return;
        }

        Actor target = interactingChanged.getTarget();
        if (target == null) {
            log.debug("Player stopped interacting");
            // Check if we should transition out of combat
            if (currentState == SandCrabState.COMBAT_ACTIVE) {
                actionQueue.add(() -> {
                    log.info("Combat ended, transitioning to waiting state");
                    currentState = SandCrabState.WAITING_FOR_AGGRESSION;
                });
            }
        } else {
            log.debug("Player began interacting with {}", target);
        }
    }

    public void onGameTick(GameTick gameTick) {
        
        // Check for aggression timer
        if (System.currentTimeMillis() - lastAggressionResetTime > AGGRESSION_TIMER_MS) {
            needsAggression = true;
        }
        
        // Increment idle tracking
        this.idleTicks++;
    }

    public void onItemContainerChanged(ItemContainerChanged itemContainerChanged) {
        if (itemContainerChanged.getContainerId() != InventoryID.INV) {
            return;
        }
        
        // Check if we need to bank due to supply changes
        if (needsToBank()) {
            log.info("Supplies depleted, need to bank");
            // Don't immediately change state here, let the main loop handle it
        }
    }

    // --- FSM STATE IMPLEMENTATIONS ---

    private void doIdle() {
        log.debug("In idle state, determining next action");
        
        // Check what we need to do
        if (needsToBank()) {
            currentState = SandCrabState.BANKING;
        } else if (needsToWalkToSpot()) {
            currentState = SandCrabState.WALKING_TO_SPOT;
        } else {
            currentState = SandCrabState.WAITING_FOR_AGGRESSION;
        }
    }

    private void doWalkingToSpot() {
        log.debug("Walking to spot state - should be handled by subtask");
        // This state should only be reached if subtask fails
        if (idleTicks > TIMEOUT_TICKS) {
            log.warn("Walking to spot timed out, retrying");
            retryOrFail("walk to spot");
        }
    }

    private void doWaitingForAggression() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        
        // Check if we're at the correct position
        if (!playerLocation.equals(currentSpot.getCombatPoint())) {
            log.debug("Not at combat position, moving to spot");
            pushWalkToSpotTask();
            return;
        }
        
        // Check if crabs are already aggressive
        if (isInCombat()) {
            log.info("Already in combat, transitioning to combat state");
            currentState = SandCrabState.COMBAT_ACTIVE;
            return;
        }
        
        // Check if we need to reset aggression
        if (needsAggression) {
            log.info("Need to reset aggression");
            currentState = SandCrabState.RESETTING_AGGRESSION;
            return;
        }
        
        // Wait for aggression to kick in
        log.debug("Waiting for sand crabs to become aggressive...");
        
        // Safety timeout
        if (idleTicks > TIMEOUT_TICKS) {
            log.warn("Waiting for aggression timed out, trying reset");
            currentState = SandCrabState.RESETTING_AGGRESSION;
        }
    }

    private void doCombatActive() {
        // Check if we're still in combat
        if (!isInCombat()) {
            log.info("Combat ended, returning to waiting state");
            currentState = SandCrabState.WAITING_FOR_AGGRESSION;
            resetIdleTracking();
            return;
        }
        
        // Safety timeout for stuck combat
        if (idleTicks > TIMEOUT_TICKS * 2) {
            log.warn("Combat state timed out, resetting");
            currentState = SandCrabState.RESETTING_AGGRESSION;
        }
    }

    private void doEating() {
        Point foodPoint = findFoodInInventory();
        
        if (foodPoint == null) {
            log.warn("No food found in inventory, continuing without eating");
            currentState = determineNextCombatState();
            return;
        }

        log.info("Eating food at point: {}", foodPoint);
        actionService.sendClickRequest(foodPoint, false);
        
        // Wait for eating animation
        delayTicks = humanizerService.getRandomDelay(3, 5);
        
        // Return to previous state
        currentState = determineNextCombatState();
    }

    private void doDrinkingPotion() {
        String potionType = config.sandCrabPotion();
        PotionType configuredPotion = PotionType.fromString(potionType);
        
        if (configuredPotion == PotionType.NONE) {
            log.warn("No potion configured, continuing without drinking");
            currentState = determineNextCombatState();
            return;
        }
        
        PotionService.PotionType potionToConsume = configuredPotion.toPotionServiceType();
        
        if (potionToConsume == null) {
            log.warn("Invalid potion type configured: {}", potionType);
            currentState = determineNextCombatState();
            return;
        }
        
        if (!potionService.hasPotion(potionToConsume)) {
            log.warn("No {} potion found in inventory", potionType);
            currentState = determineNextCombatState();
            return;
        }

        log.info("Consuming {} potion", potionType);
        boolean consumed = potionService.consumePotion(potionToConsume);
        
        if (consumed) {
            // Wait for potion consumption animation
            delayTicks = humanizerService.getRandomDelay(3, 5);
        }
        
        // Return to previous state
        currentState = determineNextCombatState();
    }

    private void doResettingAggression() {
        log.info("Resetting aggression by walking to reset point");
        
        // Walk to reset point
        WorldPoint resetPoint = currentSpot.getResetPoint();
        WalkTask resetWalkTask = new WalkTask(plugin, pathfinderConfig, resetPoint, actionService, gameService, humanizerService);

        taskManager.pushTask(resetWalkTask);
        
        // Set flag to return to combat spot after reset
        actionQueue.add(() -> {
            log.info("Reset walk completed, now walking back to combat spot");
            // Reset aggression timer
            lastAggressionResetTime = System.currentTimeMillis();
            needsAggression = false;
            currentState = SandCrabState.WALKING_TO_RESET;
        });
    }

    private void doWalkingToReset() {
        log.info("Walking back to combat spot after reset");
        
        // Walk back to combat spot
        pushWalkToSpotTask();

        actionQueue.add(() -> {
            log.info("Returned to combat spot, waiting for aggression");
            currentState = SandCrabState.WAITING_FOR_AGGRESSION;
        });
    }

    private void doBanking() {
        log.info("Banking for supplies");
        pushBankingTask();
        
        actionQueue.add(() -> {
            log.info("Banking completed, returning to combat");
            currentState = SandCrabState.WALKING_TO_SPOT;
        });
    }

    private void doWorldHopping() {
        log.info("Hopping worlds due to player detection");
        pushWorldHopTask();
        
        actionQueue.add(() -> {
            log.info("World hop completed, returning to combat");
            playersDetected = false;
            currentState = SandCrabState.WAITING_FOR_AGGRESSION;
        });
    }

    // --- HELPER METHODS ---

    private CrabSpot selectOptimalCrabSpot() {
        int configuredCrabCount = config.sandCrabCount();
        CrabSpot selectedSpot = CRAB_SPOTS.get(configuredCrabCount);
        
        if (selectedSpot == null) {
            log.warn("Invalid crab count configured: {}, defaulting to single crab", configuredCrabCount);
            selectedSpot = CRAB_SPOTS.get(1);
        }
        
        return selectedSpot;
    }

    private boolean needsToBank() {
        if (!"BANK".equals(config.sandCrabInventoryAction())) {
            return false;
        }
        
        // Check food supplies against configured minimum threshold
        int foodCount = getFoodCount();
        int minFoodCount = config.sandCrabMinFoodCount();
        if (foodCount <= minFoodCount) {
            log.info("Food count ({}) is at or below minimum threshold ({}), need to bank", foodCount, minFoodCount);
            return true;
        }
        
        // Check potion supplies against configured minimum threshold
        String potionType = config.sandCrabPotion();
        PotionType configuredPotion = PotionType.fromString(potionType);
        if (configuredPotion != PotionType.NONE) {
            int potionCount = getPotionCount(configuredPotion);
            int minPotionCount = config.sandCrabMinPotionCount();
            if (potionCount <= minPotionCount) {
                log.info("Potion count ({}) is at or below minimum threshold ({}), need to bank", potionCount, minPotionCount);
                return true;
            }
        }
        
        return false;
    }

    private boolean needsToWalkToSpot() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        WorldPoint combatPoint = currentSpot.getCombatPoint();
        
        return !playerLocation.equals(combatPoint);
    }

    private boolean shouldEat() {
        int currentHp = plugin.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int eatThreshold = config.sandCrabEatAtHp();
        
        return currentHp <= eatThreshold;
    }

    private boolean shouldDrinkPotion() {
        String potionType = config.sandCrabPotion();
        PotionType configuredPotion = PotionType.fromString(potionType);
        
        if (configuredPotion == PotionType.NONE) {
            return false;
        }
        
        PotionService.PotionType potion = configuredPotion.toPotionServiceType();
        if (potion == null) {
            log.warn("Invalid potion type: {}", potionType);
            return false;
        }
            
        switch (potion) {
            case SUPER_COMBAT:
                return potionService.needsCombatPotion() && potionService.hasPotion(potion);
            case PRAYER_POTION:
                return potionService.needsPrayerPotion(10) && potionService.hasPotion(potion);
            default:
                return false;
        }
    }

    private boolean needsAggressionReset() {
        return needsAggression && !isInCombat();
    }

    private boolean detectPlayersNearby() {
        WorldPoint playerLocation = gameService.getPlayerLocation();
        
        // Check for other players in the area
        for (Player player : plugin.getClient().getPlayers()) {
            if (player == plugin.getClient().getLocalPlayer()) {
                continue;
            }
            
            WorldPoint playerPos = player.getWorldLocation();
            if (playerPos.distanceTo(playerLocation) <= PLAYER_DETECTION_RANGE) {
                log.info("Detected player {} nearby at {}", player.getName(), playerPos);
                return true;
            }
        }
        
        return false;
    }

    private boolean isInCombat() {
        Player localPlayer = plugin.getClient().getLocalPlayer();
        return localPlayer.getInteracting() != null;
    }

    private void pushWalkToSpotTask() {
        WorldPoint combatPoint = currentSpot.getCombatPoint();
        WalkTask walkTask = new WalkTask(plugin, pathfinderConfig, combatPoint, actionService, gameService, humanizerService);
        
        taskManager.pushTask(walkTask);
        currentState = SandCrabState.WAITING_FOR_SUBTASK;
    }

    private void pushBankingTask() {
        // Create banking task configuration
        Map<Integer, Integer> itemsToWithdraw = new HashMap<>();
        
        // Add food
        String foodType = config.sandCrabFood();
        FoodType food = FoodType.fromString(foodType);
        itemsToWithdraw.put(food.getItemId(), config.sandCrabFoodQuantity());
        
        // Add potions
        String potionType = config.sandCrabPotion();
        PotionType configuredPotion = PotionType.fromString(potionType);
        if (configuredPotion != PotionType.NONE) {
            PotionService.PotionType potion = configuredPotion.toPotionServiceType();
            if (potion != null) {
                // Add potion IDs - would need to get from PotionService
                itemsToWithdraw.put(potion.getItemIds()[0], config.sandCrabPotionQuantity());
            }
        }
        
        // Create and push banking task
        // TODO: Add itemsToWithdraw Map<Integer, Integer> to BankTask
        BankTask bankTask = new BankTask(plugin, actionService, gameService, eventService);
        
        taskManager.pushTask(bankTask);
        currentState = SandCrabState.WAITING_FOR_SUBTASK;
    }

    private void pushWorldHopTask() {
        // Create world hop task
        WorldHopTask worldHopTask = new WorldHopTask(plugin, config, taskManager, 
                                                    gameService, actionService, eventService, 
                                                    humanizerService);
        
        taskManager.pushTask(worldHopTask);
        currentState = SandCrabState.WAITING_FOR_SUBTASK;
    }

    private SandCrabState determineNextStateAfterSubtask() {
        // Determine appropriate next state based on current conditions
        if (needsToBank()) {
            return SandCrabState.BANKING;
        } else if (needsToWalkToSpot()) {
            return SandCrabState.WALKING_TO_SPOT;
        } else if (needsAggressionReset()) {
            return SandCrabState.RESETTING_AGGRESSION;
        } else {
            return SandCrabState.WAITING_FOR_AGGRESSION;
        }
    }

    private SandCrabState determineNextCombatState() {
        if (isInCombat()) {
            return SandCrabState.COMBAT_ACTIVE;
        } else {
            return SandCrabState.WAITING_FOR_AGGRESSION;
        }
    }

    private Point findFoodInInventory() {
        String configuredFood = config.sandCrabFood();
        FoodType food = FoodType.fromString(configuredFood);
        int foodId = food.getItemId();
        
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (itemId == foodId) {
                return gameService.getInventoryItemPoint(slot);
            }
        }
        
        return null;
    }

    private int getFoodCount() {
        String configuredFood = config.sandCrabFood();
        FoodType food = FoodType.fromString(configuredFood);
        int foodId = food.getItemId();
        
        int count = 0;
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (itemId == foodId) {
                count++;
            }
        }
        
        return count;
    }

    private int getPotionCount(PotionType potionType) {
        if (potionType == PotionType.NONE) {
            return Integer.MAX_VALUE; // Never need to bank if no potion configured
        }
        
        PotionService.PotionType servicePotion = potionType.toPotionServiceType();
        if (servicePotion == null) {
            return 0;
        }
        
        int count = 0;
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (servicePotion.matches(itemId)) {
                count++;
            }
        }
        
        log.debug("Found {} potions of type {} in inventory", count, potionType);
        return count;
    }

    private void updateIdleTracking() {
        // Reset idle tracking if in combat or moving
        if (isInCombat() || plugin.getClient().getLocalPlayer().getAnimation() != -1) {
            this.idleTicks = 0;
        }
    }

    private void resetIdleTracking() {
        this.idleTicks = 0;
    }

    private void retryOrFail(String operation) {
        retryCount++;
        
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            log.error("Failed to {} after {} attempts, stopping task", operation, MAX_RETRY_ATTEMPTS);
            // Could implement task stopping here or transition to safe state
            currentState = SandCrabState.IDLE;
            retryCount = 0;
        } else {
            log.warn("Retrying {} (attempt {}/{})", operation, retryCount, MAX_RETRY_ATTEMPTS);
            currentState = SandCrabState.IDLE;
            delayTicks = humanizerService.getRandomDelay(3, 7);
        }
    }
}