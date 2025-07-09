package com.example.core;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

import com.example.BotConfig;
import com.example.BotType;
import com.example.TaskManager;
import com.example.RunepalPlugin;
import com.example.MiningTask;
import com.example.CombatTask;
import com.example.FishingTask;
import com.example.ActionService;
import com.example.GameService;
import com.example.EventService;
import com.example.HumanizerService;
import com.example.PipeService;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.Objects;

/**
 * Coordinates the overall bot execution and task management.
 * Handles starting/stopping bots and managing the task lifecycle.
 */
@Singleton
@Slf4j
public class PluginCoordinator {
    private final TaskManager taskManager;
    private final BotConfig config;
    private final ConfigManager configManager;
    private final RunepalPlugin plugin;
    private final PipeService pipeService;
    
    // Services needed for task creation
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private final PathfinderConfig pathfinderConfig;
    
    private boolean wasRunning = false;
    private String currentState = "IDLE";

    public PluginCoordinator(TaskManager taskManager, BotConfig config, ConfigManager configManager,
                           RunepalPlugin plugin, PipeService pipeService, ActionService actionService,
                           GameService gameService, EventService eventService, HumanizerService humanizerService,
                           PathfinderConfig pathfinderConfig) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.pipeService = Objects.requireNonNull(pipeService, "pipeService cannot be null");
        this.actionService = actionService;
        this.gameService = gameService;
        this.eventService = eventService;
        this.humanizerService = humanizerService;
        this.pathfinderConfig = pathfinderConfig;
    }

    /**
     * Handle the main game tick for bot coordination.
     */
    public void handleGameTick() {
        boolean isRunning = config.startBot();

        if (isRunning && !wasRunning) {
            startBot();
            wasRunning = true;
        }

        if (!isRunning && wasRunning) {
            stopBot();
            wasRunning = false;
        }

        if (isRunning) {
            taskManager.onLoop();
        }
    }

    /**
     * Start the bot with the configured bot type.
     */
    public void startBot() {
        if (!isAutomationConnected()) {
            log.warn("Cannot start bot: Automation server not connected. Please click 'Connect' first.");
            stopBot();
            return;
        }
        
        log.info("Bot starting...");

        // Start the appropriate task based on bot type
        BotType botType = config.botType();
        switch (botType) {
            case MINING_BOT:
                taskManager.pushTask(new MiningTask(plugin, config, taskManager, pathfinderConfig, 
                        actionService, gameService, eventService, humanizerService));
                break;
            case COMBAT_BOT:
                taskManager.pushTask(new CombatTask(plugin, config, taskManager, 
                        actionService, gameService, eventService, humanizerService));
                break;
            case FISHING_BOT:
                taskManager.pushTask(new FishingTask(plugin, config, taskManager, pathfinderConfig, 
                        actionService, gameService, eventService, humanizerService));
                break;
            default:
                log.warn("Unknown bot type: {}", botType);
                stopBot();
                return;
        }
    }

    /**
     * Stop the bot and clear all tasks.
     */
    public void stopBot() {
        log.info("Bot stopping...");
        configManager.setConfiguration("runepal", "startBot", false);
        taskManager.clearTasks();
        currentState = "IDLE";
    }

    /**
     * Check if the automation server is connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isAutomationConnected() {
        return pipeService.isConnected();
    }

    /**
     * Connect to the automation server.
     * 
     * @return true if connection successful, false otherwise
     */
    public boolean connectAutomation() {
        try {
            if (pipeService.connect()) {
                // After connecting, send a "connect" command to the Python server
                // to initialize the RemoteInput client.
                if (pipeService.sendConnect()) {
                    log.info("Successfully connected and initialized automation server.");
                    return true;
                } else {
                    log.error("Connected to pipe, but failed to send connect command.");
                    pipeService.disconnect();
                    return false;
                }
            } else {
                log.error("Failed to establish connection with automation server.");
                return false;
            }
        } catch (Exception e) {
            log.error("Error connecting to automation server: {}", e.getMessage(), e);
            pipeService.disconnect();
            return false;
        }
    }

    /**
     * Reconnect to the automation server.
     * 
     * @return true if reconnection successful, false otherwise
     */
    public boolean reconnectAutomation() {
        log.info("Attempting to reconnect to the automation server...");
        pipeService.disconnect();
        return connectAutomation();
    }

    /**
     * Get the current state of the bot.
     * 
     * @return current state string
     */
    public String getCurrentState() {
        return currentState;
    }

    /**
     * Set the current state of the bot.
     * 
     * @param state the new state
     */
    public void setCurrentState(String state) {
        this.currentState = state;
    }

    /**
     * Check if the bot is currently running.
     * 
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return config.startBot();
    }
}