package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.StatChanged;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
public class MiningTask implements BotTask {

    private final MiningBotPlugin plugin;
    private final MiningBotConfig config;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;

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
    }

    @Override
    public void onStop() {
        log.info("Stopping Mining Task.");
        this.targetRock = null;
        plugin.setTargetRock(null); // Clear overlay
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
        if (newAnimation == AnimationID.IDLE || !isMiningAnimation(newAnimation)) {
            log.info("Mining animation stopped. Animation: {}", newAnimation);
            finishMining();
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
                setRandomDelay(1, 2);
                actionQueue.add(this::finishMining);
            }
        } else {
            lastMiningXp = currentXp;
        }
    }

    // --- FSM LOGIC ---
    private void setRandomDelay(int minTicks, int maxTicks) {
        delayTicks = plugin.getRandom().nextInt(maxTicks - minTicks + 1) + minTicks;
    }

    private void doFindingRock() {
        if (plugin.isInventoryFull()) {
            currentState = MiningState.CHECK_INVENTORY;
            return;
        }
        int[] rockIds = plugin.getRockIds();
        targetRock = plugin.findNearestGameObject(rockIds);
        plugin.setTargetRock(targetRock);

        if (targetRock != null) {
            currentState = MiningState.MINING;
            doMining();
        } else {
            log.info("No rocks found to mine.");
            setRandomDelay(10, 20); // Wait a while before searching again
        }
    }

    private void doMining() {
        if (targetRock == null) {
            currentState = MiningState.FINDING_ROCK;
            return;
        }
        plugin.sendClickRequest(plugin.getRandomClickablePoint(targetRock));
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
        return animationId == AnimationID.MINING_BRONZE_PICKAXE ||
                animationId == AnimationID.MINING_IRON_PICKAXE ||
                animationId == AnimationID.MINING_STEEL_PICKAXE ||
                animationId == AnimationID.MINING_BLACK_PICKAXE ||
                animationId == AnimationID.MINING_MITHRIL_PICKAXE ||
                animationId == AnimationID.MINING_ADAMANT_PICKAXE ||
                animationId == AnimationID.MINING_RUNE_PICKAXE ||
                animationId == AnimationID.MINING_DRAGON_PICKAXE ||
                animationId == AnimationID.MINING_DRAGON_PICKAXE_OR ||
                animationId == AnimationID.MINING_INFERNAL_PICKAXE ||
                animationId == AnimationID.MINING_3A_PICKAXE ||
                animationId == AnimationID.MINING_CRYSTAL_PICKAXE ||
                animationId == AnimationID.MINING_TRAILBLAZER_PICKAXE;
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
        plugin.sendKeyRequest("/key_hold", "shift");
        setRandomDelay(1, 2);

        actionQueue.add(() -> {
            int[] oreIds = plugin.getOreIds();
            for (int i = 0; i < 28; i++) {
                int itemId = plugin.getInventoryItemId(i);
                if (plugin.isItemInList(itemId, oreIds)) {
                    final int finalI = i;
                    actionQueue.add(() -> {
                        plugin.sendClickRequest(plugin.getInventoryItemPoint(finalI));
                        setRandomDelay(1, 2);
                    });
                }
            }
            actionQueue.add(() -> {
                plugin.sendKeyRequest("/key_release", "shift");
                setRandomDelay(1, 2);
                actionQueue.add(() -> currentState = MiningState.FINDING_ROCK);
            });
        });
    }
} 