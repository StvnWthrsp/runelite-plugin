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
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.util.Arrays;
import net.runelite.client.task.Schedule;
import java.time.temporal.ChronoUnit;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;
import java.util.List;
import java.util.Random;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import java.util.ArrayList;
import java.util.Comparator;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import java.awt.image.BufferedImage;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.Constants;
import shortestpath.ShortestPathConfig;
import shortestpath.pathfinder.PathfinderConfig;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@PluginDescriptor(
	name = "Andromeda"
)
public class AndromedaPlugin extends Plugin
{
	private PipeService pipeService = null;
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
	private final ActionService actionService = new ActionService(this);

	
	// Debugging and tracking variables
	@Getter
    private GameObject targetRock = null;
	@Getter
    private NPC targetNpc = null;
	private long sessionStartXp = 0;
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
	private CombatBotNpcOverlay combatNpcOverlay;

	@Getter
    @Inject
	private Client client;

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
		log.info("General Bot started! Attempting to connect to the automation server...");
		this.currentState = "IDLE";
		panel = new BotPanel(this, config, configManager);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/images/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("General Bot")
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

		ShortestPathConfig shortestPathConfig = configManager.getConfig(ShortestPathConfig.class);
		pathfinderConfig = new PathfinderConfig(client, shortestPathConfig);
		if (client.getGameState() == GameState.LOGGED_IN) {
			pathfinderConfig.refresh();
		}

		// Initialize session tracking
		if (client.getLocalPlayer() != null)
		{
			sessionStartXp = client.getSkillExperience(Skill.MINING);
			sessionStartTime = Instant.now();
		}

		// Don't initialize pipe service automatically - user must click Connect
		log.info("General Bot initialized. Use the 'Connect' button to connect to the automation server.");
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
			log.info("General Bot plugin is running - player logged in.");
			
			if (pathfinderConfig != null) {
				pathfinderConfig.refresh();
			}

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
		BotTask currentTask = taskManager.getCurrentTask();
		if (currentTask instanceof MiningTask) {
			((MiningTask) currentTask).onAnimationChanged(animationChanged);
		}
	}
	
	@Subscribe
	public void onStatChanged(StatChanged statChanged) {
		BotTask currentTask = taskManager.getCurrentTask();
		if (currentTask instanceof MiningTask) {
			((MiningTask) currentTask).onStatChanged(statChanged);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged) {
		BotTask currentTask = taskManager.getCurrentTask();
		if (currentTask instanceof CombatTask) {
			((CombatTask) currentTask).onInteractingChanged(interactingChanged);
		}
	}

	@Provides
	BotConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BotConfig.class);
	}

	@Schedule(
		period = 600,
		unit = ChronoUnit.MILLIS
	)
	public void onGameTick() {
		if (panel != null) {
			panel.setStatus(currentState);
			panel.setButtonText(config.startBot() ? "Stop" : "Start");
			panel.updateConnectionStatus();
		}

		boolean isRunning = config.startBot();

		if (isRunning && !wasRunning) {
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
					taskManager.pushTask(new MiningTask(this, config, taskManager, pathfinderConfig, this.actionService));
					break;
				case COMBAT_BOT:
					taskManager.pushTask(new CombatTask(this, config, taskManager));
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

	public void stopBot() {
		configManager.setConfiguration("miningbot", "startBot", false);
	}

	public void walkTo(WorldPoint worldPoint) {
		LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
		if (localPoint != null) {
			net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
			if (minimapPoint != null) {
				log.info("Requesting walk to {} via minimap click at {}", worldPoint, minimapPoint);
				sendClickRequest(new Point(minimapPoint.getX(), minimapPoint.getY()), true);
			} else {
				log.warn("Cannot walk to {}: not visible on minimap.", worldPoint);
			}
		} else {
			log.warn("Cannot walk to {}: not in scene.", worldPoint);
		}
	}

	// --- Public Helper Methods for Tasks ---

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

	public boolean isItemInList(int itemId, int[] list) {
		for (int id : list) {
			if (itemId == id) {
				return true;
			}
		}
		return false;
	}

	public int getInventoryItemId(int slot) {
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null) return -1;
		Item item = inventory.getItem(slot);
		return item != null ? item.getId() : -1;
	}

	public Point getInventoryItemPoint(int slot) {
		Widget inventoryWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (inventoryWidget == null) return new Point(-1, -1);
		Widget itemWidget = inventoryWidget.getChild(slot);
		if (itemWidget == null) return new Point(-1, -1);
		Rectangle bounds = itemWidget.getBounds();
		int x = (int) bounds.getCenterX() + random.nextInt(4) - 2;
		int y = (int) bounds.getCenterY() + random.nextInt(4) - 2;
		return new Point(x, y);
	}

	public boolean isInventoryFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory != null && inventory.count() >= 28;
	}

	public boolean isInventoryEmpty() {
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		// We consider the inventory "empty" if it only contains a pickaxe (or is fully empty)
		return inventory != null && inventory.count() <= 1;
	}

	public boolean isPlayerIdle() {
		return client.getLocalPlayer().getAnimation() == -1;
	}

	public GameObject findNearestGameObject(int... ids) {
		Scene scene = client.getWorldView(-1).getScene();
		Tile[][][] tiles = scene.getTiles();
		int z = client.getWorldView(-1).getPlane();
		List<GameObject> matchingObjects = new ArrayList<>();

		for (int x = 0; x < Constants.SCENE_SIZE; x++) {
			for (int y = 0; y < Constants.SCENE_SIZE; y++) {
				Tile tile = tiles[z][x][y];
				if (tile == null) {
					continue;
				}
				for (GameObject gameObject : tile.getGameObjects()) {
					if (gameObject != null) {
						for (int id : ids) {
							if (gameObject.getId() == id) {
								matchingObjects.add(gameObject);
							}
						}
					}
				}
			}
		}

		return matchingObjects.stream()
				.min(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation())))
				.orElse(null);
	}

	public Point getRandomClickablePoint(GameObject gameObject) {
		Shape clickbox = gameObject.getClickbox();
		if (clickbox == null)
		{
			return new Point(-1, -1);
		}
		Rectangle bounds = clickbox.getBounds();
		if (bounds.isEmpty())
		{
			return new Point(-1, -1);
		}

		// In a loop, generate a random x and y within the bounding rectangle.
		for (int i = 0; i < 10; i++) {
			Point randomPoint = new Point(
				bounds.x + random.nextInt(bounds.width),
				bounds.y + random.nextInt(bounds.height)
			);

			// Use shape.contains(x, y) to check if the random point is within the actual shape.
			if (clickbox.contains(randomPoint)) {
				return randomPoint;
			}
		}
		// Fallback to center if we fail to find a point
		return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
	}

	public Point getRandomPointInBounds(Rectangle bounds) {
		if (bounds.isEmpty()) {
			return new Point(-1, -1);
		}
		return new Point(
				bounds.x + random.nextInt(bounds.width),
				bounds.y + random.nextInt(bounds.height)
		);
	}

	public void sendClickRequest(Point point, boolean move) {
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendClickRequest.");
			return;
		}
		if (pipeService != null && pipeService.isConnected()) {
			if (!pipeService.sendClick(point.x, point.y, move)) {
				log.warn("Failed to send click command via pipe");
				stopBot();
			}
		} else {
			log.warn("Cannot send click request: Pipe service not connected.");
			stopBot();
		}
	}

	public void sendMouseMoveRequest(Point point) {
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendMouseMoveRequest.");
			return;
		}
		if (pipeService != null && pipeService.isConnected()) {
			if (!pipeService.sendMouseMove(point.x, point.y)) {
				log.warn("Failed to send mouse move command via pipe");
				stopBot();
			}
		} else {
			log.warn("Cannot send mouse move request: Pipe service not connected.");
			stopBot();
		}
	}

	public void sendKeyRequest(String endpoint, String key) {
		if (pipeService == null || !pipeService.isConnected()) {
			log.warn("Cannot send key request: Pipe service not connected.");
			stopBot();
			return;
		}

		boolean success = false;
		switch (endpoint) {
			case "/key_hold":
				success = pipeService.sendKeyHold(key);
				break;
			case "/key_release":
				success = pipeService.sendKeyRelease(key);
				break;
			default:
				log.warn("Unknown key endpoint: {}", endpoint);
				return;
		}

		if (!success) {
			log.warn("Failed to send key {} command via pipe", endpoint);
			stopBot();
		}
	}

	// --- Public Getters/Setters for Panel and Overlays ---

    public void setTargetRock(GameObject rock) {
		this.targetRock = rock;
		rockOverlay.setTarget(rock);
	}

    public void setTargetNpc(NPC npc) {
		this.targetNpc = npc;
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

	public boolean verifyHoverAction(String expectedAction, String expectedTarget) {
        // Get the menu entries that are present on hover
        MenuEntry[] menuEntries = getClient().getMenu().getMenuEntries();

        // Check if there are any menu entries at all
        if (menuEntries.length == 0) {
            return false;
        }

        // The default action is the last entry in the array.
        // The array is ordered from the bottom of the right-click menu to the top.
        MenuEntry topEntry = menuEntries[menuEntries.length - 1];

        String action = topEntry.getOption();
        log.debug("Left-click action: {}", action);
        // The target name might have color tags (e.g., <col=ff9040>Goblin</col>)
        // It's a good practice to clean this up for reliable comparison.
        String target = Text.removeTags(topEntry.getTarget());
        log.debug("Left-click target: {}", target);

        // Perform the verification
        return action.equalsIgnoreCase(expectedAction);
    }


	public boolean isAutomationConnected()
	{
		return pipeService != null && pipeService.isConnected();
	}

	public boolean connectAutomation()
	{
		try {
			if (pipeService == null) {
				pipeService = new PipeService();
			}
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
			if (pipeService != null) {
				pipeService.disconnect();
			}
			panel.updateConnectionStatus();
			return false;
		}
	}

	public boolean reconnectAutomation()
	{
		log.info("Attempting to reconnect to the automation server...");
		if (pipeService != null) {
			pipeService.disconnect();
		}
		return connectAutomation();
	}
} 