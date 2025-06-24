package com.example;

import com.google.inject.Provides;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.inject.Inject;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
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

@Slf4j
@PluginDescriptor(
	name = "Mining Bot"
)
public class MiningBotPlugin extends Plugin
{
	private static final int COPPER_ORE_ID = 436;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Deque<Runnable> actionQueue = new ArrayDeque<>();
	private final Random random = new Random();
	private BotState currentState;
	private int idleTicks = 0;
	private int delayTicks = 0;
	private MiningBotPanel panel;
	private NavigationButton navButton;
	private boolean wasRunning = false;

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

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/connect"))
			.POST(HttpRequest.BodyPublishers.noBody())
			.timeout(Duration.ofSeconds(30)) // Give it a bit more time to connect
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				if (response.statusCode() == 200)
				{
					log.info("Successfully connected to the automation server.");
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Mining Bot: Connected.", null);
				}
				else
				{
					log.error("Failed to connect to automation server. Status code: {}", response.statusCode());
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Mining Bot: Connection FAILED.", null);
				}
			})
			.exceptionally(e -> {
				log.error("Failed to send connect request to automation server.", e);
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Mining Bot: Connection FAILED. Is the server running?", null);
				return null;
			});
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Mining Bot stopped!");
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Mining Bot plugin is running.", null);
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
		}

		boolean isRunning = config.startBot();

		if (isRunning && !wasRunning) {
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
		delayTicks = minTicks + random.nextInt(maxTicks - minTicks + 1);
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
			log.info("Found rock with ID {} at {}", rock.getId(), rock.getSceneMinLocation());
			actionQueue.add(() -> sendClickRequest(getRandomClickablePoint(rock)));
			currentState = BotState.MINING;
			setRandomDelay(2, 5);
		} else {
			log.info("No rocks found. Waiting.");
			setRandomDelay(5, 10);
		}
	}

	private void doMining() {
		if (isPlayerIdle()) {
			idleTicks++;
		} else {
			idleTicks = 0;
		}

		if (idleTicks > 3) {
			idleTicks = 0;
			currentState = BotState.CHECK_INVENTORY;
			setRandomDelay(1, 3);
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

	private boolean isInventoryFull() {
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
		Shape clickbox = gameObject.getClickbox();
		if (clickbox == null) {
			return null;
		}

		Rectangle bounds = clickbox.getBounds();
		if (bounds.isEmpty()) {
			return null;
		}

		for (int i = 0; i < 10; i++) {
			Point randomPoint = new Point(
				bounds.x + (int) (bounds.width * Math.random()),
				bounds.y + (int) (bounds.height * Math.random())
			);
			if (clickbox.contains(randomPoint)) {
				return randomPoint;
			}
		}
		return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
	}

	private void sendClickRequest(Point point) {
		if (point == null) return;

		java.util.Map<String, Integer> payload = new java.util.HashMap<>();
		payload.put("x", point.x);
		payload.put("y", point.y);
		String jsonPayload = gson.toJson(payload);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/click"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
			.timeout(Duration.ofSeconds(2))
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				if (response.statusCode() != 200) {
					log.warn("Click request failed with status: {}", response.statusCode());
				}
			})
			.exceptionally(e -> {
				log.error("Failed to send click request.", e);
				return null;
			});
	}

	private void sendKeyRequest(String endpoint, String key) {
		java.util.Map<String, String> payload = new java.util.HashMap<>();
		payload.put("key", key);
		String jsonPayload = gson.toJson(payload);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/" + endpoint))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
			.timeout(Duration.ofSeconds(2))
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				if (response.statusCode() != 200) {
					log.warn("Key request to {} failed with status: {}", endpoint, response.statusCode());
				}
			})
			.exceptionally(e -> {
				log.error("Failed to send key request to " + endpoint, e);
				return null;
			});
	}
} 