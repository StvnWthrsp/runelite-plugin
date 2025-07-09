package com.example;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.*;

import com.example.services.GameStateService;
import com.example.services.EntityService;
import com.example.services.ClickService;
import com.example.services.UtilityService;
import com.example.core.PluginCoordinator;
import com.example.core.SessionTracker;
import com.example.core.ConfigurationHelper;
import com.example.core.EventManager;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.OverlayManager;
import shortestpath.ShortestPathConfig;
import shortestpath.Transport;
import shortestpath.pathfinder.PathfinderConfig;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@PluginDescriptor(
	name = "Runepal"
)
public class RunepalPlugin extends Plugin
{
	@Getter
	@Inject
	private Client client;

	@Inject
	private PipeService pipeService;
	@Getter
    private final Random random = new Random();
	@Getter
    @Setter
    private String currentState = "IDLE";
	private BotPanel panel;
	private NavigationButton navButton;
	private boolean wasRunning = false;
	private final TaskManager taskManager = new TaskManager();
	private PathfinderConfig pathfinderConfig;
	@Getter
	private ActionService actionService = null;
	@Getter
	private GameService gameService = null;
	@Getter
	private HumanizerService humanizerService = null;
	@Getter
	private EventService eventService = null;

	// Core coordinator services
	private PluginCoordinator pluginCoordinator;
	private SessionTracker sessionTracker;
	private ConfigurationHelper configHelper;
	private EventManager eventManager;

	// Debugging and tracking variables
	@Getter
	private GameObject targetRock = null;
	@Setter
	@Getter
	private NPC targetNpc = null;

	@Inject
	private MouseIndicatorOverlay mouseIndicatorOverlay;

	@Inject
	private MiningBotRockOverlay rockOverlay;

	@Inject
	private MiningBotStatusOverlay statusOverlay;

	@Inject
	private MiningBotInventoryOverlay inventoryOverlay;

	@Inject
	private CombatBotNpcOverlay combatNpcOverlay;

	@Inject
	private BotConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Runepal started!");
		this.currentState = "IDLE";
		panel = new BotPanel(this, config, configManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/images/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Runepal")
			.icon(icon)
			.priority(1)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// Register overlays
		overlayManager.add(mouseIndicatorOverlay);
		overlayManager.add(rockOverlay);
		overlayManager.add(statusOverlay);
		overlayManager.add(inventoryOverlay);
		overlayManager.add(combatNpcOverlay);

		// Initialize core services
		eventService = new EventService();
		humanizerService = new HumanizerService();
		
		// Initialize game services in correct dependency order
		GameStateService gameStateService = new GameStateService(client);
		EntityService entityService = new EntityService(client, gameStateService);
		ClickService clickService = new ClickService();
		UtilityService utilityService = new UtilityService(client);
		
		gameService = new GameService(gameStateService, entityService, clickService, utilityService);
		actionService = new ActionService(this, pipeService, gameService);

		ShortestPathConfig shortestPathConfig = configManager.getConfig(ShortestPathConfig.class);
		pathfinderConfig = new PathfinderConfig(client, shortestPathConfig);
		if (client.getGameState() == GameState.LOGGED_IN) {
			pathfinderConfig.refresh();
		}

		// Initialize coordinator services
		sessionTracker = new SessionTracker(client);
		configHelper = new ConfigurationHelper(config);
		eventManager = new EventManager(eventService);
		pluginCoordinator = new PluginCoordinator(taskManager, config, configManager, this, 
				pipeService, actionService, gameService, eventService, humanizerService, pathfinderConfig);

		// Initialize session tracking
		sessionTracker.initializeSession();

		// Don't initialize pipe service automatically - user must click Connect
		log.info("Runepal initialized. Use the 'Connect' button to connect to the automation server.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("General Bot stopped!");
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(mouseIndicatorOverlay);
		overlayManager.remove(rockOverlay);
		overlayManager.remove(statusOverlay);
		overlayManager.remove(inventoryOverlay);
		overlayManager.remove(combatNpcOverlay);
		taskManager.clearTasks();

		// Clean up services
		if (eventManager != null) {
			eventManager.clearAllSubscribers();
		}

		// Disconnect from pipe service
		pipeService.disconnect();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			log.info("Runepal is running - player logged in.");

			if (pathfinderConfig != null) {
				pathfinderConfig.refresh();
			}

			// Initialize session tracking if not already done
			if (sessionTracker != null) {
				sessionTracker.initializeSession();
			}
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			log.info("Player logged out - gracefully stopping bot.");
			
			// Gracefully stop the bot when player logs out
			if (config.startBot()) {
				stopBot();
				log.info("Bot stopped due to logout.");
			}
		}
		
		// Publish game state change event
		if (eventManager != null) {
			eventManager.onGameStateChanged(gameStateChanged);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged) {
		if (eventManager != null) {
			eventManager.onAnimationChanged(animationChanged);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		if (eventManager != null) {
			eventManager.onStatChanged(statChanged);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged) {
		if (eventManager != null) {
			eventManager.onInteractingChanged(interactingChanged);
		}
	}

	@Provides
	BotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BotConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		// Publish game tick event to the event service
		if (eventManager != null) {
			eventManager.onGameTick(gameTick);
		}

		// Update UI
		updateUI();

		// Delegate to coordinator
		if (pluginCoordinator != null) {
			pluginCoordinator.handleGameTick();
			currentState = pluginCoordinator.getCurrentState();
		}
	}

	public void stopBot() {
		if (pluginCoordinator != null) {
			pluginCoordinator.stopBot();
		} else {
			configManager.setConfiguration("runepal", "startBot", false);
		}
	}

	/**
	 * Update the UI panel with current status.
	 */
	private void updateUI() {
		if (panel != null) {
			panel.setStatus(currentState);
			panel.setButtonText(config.startBot() ? "Stop" : "Start");
			panel.updateConnectionStatus();
		}
	}

	// --- Public Helper Methods for Tasks ---
	// Use for configuration dependent logic that might be shared
	// between multiple bots.

	public int[] getRockIds() {
		return configHelper != null ? configHelper.getRockIds() : new int[0];
	}

	public int[] getOreIds() {
		return configHelper != null ? configHelper.getOreIds() : new int[0];
	}

	public WorldPoint getBankCoordinates() {
		return configHelper != null ? configHelper.getBankCoordinates() : null;
	}

	// --- Public Getters/Setters for Panel and Overlays ---
	// Tasks do not have direct access to the Panels/Overlay, so we
	// set variables inside the plugin class to communicate that info.

	public void setTargetRock(GameObject rock) {
		this.targetRock = rock;
		rockOverlay.setTarget(rock);
	}

	public long getSessionXpGained() {
		return sessionTracker != null ? sessionTracker.getSessionXpGained() : 0;
	}

	public Duration getSessionRuntime() {
		return sessionTracker != null ? sessionTracker.getSessionRuntime() : Duration.ZERO;
	}

	public String getXpPerHour() {
		return sessionTracker != null ? sessionTracker.getXpPerHour() : "0";
	}

	public boolean isAutomationConnected() {
		return pluginCoordinator != null ? pluginCoordinator.isAutomationConnected() : pipeService.isConnected();
	}

	public boolean connectAutomation() {
		if (pluginCoordinator != null) {
			boolean connected = pluginCoordinator.connectAutomation();
			if (panel != null) {
				panel.updateConnectionStatus();
			}
			return connected;
		}
		
		// Fallback to old logic if coordinator not available
		try {
			if (gameService == null) {
				// Initialize game services in correct dependency order
				GameStateService gameStateService = new GameStateService(client);
				EntityService entityService = new EntityService(client, gameStateService);
				ClickService clickService = new ClickService();
				UtilityService utilityService = new UtilityService(client);
				
				gameService = new GameService(gameStateService, entityService, clickService, utilityService);
			}
			if (actionService == null) {
				actionService = new ActionService(this, pipeService, gameService);
			}
			if (pipeService.connect()) {
				if (pipeService.sendConnect()) {
					log.info("Successfully connected and initialized automation server.");
					if (panel != null) panel.updateConnectionStatus();
					return true;
				} else {
					log.error("Connected to pipe, but failed to send connect command.");
					pipeService.disconnect();
					if (panel != null) panel.updateConnectionStatus();
					return false;
				}
			} else {
				log.error("Failed to establish connection with automation server.");
				if (panel != null) panel.updateConnectionStatus();
				return false;
			}
		} catch (Exception e) {
			log.error("Error connecting to automation server: {}", e.getMessage(), e);
			pipeService.disconnect();
			if (panel != null) panel.updateConnectionStatus();
			return false;
		}
	}

	public boolean reconnectAutomation() {
		return pluginCoordinator != null ? pluginCoordinator.reconnectAutomation() : connectAutomation();
	}

	public Map<Integer, Set<Transport>> getTransports() {
		return pathfinderConfig.getTransports();
	}
} 