package com.runepal;

import com.runepal.runtime.SubscriptionBag;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class WorldHopTask implements BotTask {

    private final RunepalPlugin plugin;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private ScheduledExecutorService scheduler;

    private SubscriptionBag subscriptionBag;

    // Internal state for world hopping FSM
    private enum WorldHopState {
        IDLE,
        OPENING_WORLD_LIST,
        SELECTING_WORLD,
        WAITING_FOR_HOP,
        FINISHED
    }

    private WorldHopState currentState;
    private boolean isStarted = false;
    private boolean isFinished = false;
    private int delayTicks = 0;
    private int timeoutTicks = 0;
    private int targetWorld = -1;

    private static final int TIMEOUT_LIMIT = 100; // ~60 seconds
    public WorldHopTask(RunepalPlugin plugin,
                        EventService eventService,
                        HumanizerService humanizerService) {
        this.plugin = plugin;
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
    }

    @Override
    public void onStart() {
        log.info("Starting World Hop Task.");
        log.warn("WorldHopTask is experimental and may not handle all edge cases.");
        this.isStarted = true;
        this.currentState = WorldHopState.IDLE;
        
        this.subscriptionBag = new SubscriptionBag(eventService);
        subscriptionBag.subscribe(GameStateChanged.class, this::onGameStateChanged);
        subscriptionBag.subscribe(GameTick.class, this::onGameTick);
        
        // Initialize scheduler
        if (this.scheduler == null || this.scheduler.isShutdown()) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        // Select target world
        this.targetWorld = selectRandomWorld();
        log.info("Selected world {} for hopping", targetWorld);
        
        // Start the hopping process
        this.currentState = WorldHopState.OPENING_WORLD_LIST;
    }

    @Override
    public void onStop() {
        log.info("Stopping World Hop Task.");
        this.isStarted = false;
        
        if (subscriptionBag != null) {
            subscriptionBag.clear();
            subscriptionBag = null;
        }
        
        // Shutdown scheduler
        if (this.scheduler != null && !this.scheduler.isShutdown()) {
            this.scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isFinished() {
        return this.isFinished;
    }

    @Override
    public boolean isStarted() {
        return this.isStarted;
    }

    @Override
    public String getTaskName() {
        return "World Hop";
    }

    @Override
    public void onLoop() {
        // Process delays
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        
        // Check timeout
        timeoutTicks++;
        if (timeoutTicks > TIMEOUT_LIMIT) {
            log.warn("World hop task timed out, finishing");
            this.isFinished = true;
            return;
        }

        // FSM state machine
        switch (currentState) {
            case IDLE:
                doIdle();
                break;
            case OPENING_WORLD_LIST:
                doOpeningWorldList();
                break;
            case SELECTING_WORLD:
                doSelectingWorld();
                break;
            case WAITING_FOR_HOP:
                doWaitingForHop();
                break;
            case FINISHED:
                this.isFinished = true;
                break;
            default:
                log.warn("Unknown world hop state: {}", currentState);
                this.isFinished = true;
                break;
        }
        
        // Update UI
        plugin.setCurrentState("World Hop: " + currentState.toString());
    }

    // Event handlers
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState newState = gameStateChanged.getGameState();
        log.debug("Game state changed: {}", newState);
        
        if (newState == GameState.LOGGED_IN) {
            if (currentState == WorldHopState.WAITING_FOR_HOP) {
                log.info("Successfully hopped to world {}", plugin.getClient().getWorld());
                currentState = WorldHopState.FINISHED;
            }
        } else if (newState == GameState.HOPPING) {
            if (currentState == WorldHopState.SELECTING_WORLD) {
                log.info("World hop initiated, waiting for completion");
                currentState = WorldHopState.WAITING_FOR_HOP;
            }
        }
    }

    public void onGameTick(GameTick gameTick) {
        // General tick processing if needed
    }

    // --- FSM STATE IMPLEMENTATIONS ---

    private void doIdle() {
        log.debug("World hop task idle, starting hop process");
        currentState = WorldHopState.OPENING_WORLD_LIST;
    }

    private void doOpeningWorldList() {
        log.info("Opening world list interface");
        
        // This is a simplified implementation
        // In a real implementation, you would need to:
        // 1. Check if world list is already open
        // 2. Click the world hop button/interface
        // 3. Wait for the world list to appear
        
        // For now, we'll simulate opening the world list
        // and move directly to selecting world
        delayTicks = humanizerService.getRandomDelay(2, 4);
        currentState = WorldHopState.SELECTING_WORLD;
    }

    private void doSelectingWorld() {
        log.info("Selecting world {}", targetWorld);
        
        // This is a simplified implementation
        // In a real implementation, you would need to:
        // 1. Find the world in the world list
        // 2. Click on the specific world
        // 3. Handle any world restrictions (members, PvP, etc.)
        
        // For now, we'll use a basic world hop approach
        try {
            // Attempt to hop worlds using RuneLite's world utilities
            Client client = plugin.getClient();
            if (client.getGameState() == GameState.LOGGED_IN) {
                // This is a placeholder - actual world hopping would require
                // interfacing with the game's world selection interface
                log.info("Attempting to hop to world {}", targetWorld);
                
                // Simulate world hop delay
                delayTicks = humanizerService.getRandomDelay(5, 10);
                currentState = WorldHopState.WAITING_FOR_HOP;
            }
        } catch (Exception e) {
            log.error("Error during world hop: {}", e.getMessage());
            this.isFinished = true;
        }
    }

    private void doWaitingForHop() {
        log.debug("Waiting for world hop to complete...");
        
        // Check if we successfully changed worlds
        Client client = plugin.getClient();
        if (client.getGameState() == GameState.LOGGED_IN) {
            int currentWorld = client.getWorld();
            if (currentWorld == targetWorld) {
                log.info("Successfully hopped to world {}", currentWorld);
                currentState = WorldHopState.FINISHED;
            } else {
                log.debug("Still on world {}, waiting for hop to world {}", currentWorld, targetWorld);
            }
        }
    }

    // --- HELPER METHODS ---

    private int selectRandomWorld() {
        // This is a simplified world selection
        // In a real implementation, you would:
        // 1. Get list of available worlds
        // 2. Filter by membership status, region, etc.
        // 3. Avoid PvP worlds, tournament worlds, etc.
        // 4. Select a random world from the filtered list
        
        Client client = plugin.getClient();
        int currentWorld = client.getWorld();
        
        // Simple logic: just try a different world
        // This is a placeholder implementation
        int[] commonWorlds = {301, 302, 303, 304, 305, 306, 307, 308, 309, 310,
                             311, 312, 313, 314, 315, 316, 317, 318, 319, 320,
                             321, 322, 323, 324, 325, 326, 327, 328, 329, 330};
        
        // Select a random world different from current
        int selectedWorld;
        do {
            selectedWorld = commonWorlds[(int) (Math.random() * commonWorlds.length)];
        } while (selectedWorld == currentWorld);
        
        return selectedWorld;
    }
}
