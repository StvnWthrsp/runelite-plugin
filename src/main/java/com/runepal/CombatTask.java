package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.InteractingChanged;

import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import com.runepal.entity.Interactable;
import com.runepal.entity.NpcEntity;

import java.util.Arrays;

@Slf4j
public class CombatTask implements BotTask {

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final TaskManager taskManager;
    private ScheduledExecutorService scheduler;
    private final GameService gameService;
    private final ActionService actionService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private final PotionService potionService;

    // Event handler references to maintain identity
    private Consumer<AnimationChanged> animationHandler;
    private Consumer<InteractingChanged> interactingHandler;

    // Internal state for combat FSM
    private enum CombatState {
        IDLE,
        FINDING_NPC,
        VERIFY_ATTACK,
        ATTACKING,
        EATING,
        DRINKING_POTION,
        LOOTING,
        WAITING_FOR_COMBAT_END
    }

    private CombatState currentState;
    private NPC targetNpc = null;
    private int delayTicks = 0;
    private int combatStartTicks = 0;
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

    public CombatTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, ActionService actionService, GameService gameService, EventService eventService, HumanizerService humanizerService, PotionService potionService) {
        this.plugin = plugin;
        this.config = config;
        this.taskManager = taskManager;
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.potionService = Objects.requireNonNull(potionService, "potionService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting Combat Task.");
        this.currentState = CombatState.FINDING_NPC;
        
        // Store event handler references to maintain identity
        this.animationHandler = this::onAnimationChanged;
        this.interactingHandler = this::onInteractingChanged;
        
        // Subscribe to events
        this.eventService.subscribe(AnimationChanged.class, animationHandler);
        this.eventService.subscribe(InteractingChanged.class, interactingHandler);
        
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
    }

    @Override
    public void onStop() {
        log.info("Stopping Combat Task.");
        this.targetNpc = null;
        plugin.setTargetNpc(null); // Clear overlay
        
        // Unsubscribe using the stored handler references
        this.eventService.unsubscribe(AnimationChanged.class, animationHandler);
        this.eventService.unsubscribe(InteractingChanged.class, interactingHandler);
        
        // Clear handler references
        this.animationHandler = null;
        this.interactingHandler = null;
        
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

        // Check critical needs first, regardless of current state
        if (shouldEat()) {
            if (currentState != CombatState.EATING) {
                log.info("Health is low, switching to eating state");
                currentState = CombatState.EATING;
            }
        } else if (shouldDrinkPotion()) {
            if (currentState != CombatState.DRINKING_POTION) {
                log.info("Need potion, switching to drinking potion state");
                currentState = CombatState.DRINKING_POTION;
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
            case DRINKING_POTION:
                doDrinkingPotion();
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

        Actor target = interactingChanged.getTarget();
        if (target == null) {
            log.info("Player stopped interacting");
        } else {
            log.info("Player began interacting with {}", target);
        }

        if (currentState == CombatState.VERIFY_ATTACK) {
            log.info("Verified attacking started");
            currentState = CombatState.ATTACKING;
            waitToVerifyTicks = 0;
            return;
        }
        
        if (currentState == CombatState.ATTACKING) {
            if (target == null) {
                // Player stopped attacking
                log.info("Player stopped attacking, transitioning to waiting for combat end");
                currentState = CombatState.WAITING_FOR_COMBAT_END;
                combatStartTicks = 0;
            }
        }
    }

    // --- FSM LOGIC ---
    

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
        
        // Use enhanced interaction system
        log.info("Attacking NPC {} using enhanced interaction system", targetNpc.getName());
        actionService.interactWithEntity(selectedEntity, "Attack");
        
        currentState = CombatState.VERIFY_ATTACK;
        waitToVerifyTicks = 5;
        combatStartTicks = 0;
    }

    private void doVerifyAttack() {
        waitToVerifyTicks--;
        
//        Player localPlayer = plugin.getClient().getLocalPlayer();
//
//        // Check if we started attacking
//        if (localPlayer.getInteracting() == targetNpc) {
//            log.info("Successfully started attacking {}", targetNpc.getName());
//            currentState = CombatState.ATTACKING;
//            waitToVerifyTicks = 0;
//            return;
//        }
//
//        // Check if we're attacking someone else (acceptable)
//        if (localPlayer.getInteracting() != null) {
//            log.info("Started attacking different target, updating targetNpc");
//            targetNpc = (NPC) localPlayer.getInteracting();
//            plugin.setTargetNpc(targetNpc);
//            currentState = CombatState.ATTACKING;
//            waitToVerifyTicks = 0;
//            return;
//        }
        
        if (waitToVerifyTicks <= 0) {
            log.warn("Attack verification failed, retrying");
            currentState = CombatState.FINDING_NPC;
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
                delayTicks = humanizerService.getRandomDelay(2, 3);
            } else {
                // Lost target, find new one
                log.info("Lost target, finding new NPC");
                currentState = CombatState.FINDING_NPC;
                delayTicks = humanizerService.getRandomDelay(1, 3);
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
        delayTicks = humanizerService.getRandomDelay(3, 5);
        
        // After eating, continue with previous activity
        if (targetNpc != null && targetNpc.getHealthRatio() > 0) {
            currentState = CombatState.ATTACKING;
        } else {
            currentState = CombatState.FINDING_NPC;
        }
    }

    private void doDrinkingPotion() {
        PotionService.PotionType potionToConsume = null;
        
        // Determine which potion to consume (priority order)
        if (potionService.needsPrayerPotion(config.combatPrayerPotionThreshold()) && 
            potionService.hasPotion(PotionService.PotionType.PRAYER_POTION)) {
            potionToConsume = PotionService.PotionType.PRAYER_POTION;
        } else if (config.combatUseCombatPotions() && 
                   potionService.needsCombatPotion() && 
                   potionService.hasPotion(PotionService.PotionType.SUPER_COMBAT)) {
            potionToConsume = PotionService.PotionType.SUPER_COMBAT;
        } else if (config.combatUseAntipoison() && 
                   potionService.needsAntipoison() && 
                   potionService.hasPotion(PotionService.PotionType.ANTIPOISON)) {
            potionToConsume = PotionService.PotionType.ANTIPOISON;
        }
        
        if (potionToConsume == null) {
            log.warn("No suitable potion found, continuing without drinking");
            currentState = CombatState.FINDING_NPC;
            return;
        }

        log.info("Consuming {} potion", potionToConsume);
        boolean consumed = potionService.consumePotion(potionToConsume);
        
        if (consumed) {
            // Wait for potion consumption animation
            delayTicks = humanizerService.getRandomDelay(3, 5);
        }
        
        // After drinking potion, continue with previous activity
        if (targetNpc != null && targetNpc.getHealthRatio() > 0) {
            currentState = CombatState.ATTACKING;
        } else {
            currentState = CombatState.FINDING_NPC;
        }
    }

    private void doLooting() {
        if (!config.combatAutoLoot()) {
            // Auto-loot disabled, skip looting
            log.debug("Auto-loot disabled, skipping loot collection");
            delayTicks = humanizerService.getRandomDelay(1, 3);
            currentState = CombatState.FINDING_NPC;
            targetNpc = null;
            plugin.setTargetNpc(null);
            return;
        }

        // Check if inventory has space for loot
        if (gameService.isInventoryFull()) {
            log.debug("Inventory full, skipping loot collection");
            currentState = CombatState.FINDING_NPC;
            targetNpc = null;
            plugin.setTargetNpc(null);
            return;
        }

        // Find valuable loot on the ground (simplified implementation)
        boolean foundLoot = hasValuableLootNearby();
        
        if (!foundLoot) {
            log.debug("No valuable loot found, continuing to next target");
            delayTicks = humanizerService.getRandomDelay(2, 4);
            currentState = CombatState.FINDING_NPC;
            targetNpc = null;
            plugin.setTargetNpc(null);
            return;
        }

        log.info("Loot detection enabled - performing basic loot scan");
        // TODO: Implement proper ground item detection and collection
        
        // Continue to next target after looting attempt
        currentState = CombatState.FINDING_NPC;
        targetNpc = null;
        plugin.setTargetNpc(null);
    }

    private boolean hasValuableLootNearby() {
        // Simplified loot detection - always returns false for now
        // TODO: Implement proper ground item detection using RuneLite API
        // This would involve:
        // 1. Scanning tiles around the player for ground items
        // 2. Checking item names against whitelist
        // 3. Evaluating item values against threshold
        // 4. Prioritizing closest valuable items
        
        log.debug("Loot scanning enabled but not yet fully implemented");
        return false; // Always returns false for now - safe default
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

    private boolean shouldDrinkPotion() {
        // Check if prayer restoration is needed (highest priority)
        if (potionService.needsPrayerPotion(config.combatPrayerPotionThreshold()) && 
            potionService.hasPotion(PotionService.PotionType.PRAYER_POTION)) {
            return true;
        }
        
        // Check if combat boost is needed
        if (config.combatUseCombatPotions() && 
            potionService.needsCombatPotion() && 
            potionService.hasPotion(PotionService.PotionType.SUPER_COMBAT)) {
            return true;
        }
        
        // Check if antipoison is needed
        if (config.combatUseAntipoison() && 
            potionService.needsAntipoison() && 
            potionService.hasPotion(PotionService.PotionType.ANTIPOISON)) {
            return true;
        }
        
        return false;
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