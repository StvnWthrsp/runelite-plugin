package com.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;

import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.example.entity.Interactable;
import com.example.entity.NpcEntity;

import java.util.Arrays;

@Slf4j
public class CombatTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private ScheduledExecutorService scheduler;
    private final GameService gameService;
    private final ActionService actionService;

    // Internal state for combat FSM
    private enum CombatState {
        IDLE,
        FINDING_NPC,
        VERIFY_ATTACK,
        ATTACKING,
        EATING,
        LOOTING,
        WAITING_FOR_COMBAT_END
    }

    private CombatState currentState;
    private NPC targetNpc = null;
    private int idleTicks = 0;
    private int delayTicks = 0;
    private int combatStartTicks = 0;
    private int lastHealthCheck = 0;
    private int waitToVerifyTicks = 0;
    
    // Food item IDs (common foods)
    private static final int[] FOOD_IDS = {
        379,  // Lobster
        385,  // Shark
        7946, // Monkfish
        361,  // Tuna
        373,  // Swordfish
        2142, // Cooked karambwan
        329   // Salmon
    };

    public CombatTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, ActionService actionService, GameService gameService) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Combat Task.");
        this.currentState = CombatState.FINDING_NPC;
        this.lastHealthCheck = plugin.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Combat Task.");
        this.targetNpc = null;
        plugin.setTargetNpc(null); // Clear overlay
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
        if (currentState == null) {
            return false;
        }
        return true;
    }

    @Override
    public String getTaskName() {
        return "Combat";
    }

    @Override
    public void onLoop() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Always check health first, regardless of state
        if (shouldEat()) {
            if (currentState != CombatState.EATING) {
                log.info("Health is low, switching to eating state");
                currentState = CombatState.EATING;
            }
        }

        switch (currentState) {
            case FINDING_NPC:
                doFindingNpc();
                break;
            case VERIFY_ATTACK:
                doVerifyAttack();
                break;
            case ATTACKING:
                doAttacking();
                break;
            case EATING:
                doEating();
                break;
            case LOOTING:
                doLooting();
                break;
            case WAITING_FOR_COMBAT_END:
                doWaitingForCombatEnd();
                break;
            case IDLE:
                // Do nothing in idle state
                break;
            default:
                break;
        }
        
        plugin.setCurrentState("Combat: " + currentState.toString());
    }

    // Event handlers
    public void onAnimationChanged(AnimationChanged animationChanged) {
        if (animationChanged.getActor() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        // Can be used to detect attack animations or other combat-related animations
    }

    public void onInteractingChanged(InteractingChanged interactingChanged) {
        if (interactingChanged.getSource() != plugin.getClient().getLocalPlayer()) {
            return;
        }
        
        if (currentState == CombatState.ATTACKING) {
            Actor target = interactingChanged.getTarget();
            if (target == null) {
                // Player stopped attacking
                log.info("Player stopped attacking, transitioning to waiting for combat end");
                currentState = CombatState.WAITING_FOR_COMBAT_END;
                combatStartTicks = 0;
            }
        }
    }

    // --- FSM LOGIC ---
    
    private void setRandomDelay(int minTicks, int maxTicks) {
        delayTicks = plugin.getRandom().nextInt(maxTicks - minTicks + 1) + minTicks;
    }

    private void doFindingNpc() {
        // Get NPC names to target from config
        String[] npcNames = getNpcNamesFromConfig();
        if (npcNames.length == 0) {
            log.warn("No NPC names configured for combat");
            return;
        }
        
        // Find nearest valid NPC to attack
        Interactable selectedEntity = gameService.findNearest(interactable -> {
            if (!(interactable instanceof NpcEntity)) {
                return false;
            }
            
            NpcEntity npcEntity = (NpcEntity) interactable;
            NPC npc = npcEntity.getNpc();
            
            // Check if NPC name is null
            if (npc.getName() == null) {
                return false;
            }
            
            // Check if NPC name matches our target list (using config instead of hardcoded "Goblin")
            boolean nameMatches = Arrays.stream(npcNames)
                    .anyMatch(targetName -> npc.getName().toLowerCase().contains(targetName.toLowerCase().trim()));
            if (!nameMatches) {
                return false;
            }
            
            // Only exclude NPCs that are definitely dead (health ratio exactly 0 AND in combat)
            // NPCs not in combat will have health ratio 0, but they're still alive and targetable
            if (npc.getHealthRatio() == 0 && npc.getInteracting() != null) {
                return false; // NPC is dead (health 0 while in combat)
            }
            
            // Skip NPCs already in combat with another player (not us)
            if (npc.getInteracting() != null && npc.getInteracting() != plugin.getClient().getLocalPlayer()) {
                return false;
            }
            
            return true;
        });

        if (selectedEntity == null) {
            log.debug("No valid NPCs found, waiting...");
            targetNpc = null;
            return;
        }
        
        targetNpc = ((NpcEntity) selectedEntity).getNpc();
        
        if (targetNpc == null) {
            log.debug("Selected entity had null NPC, waiting...");
            return;
        }

        log.debug("Found target NPC: {} at {}", targetNpc.getName(), targetNpc.getWorldLocation());
        
        // Set target NPC for overlay debugging
        plugin.setTargetNpc(targetNpc);
        
        // Get clickable point and attack using the new unified approach
        Point clickPoint = gameService.getRandomClickablePoint(selectedEntity);
        if (clickPoint != null && clickPoint.x != -1 && clickPoint.y != -1) {
            actionService.sendClickRequest(clickPoint, true);
            currentState = CombatState.VERIFY_ATTACK;
            waitToVerifyTicks = 5;
            combatStartTicks = 0;
        } else {
            log.warn("Found {} at {} but could not get clickable point for NPC.", targetNpc.getName(), targetNpc.getWorldLocation());
        }
    }

    private void doVerifyAttack() {
        waitToVerifyTicks--;

        if (plugin.getClient().getLocalPlayer().getInteracting() != null) {
            currentState = CombatState.ATTACKING;
            waitToVerifyTicks = 0;
            return;
        }
        if (waitToVerifyTicks <= 0) {
            log.warn("Attacking did not start after 5 ticks. Finding new target.");
            currentState = CombatState.FINDING_NPC;
            waitToVerifyTicks = 0;
        }
    }

    private void doAttacking() {
        if (targetNpc == null) {
            currentState = CombatState.FINDING_NPC;
            return;
        }

        Player localPlayer = plugin.getClient().getLocalPlayer();
        
        // Check if we're still in combat
        if (localPlayer.getInteracting() == null) {
            // Not attacking anyone, check if NPC is dead or moved
            if (targetNpc.getHealthRatio() == 0) {
                log.info("Target NPC defeated");
                currentState = CombatState.LOOTING;
                setRandomDelay(2, 3);
            } else {
                // Lost target, find new one
                log.info("Lost target, finding new NPC");
                currentState = CombatState.FINDING_NPC;
                setRandomDelay(1, 3);
            }
        } else if (localPlayer.getInteracting() == targetNpc) {
            // Still attacking the target, wait
            combatStartTicks++;
            if (combatStartTicks > 100) { // ~60 seconds timeout
                log.warn("Combat taking too long, finding new target");
                currentState = CombatState.FINDING_NPC;
                targetNpc = null;
            }
        }
    }

    private void doEating() {
        Point foodPoint = findFoodInInventory();
        
        if (foodPoint == null) {
            log.warn("No food found in inventory, continuing without eating");
            currentState = CombatState.FINDING_NPC;
            return;
        }

        log.info("Eating food at point: {}", foodPoint);
        actionService.sendClickRequest(foodPoint, false);
        
        // Wait a bit for eating animation
        setRandomDelay(3, 5);
        
        // After eating, continue with previous activity
        if (targetNpc != null && targetNpc.getHealthRatio() > 0) {
            currentState = CombatState.ATTACKING;
        } else {
            currentState = CombatState.FINDING_NPC;
        }
    }

    private void doLooting() {
        // TODO: Implement basic loot detection and pickup
        // For now, just wait a bit and look for new targets
        log.debug("Looting phase - waiting for loot to appear");
        setRandomDelay(3, 5);
        currentState = CombatState.FINDING_NPC;
        targetNpc = null;
        plugin.setTargetNpc(null); // Clear overlay
    }

    private void doWaitingForCombatEnd() {
        combatStartTicks++;
        
        // Wait a few ticks for combat to fully end
        if (combatStartTicks > 5) {
            currentState = CombatState.LOOTING;
            combatStartTicks = 0;
        }
    }

    // --- HELPER METHODS ---

    private boolean shouldEat() {
        int currentHp = plugin.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = plugin.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int healthPercentage = (currentHp * 100) / maxHp;
        
        return healthPercentage <= config.combatEatAtHealthPercent();
    }

    private String[] getNpcNamesFromConfig() {
        String npcNamesStr = config.combatNpcNames();
        if (npcNamesStr == null || npcNamesStr.trim().isEmpty()) {
            return new String[0];
        }
        return npcNamesStr.split(",");
    }

    private Point findFoodInInventory() {
        for (int slot = 0; slot < 28; slot++) {
            int itemId = gameService.getInventoryItemId(slot);
            if (Arrays.stream(FOOD_IDS).anyMatch(id -> id == itemId)) {
                return gameService.getInventoryItemPoint(slot);
            }
        }
        return null;
    }


} 