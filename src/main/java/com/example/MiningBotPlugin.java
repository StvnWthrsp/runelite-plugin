package com.example;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.Skill;
import net.runelite.api.AnimationID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import net.runelite.client.task.Schedule;
import java.time.temporal.ChronoUnit;
import net.runelite.api.GameObject;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import java.util.List;
import java.util.Random;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Polygon;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import java.util.ArrayList;
import java.util.Comparator;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.image.BufferedImage;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Mining Bot"
)
public class MiningBotPlugin extends Plugin
{
	private static final int COPPER_ORE_ID = 436;
	private final Deque<Runnable> actionQueue = new ArrayDeque<>();
	private PipeService pipeService = null;
	private final Random random = new Random();
	private BotState currentState;
	private int idleTicks = 0;
	private int delayTicks = 0;
	private MiningBotPanel panel;
	private NavigationButton navButton;
	private boolean wasRunning = false;
	
	// Mining completion detection variables
	private long lastMiningXp = 0;
	private long xpGainedThisMine = 0;
	private boolean miningStarted = false;
	
	// Debugging and tracking variables
	private GameObject targetRock = null;
	private long sessionStartXp = 0;
	private long totalXpGained = 0;
	private Instant sessionStartTime = null;

	@Inject
	private MouseIndicatorOverlay mouseIndicatorOverlay;

	@Inject
	private MiningBotRockOverlay rockOverlay;

	@Inject
	private MiningBotStatusOverlay statusOverlay;

	@Inject
	private MiningBotInventoryOverlay inventoryOverlay;

	@Inject
	private Client client;

	@Inject
	private MiningBotConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Mining Bot started! Attempting to connect to the automation server...");
		this.currentState = BotState.IDLE;
		panel = new MiningBotPanel(this, config, configManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/images/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Mining Bot")
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

		// Initialize session tracking
		if (client.getLocalPlayer() != null)
		{
			sessionStartXp = client.getSkillExperience(Skill.MINING);
			sessionStartTime = Instant.now();
		}

		// Don't initialize pipe service automatically - user must click Connect
		log.info("Mining Bot initialized. Use the 'Connect' button to connect to the automation server.");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Mining Bot stopped!");
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(mouseIndicatorOverlay);
		overlayManager.remove(rockOverlay);
		overlayManager.remove(statusOverlay);
		overlayManager.remove(inventoryOverlay);
		
		// Disconnect from pipe service
		if (pipeService != null)
		{
			pipeService.disconnect();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			log.info("Mining Bot plugin is running - player logged in.");
			
			// Initialize session tracking if not already done
			if (sessionStartTime == null)
			{
				sessionStartXp = client.getSkillExperience(Skill.MINING);
				sessionStartTime = Instant.now();
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged animationChanged) {
		if (animationChanged.getActor() != client.getLocalPlayer()) {
			return;
		}
		
		// Only process animation changes if we're in WAIT_MINING state
		if (currentState != BotState.WAIT_MINING) {
			return;
		}
		
		int newAnimation = client.getLocalPlayer().getAnimation();
		
		// Check if player stopped mining (went idle or changed to different animation)
		if (newAnimation == AnimationID.IDLE || !isMiningAnimation(newAnimation)) {
			log.info("Mining animation stopped. Animation: {}", newAnimation);
			finishMining();
		}
	}
	
	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		// Only track mining XP
		if (statChanged.getSkill() != Skill.MINING) {
			return;
		}
		
		long currentXp = statChanged.getXp();
		
		// If we're actively mining and gained XP, this is superior detection
		if (currentState == BotState.WAIT_MINING && miningStarted) {
			if (currentXp > lastMiningXp) {
				long xpGained = currentXp - lastMiningXp;
				xpGainedThisMine += xpGained;
				lastMiningXp = currentXp;
				log.info("Gained {} mining XP (total this mine: {})", xpGained, xpGainedThisMine);
				
				// XP gain means we successfully mined an ore
				// We should wait a brief moment for animation to finish, then transition
				setRandomDelay(1, 2);
				actionQueue.add(() -> finishMining());
			}
		} else {
			// Always keep track of current XP for reference
			lastMiningXp = currentXp;
		}
	}

	@Provides
	MiningBotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MiningBotConfig.class);
	}

	@Schedule(
		period = 600,
		unit = ChronoUnit.MILLIS
	)
	public void runFsm() {
		if (panel != null) {
			panel.setStatus(currentState.toString());
			panel.setButtonText(config.startBot() ? "Stop" : "Start");
			panel.updateConnectionStatus();
		}

		boolean isRunning = config.startBot();

		if (isRunning && !wasRunning) {
			// Check if automation server is connected before starting bot
			if (!isAutomationConnected()) {
				log.warn("Cannot start bot: Automation server not connected. Please click 'Connect' first.");
				configManager.setConfiguration("miningbot", "startBot", false);
				return;
			}
			log.info("Bot starting...");
			currentState = BotState.FINDING_ROCK;
			wasRunning = true;
		}

		if (!isRunning && wasRunning) {
			log.info("Bot stopping...");
			currentState = BotState.IDLE;
			actionQueue.clear();
			delayTicks = 0;
			idleTicks = 0;
			wasRunning = false;
			return;
		}

		if (!isRunning) {
			return;
		}

		if (delayTicks > 0) {
			delayTicks--;
			return;
		}

		if (!actionQueue.isEmpty()) {
			actionQueue.poll().run();
			setRandomDelay(1, 3);
			return;
		}

		switch (currentState) {
			case IDLE:
				log.info("State: IDLE. Bot is running but in IDLE state. Transitioning to FINDING_ROCK.");
				currentState = BotState.FINDING_ROCK;
				break;
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
			case WALKING_TO_BANK:
			case BANKING:
				log.info("State: Banking (not implemented)");
				configManager.setConfiguration("miningbot", "startBot", false);
				break;
			default:
				log.warn("Unhandled bot state: {}", currentState);
				configManager.setConfiguration("miningbot", "startBot", false);
				break;
		}
	}

	private void setRandomDelay(int minTicks, int maxTicks) {
		// delayTicks = minTicks + random.nextInt(maxTicks - minTicks + 1);
		delayTicks = 0;
	}

	private void doFindingRock() {
		int[] rockIds = Arrays.stream(config.rockIds().split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.mapToInt(Integer::parseInt)
				.toArray();

		if (rockIds.length == 0) {
			log.warn("No rock IDs configured. Please set them in the config panel.");
			configManager.setConfiguration("miningbot", "startBot", false);
			return;
		}

		GameObject rock = findNearestGameObject(rockIds);
		if (rock != null) {
			targetRock = rock; // Store target rock for debugging
			log.info("Found rock with ID {} at {}", rock.getId(), rock.getSceneMinLocation());
			actionQueue.add(() -> sendClickRequest(getRandomClickablePoint(rock)));
			currentState = BotState.MINING;
			setRandomDelay(2, 5);
		} else {
			targetRock = null; // Clear target if no rock found
			log.info("No rocks found. Waiting.");
			setRandomDelay(5, 10);
		}
	}

	private void doMining() {
		// Initialize mining tracking
		miningStarted = true;
		xpGainedThisMine = 0;
		if (client.getSkillExperience(Skill.MINING) > 0) {
			lastMiningXp = client.getSkillExperience(Skill.MINING);
		}
		
		log.info("Started mining, transitioning to WAIT_MINING state");
		currentState = BotState.WAIT_MINING;
		setRandomDelay(1, 2);
	}
	
	private void doWaitMining() {
		// This state is primarily handled by events (onAnimationChanged and onStatChanged)
		// But we also include a fallback timeout mechanism
		
		if (!miningStarted) {
			log.warn("WAIT_MINING state but mining not started. Transitioning to CHECK_INVENTORY.");
			finishMining();
			return;
		}
		
		// Fallback: Check if player has been idle too long (backup detection)
		if (isPlayerIdle()) {
			idleTicks++;
			if (idleTicks > 5) { // Player idle for more than 5 ticks (~3 seconds)
				log.info("Player idle for {} ticks, mining likely finished", idleTicks);
				finishMining();
			}
		} else {
			idleTicks = 0; // Reset idle counter if player is active
		}
		
		// Additional safety: Check if we've been waiting too long (e.g., rock depleted)
		// This shouldn't normally trigger with event-based detection
		if (System.currentTimeMillis() % 30000 < 600) { // Every 30 seconds check
			if (xpGainedThisMine == 0) {
				log.warn("Been waiting for mining completion for a while with no XP gain. Rock may be depleted.");
				finishMining();
			}
		}
	}
	
	private void finishMining() {
		log.info("Mining completed. Gained {} XP this mine.", xpGainedThisMine);
		totalXpGained += xpGainedThisMine;
		miningStarted = false;
		idleTicks = 0;
		xpGainedThisMine = 0;
		targetRock = null; // Clear target rock when finished mining
		currentState = BotState.CHECK_INVENTORY;
		setRandomDelay(1, 3);
	}
	
	/**
	 * Check if the given animation ID represents a mining animation
	 */
	private boolean isMiningAnimation(int animationId) {
		// Common mining animation IDs (you may need to add more based on your pickaxe types)
		// These are approximate - you may need to verify the exact IDs in-game
		switch (animationId) {
			case 625:   // Bronze pickaxe
			case 626:   // Iron pickaxe  
			case 627:   // Steel pickaxe
			case 628:   // Black pickaxe
			case 629:   // Mithril pickaxe
			case 630:   // Adamant pickaxe
			case 631:   // Rune pickaxe
			case 7139:  // Dragon pickaxe
			case 8347:  // Infernal pickaxe
			case 4481:  // Crystal pickaxe
				return true;
			default:
				return false;
		}
	}

	private void doCheckInventory() {
		if (isInventoryFull()) {
			if (config.miningMode() == MiningMode.POWER_MINE_DROP) {
				currentState = BotState.DROPPING;
			} else {
				currentState = BotState.WALKING_TO_BANK;
			}
		} else {
			currentState = BotState.FINDING_ROCK;
		}
		setRandomDelay(1, 3);
	}

	private void doDropping() {
		actionQueue.add(() -> sendKeyRequest("key_hold", "shift"));

		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		if (inventoryWidget != null) {
			ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
			if (inventory == null) return;

			int[] oreIds = Arrays.stream(config.oreIds().split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.mapToInt(Integer::parseInt)
					.toArray();

			if (oreIds.length == 0) {
				log.warn("No ore IDs configured for dropping. Please set them in the config panel.");
				// We don't stop the bot here, just log a warning.
				// The bot will proceed to find the next rock.
			}

			for (Widget item : inventoryWidget.getDynamicChildren()) {
				if (item != null && !item.isHidden() && Arrays.stream(oreIds).anyMatch(id -> id == item.getItemId())) {
					actionQueue.add(() -> {
						Point clickPoint = new Point(
								item.getCanvasLocation().getX() + (item.getWidth() / 2) + random.nextInt(4) - 2,
								item.getCanvasLocation().getY() + (item.getHeight() / 2) + random.nextInt(4) - 2
						);
						sendClickRequest(clickPoint);
					});
				}
			}
		}

		actionQueue.add(() -> sendKeyRequest("key_release", "shift"));
		actionQueue.add(() -> {
			currentState = BotState.FINDING_ROCK;
			log.info("Finished dropping. Back to mining.");
		});
	}

	public boolean isInventoryFull() {
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null) {
			return false;
		}
		return inventory.count() >= 28;
	}

	private boolean isPlayerIdle() {
		return client.getLocalPlayer() != null && client.getLocalPlayer().getAnimation() == -1;
	}

	private GameObject findNearestGameObject(int... ids) {
		if (client.getLocalPlayer() == null)
		{
			return null;
		}

		List<GameObject> result = new ArrayList<>();
		Scene scene = client.getScene();
		Tile[][] tiles = scene.getTiles()[client.getPlane()];

		for (int x = 0; x < scene.getTiles()[0].length; x++) {
			for (int y = 0; y < scene.getTiles()[0][0].length; y++) {
				Tile tile = tiles[x][y];
				if (tile == null) {
					continue;
				}
				for (GameObject gameObject : tile.getGameObjects()) {
					if (gameObject != null) {
						for (int id : ids) {
							if (gameObject.getId() == id) {
								result.add(gameObject);
							}
						}
					}
				}
			}
		}

		if (result.isEmpty()) {
			return null;
		}

		result.sort(Comparator.comparingInt(a -> a.getLocalLocation().distanceTo(client.getLocalPlayer().getLocalLocation())));
		return result.get(0);
	}

	private Point getRandomClickablePoint(GameObject gameObject) {
		if (gameObject == null) {
			return null;
		}
		// Use getConvexHull for a more accurate clickable polygon
		Shape convexHull = gameObject.getConvexHull();
		if (convexHull == null) {
			// Fallback to the old method if convex hull is not available
			return getRandomClickablePointFromClickbox(gameObject);
		}

		Rectangle bounds = convexHull.getBounds();
		if (bounds.isEmpty()) {
			return null;
		}

		// Center of the bounding box for the Gaussian distribution
		double centerX = bounds.getCenterX();
		double centerY = bounds.getCenterY();
		// Standard deviation - smaller values mean tighter clusters around the center
		double stdDevX = bounds.getWidth() / 4.0;
		double stdDevY = bounds.getHeight() / 4.0;

		// Try up to 15 times to find a point within the polygon using a Gaussian distribution
		for (int i = 0; i < 15; i++) {
			int x = (int) (centerX + random.nextGaussian() * stdDevX);
			int y = (int) (centerY + random.nextGaussian() * stdDevY);
			Point randomPoint = new Point(x, y);

			// Ensure the point is within the actual polygon shape
			if (convexHull.contains(randomPoint)) {
				return randomPoint;
			}
		}

		// If Gaussian fails, fall back to a uniformly random point within the hull
		log.warn("Could not find Gaussian point in 15 attempts, falling back to uniform random.");
		return getRandomClickablePointFromClickbox(gameObject);
	}

	private Point getRandomClickablePointFromClickbox(GameObject gameObject) {
		if (gameObject == null) {
			return null;
		}
		Shape clickbox = gameObject.getClickbox();
		if (clickbox == null) {
			return null;
		}

		Rectangle bounds = clickbox.getBounds();
		if (bounds.isEmpty()) {
			return null;
		}

		// Try up to 10 times to find a random point in the clickbox
		for (int i = 0; i < 10; i++) {
			Point randomPoint = new Point(
				bounds.x + random.nextInt(bounds.width),
				bounds.y + random.nextInt(bounds.height)
			);
			if (clickbox.contains(randomPoint)) {
				return randomPoint;
			}
		}

		// If all else fails, return the center
		return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
	}

	private void sendClickRequest(Point point) {
		if (point == null) return;

		if (pipeService != null && pipeService.isConnected()) {
			if (!pipeService.sendClick(point.x, point.y)) {
				log.warn("Failed to send click command via pipe");
			}
		} else {
			log.warn("Cannot send click - pipe service not connected");
		}
	}

	private void sendKeyRequest(String endpoint, String key) {
		if (pipeService != null && pipeService.isConnected()) {
			boolean success = false;
			
			switch (endpoint) {
				case "key_press":
					success = pipeService.sendKeyPress(key);
					break;
				case "key_hold":
					success = pipeService.sendKeyHold(key);
					break;
				case "key_release":
					success = pipeService.sendKeyRelease(key);
					break;
				default:
					log.warn("Unknown key endpoint: {}", endpoint);
					return;
			}
			
			if (!success) {
				log.warn("Failed to send key {} command via pipe", endpoint);
			}
		} else {
			log.warn("Cannot send key command - pipe service not connected");
		}
	}

	// Getter methods for debugging overlays
	public BotState getCurrentState()
	{
		return currentState;
	}

	public GameObject getTargetRock()
	{
		return targetRock;
	}

	public long getSessionXpGained()
	{
		if (sessionStartXp == 0)
		{
			return 0;
		}
		return client.getSkillExperience(Skill.MINING) - sessionStartXp;
	}

	public long getTotalXpGained()
	{
		return totalXpGained;
	}

	public Duration getSessionRuntime()
	{
		if (sessionStartTime == null)
		{
			return Duration.ZERO;
		}
		return Duration.between(sessionStartTime, Instant.now());
	}
	
	/**
	 * Check if the automation server is connected.
	 * @return true if connected via named pipe
	 */
	public boolean isAutomationConnected()
	{
		return pipeService != null && pipeService.isConnected();
	}
	
	/**
	 * Manually connect to the automation server (triggered by Connect button).
	 * @return true if connection successful
	 */
	public boolean connectAutomation()
	{
		if (pipeService == null)
		{
			pipeService = new PipeService();
		}
		
		if (pipeService.connect())
		{
			if (pipeService.sendConnect())
			{
				log.info("Successfully connected to automation server via Connect button.");
				return true;
			}
			else
			{
				log.warn("Connected to pipe but failed to send connect command.");
				return false;
			}
		}
		else
		{
			log.error("Failed to connect to automation server. Make sure the Python server is running.");
			return false;
		}
	}
	
	/**
	 * Attempt to reconnect to the automation server.
	 * @return true if reconnection successful
	 */
	public boolean reconnectAutomation()
	{
		if (pipeService == null)
		{
			pipeService = new PipeService();
		}
		
		if (pipeService.connect())
		{
			if (pipeService.sendConnect())
			{
				log.info("Successfully reconnected to automation server.");
				return true;
			}
			else
			{
				log.warn("Connected to pipe but failed to send connect command.");
				return false;
			}
		}
		else
		{
			log.error("Failed to reconnect to automation server.");
			return false;
		}
	}
} 