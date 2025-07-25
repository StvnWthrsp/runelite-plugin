package com.runepal;

import com.runepal.services.*;
import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.*;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.util.Arrays;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.OverlayManager;
import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@PluginDescriptor(
	name = "Runepal",
	description = "Runepal automation platform"
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
	@Getter
	private PotionService potionService = null;
	@Getter
	private PrayerService prayerService = null;
	@Getter
	private SupplyManager supplyManager = null;

	// Debugging and tracking variables
	@Getter
	private GameObject targetRock = null;
	@Getter
	private GameObject targetTree = null;
	@Setter
	@Getter
	private NPC targetNpc = null;
	private long sessionStartXp = 0;
	private Instant sessionStartTime = null;

	@Inject
	private MouseIndicatorOverlay mouseIndicatorOverlay;

	@Inject
	private MiningBotRockOverlay rockOverlay;
	
	@Getter
	@Inject
	private MenuDebugOverlay menuDebugOverlay;

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
		overlayManager.add(menuDebugOverlay);

		// Initialize core services
		eventService = new EventService();
		humanizerService = new HumanizerService();
		
		// Initialize game services in correct dependency order
		GameStateService gameStateService = new GameStateService(client);
		EntityService entityService = new EntityService(client, gameStateService);
		ClickService clickService = new ClickService();
		UtilityService utilityService = new UtilityService(client);
		WindmouseService windMouseService = new WindmouseService(this, eventService, config);
		
		gameService = new GameService(gameStateService, entityService, clickService, utilityService);
		actionService = new ActionService(this, pipeService, gameService, eventService, config, windMouseService);
		
		// Initialize combat-specific services
		potionService = new PotionService(client, gameService, actionService, humanizerService);
		prayerService = new PrayerService(client, actionService, humanizerService);
		supplyManager = new SupplyManager(client, gameService, potionService, config);

		pathfinderConfig = new PathfinderConfig(client, config);

		// Don't initialize pipe service automatically - user must click Connect
		log.info("Runepal initialized. Use the 'Connect' button to connect to the RemoteInput server.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Runepal shut down!");
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(mouseIndicatorOverlay);
		overlayManager.remove(rockOverlay);
		overlayManager.remove(statusOverlay);
		overlayManager.remove(inventoryOverlay);
		overlayManager.remove(combatNpcOverlay);
		taskManager.clearTasks();

		// Clean up services
		if (eventService != null) {
			eventService.clearAllSubscribers();
		}
		if (potionService != null) {
			potionService.shutdown();
		}
		if (prayerService != null) {
			prayerService.shutdown();
		}

		// Kill the client if using RemoteInput
		if (isAutomationConnected()) {
			pipeService.sendExit();
		}
		
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			log.info("Runepal is running - player logged in.");
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
		if (eventService != null) {
			eventService.publish(gameStateChanged);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged) {
		if (eventService != null) {
			eventService.publish(animationChanged);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		if (eventService != null) {
			eventService.publish(statChanged);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged) {
		if (eventService != null) {
			eventService.publish(interactingChanged);
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
		if (eventService != null) {
			eventService.publish(gameTick);
		}

		if (panel != null) {
			panel.setStatus(currentState);
			panel.setButtonText(config.startBot() ? "Stop" : "Start");
			panel.updateConnectionStatus();
		}

		boolean isRunning = config.startBot();

		if (isRunning && !wasRunning) {
			if (!isAutomationConnected()) {
				log.warn("Automation server not connected. RemoteInput will be disabled. Please click 'Connect' first.");
				// stopBot();
				// return;
			}
			log.info("Bot starting...");

			// Start the appropriate task based on bot type
			BotType botType = config.botType();
			switch (botType) {
				case MINING_BOT:
					taskManager.pushTask(new MiningTask(this, config, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService));
					break;
				case COMBAT_BOT:
					taskManager.pushTask(new CombatTask(this, config, taskManager, actionService, gameService, eventService, humanizerService, potionService));
					break;
				case FISHING_BOT:
					taskManager.pushTask(new FishingTask(this, config, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService));
					break;
				case WOODCUTTING_BOT:
					taskManager.pushTask(new WoodcuttingTask(this, config, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService));
					break;
				case SAND_CRAB_BOT:
					taskManager.pushTask(new SandCrabTask(this, config, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService, potionService, supplyManager));
					break;
				case GEMSTONE_CRAB_BOT:
					taskManager.pushTask(new GemstoneCrabTask(this, config, taskManager, actionService, gameService, eventService, humanizerService));
					break;
				default:
					log.warn("Unknown bot type: {}", botType);
					stopBot();
					return;
			}

			wasRunning = true;
		}

		if (!isRunning && wasRunning) {
			log.info("Bot stopping...");
			taskManager.clearTasks();
			currentState = "IDLE";
			wasRunning = false;
		}

		if (isRunning) {
			taskManager.onLoop();
		}
	}

	@Subscribe
	public void onClientTick(ClientTick clientTick) {
		eventService.publish(clientTick);
	}

	public void stopBot() {
		configManager.setConfiguration("runepal", "startBot", false);
		taskManager.clearTasks();
	}

	// --- Public Helper Methods for Tasks ---
	// Use for configuration dependent logic that might be shared
	// between multiple bots.

	public int[] getRockIds() {
		String[] rockTypes = config.rockTypes().split(",");
		List<Integer> idList = new ArrayList<>();
		for (String rockType : rockTypes) {
			switch (rockType) {
				case "Copper":
					idList.addAll(RockOres.COPPER.getRockIds());
					break;
				case "Tin":
					idList.addAll(RockOres.TIN.getRockIds());
					break;
				case "Iron":
					idList.addAll(RockOres.IRON.getRockIds());
					break;
				case "Coal":
					idList.addAll(RockOres.COAL.getRockIds());
					break;
				case "Mithril":
					idList.addAll(RockOres.MITHRIL.getRockIds());
					break;
				case "Adamantite":
					idList.addAll(RockOres.ADAMANTITE.getRockIds());
					break;
				case "Runite":
					idList.addAll(RockOres.RUNITE.getRockIds());
					break;
			}
		}
		return idList.stream().mapToInt(Integer::intValue).toArray();
	}

	public int[] getOreIds() {
		// A simple map from rock ID to ore ID. This could be improved.
		return Arrays.stream(getRockIds())
				.map(RockOres::getOreForRock)
				.filter(oreId -> oreId != -1)
				.toArray();
	}

	public int[] getTreeIds() {
		String[] treeTypes = config.treeTypes().split(",");
		List<Integer> idList = new ArrayList<>();
		for (String treeType : treeTypes) {
			String trimmedType = treeType.trim();
			switch (trimmedType) {
				case "Tree":
					idList.addAll(TreeTypes.TREE.getTreeIds());
					break;
				case "Oak":
					idList.addAll(TreeTypes.OAK.getTreeIds());
					break;
				case "Willow":
					idList.addAll(TreeTypes.WILLOW.getTreeIds());
					break;
				case "Maple":
					idList.addAll(TreeTypes.MAPLE.getTreeIds());
					break;
				case "Yew":
					idList.addAll(TreeTypes.YEW.getTreeIds());
					break;
				case "Magic":
					idList.addAll(TreeTypes.MAGIC.getTreeIds());
					break;
				case "Teak":
					idList.addAll(TreeTypes.TEAK.getTreeIds());
					break;
				case "Mahogany":
					idList.addAll(TreeTypes.MAHOGANY.getTreeIds());
					break;
			}
		}
		return idList.stream().mapToInt(Integer::intValue).toArray();
	}

	public int[] getLogIds() {
		// A simple map from tree ID to log ID.
		return Arrays.stream(getTreeIds())
				.map(TreeTypes::getLogForTree)
				.filter(logId -> logId != -1)
				.toArray();
	}

	public WorldPoint getBankCoordinates() {
		String bankName = config.miningBank();
		log.info("Bank name: {}", bankName);
		switch (bankName) {
			case "VARROCK_EAST":
				return Banks.VARROCK_EAST.getBankCoordinates();
			case "VARROCK_WEST":
				return Banks.VARROCK_WEST.getBankCoordinates();
			case "INTERIOR_TEST":
				return Banks.INTERIOR_TEST.getBankCoordinates();
			case "HUNTER_GUILD":
				return Banks.HUNTER_GUILD.getBankCoordinates();
			default:
				return null;
		}
	}

	// --- Public Getters/Setters for Panel and Overlays ---
	// Tasks do not have direct access to the Panels/Overlay, so we
	// set variables inside the plugin class to communicate that info.

	public void setTargetRock(GameObject rock) {
		this.targetRock = rock;
		rockOverlay.setTarget(rock);
	}

	public void setTargetTree(GameObject tree) {
		this.targetTree = tree;
		// TODO: Add tree overlay when implemented
		// treeOverlay.setTarget(tree);
	}

	public long getSessionXpGained()
	{
		if (sessionStartTime == null) return 0;
		return client.getSkillExperience(Skill.MINING) - sessionStartXp;
	}

	public Duration getSessionRuntime()
	{
		if (sessionStartTime == null) return Duration.ZERO;
		return Duration.between(sessionStartTime, Instant.now());
	}

	public String getXpPerHour() {
		Duration runtime = getSessionRuntime();
		long xpGained = getSessionXpGained();
		if (runtime.isZero() || xpGained == 0) {
			return "0";
		}
		long seconds = runtime.getSeconds();
		double xpPerSecond = (double) xpGained / seconds;
		long xpPerHour = (long) (xpPerSecond * 3600);
		return String.format("%,d", xpPerHour);
	}

	public boolean isAutomationConnected()
	{
		return pipeService.isConnected();
	}

	public boolean connectAutomation()
	{
		if (gameService == null || actionService == null) {
			log.error("Unable to connect because plugin failed to initialize.");
			return false;
		}
		try {
			if (pipeService.connect()) {
				// After connecting, send a "connect" command to the Python server
				// to initialize the RemoteInput client.
				if (pipeService.sendConnect()) {
					log.info("Successfully connected and initialized automation server.");
					panel.updateConnectionStatus();
					return true;
				} else {
					log.error("Connected to pipe, but failed to send connect command.");
					pipeService.disconnect();
					panel.updateConnectionStatus();
					return false;
				}
			} else {
				log.error("Failed to establish connection with automation server.");
				panel.updateConnectionStatus();
				return false;
			}
		} catch (Exception e) {
			log.error("Error connecting to automation server: {}", e.getMessage(), e);
			pipeService.disconnect();
			panel.updateConnectionStatus();
			return false;
		}
	}

	public boolean reconnectAutomation()
	{
		log.info("Attempting to reconnect to the automation server...");
		pipeService.disconnect();
		return connectAutomation();
	}
} 