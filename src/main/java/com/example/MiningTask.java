package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.MenuEntry;
import net.runelite.client.util.Text;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MiningTask implements BotTask {

    private final MiningBotPlugin plugin;
    private final MiningBotConfig config;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;
    private ScheduledExecutorService scheduler;

    // Internal state for this task only
    private enum MiningState {
        IDLE,
        FINDING_ROCK,
        MINING,
        WAIT_MINING,
        CHECK_INVENTORY,
        DROPPING,
        WALKING_TO_BANK,
        BANKING,
        WALKING_TO_MINE,
        WAITING_FOR_SUBTASK
    }

    private static final WorldPoint VARROCK_EAST_MINE = new WorldPoint(3285, 3365, 0);
    private static final WorldPoint VARROCK_EAST_BANK = new WorldPoint(3253, 3420, 0);

    private MiningState currentState;
    private final Deque<Runnable> actionQueue = new ArrayDeque<>();
    private int idleTicks = 0;
    private int delayTicks = 0;
    private GameObject targetRock = null;
    private volatile boolean droppingFinished = false;

    // Mining completion detection variables
    private long lastMiningXp = 0;
    private long xpGainedThisMine = 0;
    private boolean miningStarted = false;

    public MiningTask(MiningBotPlugin plugin, MiningBotConfig config, TaskManager taskManager, PathfinderConfig pathfinderConfig) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.pathfinderConfig = pathfinderConfig;
    }

    @Override
    public void onStart() {
        log.info("Starting Mining Task.");
        this.currentState = MiningState.FINDING_ROCK;
        this.lastMiningXp = plugin.getClient().getSkillExperience(Skill.MINING);
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Mining Task.");
        this.targetRock = null;
        plugin.setTargetRock(null); // Clear overlay
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            this.scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isFinished() {
        // This task runs indefinitely until stopped by the user via the TaskManager.
        return false;
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
            case CHECK_INVENTORY:
                doCheckInventory();
                break;
            case DROPPING:
                doDropping();
                break;
            case IDLE:
                // Do nothing in idle state
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
        if (isMiningAnimation(newAnimation)) {
            log.info("Mining animation started. Animation: {}", newAnimation);
            locateRock();
            plugin.sendMouseMoveRequest(plugin.getRandomClickablePoint(targetRock));
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

    // --- FSM LOGIC ---
    private void setRandomDelay(int minTicks, int maxTicks) {
        delayTicks = plugin.getRandom().nextInt(maxTicks - minTicks + 1) + minTicks;
        delayTicks = 0;
    }

    private void doFindingRock() {
        if (plugin.isInventoryFull()) {
            currentState = MiningState.CHECK_INVENTORY;
            return;
        }
        if (!verifyHoverAction("Mine", "Copper rocks")) {
            locateRock();
            if (targetRock != null) {
                currentState = MiningState.MINING;
                plugin.sendMouseMoveRequest(plugin.getRandomClickablePoint(targetRock));
                return;
            } else {
                log.info("No rocks found to mine.");
                setRandomDelay(10, 20); // Wait a while before searching again
                return;
            }
        }
        currentState = MiningState.MINING;
        plugin.sendClickRequest(plugin.getRandomClickablePoint(targetRock), false);
    }

    private void doMining() {
        if (targetRock == null) {
            currentState = MiningState.FINDING_ROCK;
            return;
        }
        if (!verifyHoverAction("Mine", "Copper rocks")) {
            log.info("Click targeted wrong action. Did not execute click.");
            currentState = MiningState.FINDING_ROCK;
            return;
        }
        plugin.sendClickRequest(plugin.getRandomClickablePoint(targetRock), false);
        miningStarted = false;
        xpGainedThisMine = 0;
        idleTicks = 0;
        lastMiningXp = plugin.getClient().getSkillExperience(Skill.MINING);
        currentState = MiningState.WAIT_MINING;
        setRandomDelay(3, 5); // Wait a few ticks for the animation to start
    }

    private void doWaitMining() {
        idleTicks++;
        int currentAnimation = plugin.getClient().getLocalPlayer().getAnimation();
        if (isMiningAnimation(currentAnimation)) {
            miningStarted = true;
            idleTicks = 0; // Reset idle counter if we see a mining animation
        }
        if (idleTicks > 10) { // 10 ticks = 6 seconds
            log.warn("Mining seems to have failed or rock depleted (idle for 6s). Finishing.");
            finishMining();
        }
    }

    private void finishMining() {
        log.info("Finished mining rock. XP gained: {}", xpGainedThisMine);
        targetRock = null;
        plugin.setTargetRock(null);
        miningStarted = false;
        currentState = MiningState.CHECK_INVENTORY;
        doCheckInventory();
    }

    private boolean isMiningAnimation(int animationId) {
        return animationId == AnimationID.HUMAN_MINING_BRONZE_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_IRON_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_STEEL_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_BLACK_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_MITHRIL_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_ADAMANT_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_RUNE_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_DRAGON_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY ||
                animationId == AnimationID.HUMAN_MINING_INFERNAL_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_3A_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE ||
                animationId == AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE;
    }

    private void doCheckInventory() {
        if (plugin.isInventoryFull()) {
            switch (config.miningMode()) {
                case BANK:
                    log.info("Inventory full. Banking.");
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, VARROCK_EAST_MINE));
                    taskManager.pushTask(new BankTask(plugin));
                    taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, VARROCK_EAST_BANK));
                    currentState = MiningState.WAITING_FOR_SUBTASK;
                    break;
                case POWER_MINE:
                    currentState = MiningState.DROPPING;
                    break;
            }
        } else {
            currentState = MiningState.FINDING_ROCK;
        }
    }

    private void doDropping() {
        log.info("Starting to drop inventory.");
        currentState = MiningState.IDLE; // Prevent other actions while dropping

        plugin.sendKeyRequest("/key_hold", "shift");

        long delay = (long) (Math.random() * (250 - 350)) + 350; // Initial delay before first click
        int[] oreIds = plugin.getOreIds();

        for (int i = 0; i < 28; i++) {
            int itemId = plugin.getInventoryItemId(i);
            if (plugin.isItemInList(itemId, oreIds)) {
                final int finalI = i;
                scheduler.schedule(() -> {
                    plugin.sendClickRequest(plugin.getInventoryItemPoint(finalI), true);
                }, delay, TimeUnit.MILLISECONDS);
                delay += (long) (Math.random() * (250 - 350)) + 350;; // Stagger subsequent clicks
            }
        }

        // Schedule the final actions after all drops are scheduled
        scheduler.schedule(() -> {
            plugin.sendKeyRequest("/key_release", "shift");
            log.info("Finished dropping inventory.");
            droppingFinished = true; // Signal to the main loop
        }, delay, TimeUnit.MILLISECONDS);
    }

    public boolean verifyHoverAction(String expectedAction, String expectedTarget) {
        // Get the menu entries that are present on hover
        MenuEntry[] menuEntries = plugin.getClient().getMenu().getMenuEntries();

        // Check if there are any menu entries at all
        if (menuEntries.length == 0) {
            return false;
        }

        // The default action is the last entry in the array.
        // The array is ordered from the bottom of the right-click menu to the top.
        MenuEntry topEntry = menuEntries[menuEntries.length - 1];

        String action = topEntry.getOption();
        log.info("Left-click action: {}", action);
        // The target name might have color tags (e.g., <col=ff9040>Goblin</col>)
        // It's a good practice to clean this up for reliable comparison.
        String target = Text.removeTags(topEntry.getTarget());
        log.info("Left-click target: {}", target);

        // Perform the verification
        return action.equalsIgnoreCase(expectedAction) && target.equalsIgnoreCase(expectedTarget);
    }

    // Expects that the mouse is already over the target
    public boolean verifyAndClick(String expectedAction, String expectedTarget) {
        if (verifyHoverAction(expectedAction, expectedTarget)) {
            plugin.sendClickRequest(plugin.getRandomClickablePoint(targetRock), false);
            return true;
        }
        return false;
    }

    private void locateRock() {
        int[] rockIds = plugin.getRockIds();
        targetRock = plugin.findNearestGameObject(rockIds);
        plugin.setTargetRock(targetRock);
    }
} 