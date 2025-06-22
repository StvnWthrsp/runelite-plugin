package com.example;

import com.google.inject.Provides;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import com.google.gson.Gson;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.Constants;

@Slf4j
@PluginDescriptor(
	name = "Mining Bot"
)
public class MiningBotPlugin extends Plugin
{
	private static final int[] IRON_ORE_IDS = { 11364, 11365 };
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Random random = new Random();
	private BotState currentState;
	private int timeout;

	@Inject
	private Client client;

	@Inject
	private MiningBotConfig config;

	@Inject
	private Gson gson;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Mining Bot started!");
		currentState = BotState.IDLE;

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/status"))
			.POST(HttpRequest.BodyPublishers.noBody())
			.timeout(Duration.ofSeconds(5))
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				log.info("Status check response: {}", response.statusCode());
			})
			.exceptionally(e -> {
				log.warn("Failed to connect to automation server: {}", e.getMessage());
				return null;
			});
	}

	@Schedule(
		period = 600,
		unit = ChronoUnit.MILLIS
	)
	public void runFsm()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		switch (currentState)
		{
			case IDLE:
				currentState = BotState.FINDING_ROCK;
				break;
			case FINDING_ROCK:
				doFindingRock();
				break;
			case MINING:
				doMining();
				break;
			case CHECK_INVENTORY:
				// For now, just loop back to finding another rock.
				log.info("Checking inventory, then finding next rock.");
				currentState = BotState.FINDING_ROCK;
				break;
			default:
				log.info("FSM in unhandled state: {}", currentState);
				break;
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Mining Bot stopped!");
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

	// --- State Logic Methods ---

	private void doFindingRock()
	{
		GameObject rock = findNearestGameObject(IRON_ORE_IDS);
		if (rock != null)
		{
			Point clickPoint = getRandomClickablePoint(rock);
			if (clickPoint != null)
			{
				sendClick(clickPoint);
				timeout = 0;
				currentState = BotState.MINING;
			}
		}
	}

	private void doMining()
	{
		if (isPlayerIdle())
		{
			timeout++;
			// If idle for ~2.4 seconds (4 ticks * 600ms), assume rock is depleted.
			if (timeout > 4)
			{
				currentState = BotState.CHECK_INVENTORY;
			}
		}
		else
		{
			// Reset timeout if we are animating
			timeout = 0;
		}
	}

	// --- API Communication ---

	private void sendClick(Point p)
	{
		// The point p is already in canvas coordinates. No offset correction needed.
		java.util.Map<String, Integer> payload = new java.util.HashMap<>();
		payload.put("x", p.getX());
		payload.put("y", p.getY());

		String jsonPayload = gson.toJson(payload);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/click"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
			.timeout(Duration.ofSeconds(2))
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> log.debug("Click response: {}", response.statusCode()))
			.exceptionally(e -> {
				log.warn("Failed to send click: {}", e.getMessage());
				return null;
			});
	}

	// --- Helper Methods for FSM ---

	private boolean isInventoryFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		return inventory != null && inventory.count() >= 28;
	}

	private boolean isPlayerIdle()
	{
		return client.getLocalPlayer() != null && client.getLocalPlayer().getAnimation() == -1;
	}

	private GameObject findNearestGameObject(int... ids)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		LocalPoint playerLocation = localPlayer.getLocalLocation();
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();

		GameObject closestObject = null;
		int closestDistance = -1;

		for (int z = 0; z < Constants.MAX_Z; z++)
		{
			for (int x = 0; x < Constants.SCENE_SIZE; x++)
			{
				for (int y = 0; y < Constants.SCENE_SIZE; y++)
				{
					Tile tile = tiles[z][x][y];
					if (tile == null)
					{
						continue;
					}

					for (GameObject gameObject : tile.getGameObjects())
					{
						if (gameObject == null)
						{
							continue;
						}

						for (int id : ids)
						{
							if (gameObject.getId() == id)
							{
								int distance = gameObject.getLocalLocation().distanceTo(playerLocation);
								if (closestObject == null || distance < closestDistance)
								{
									closestObject = gameObject;
									closestDistance = distance;
								}
								break;
							}
						}
					}
				}
			}
		}

		return closestObject;
	}

	private Point getRandomClickablePoint(GameObject object)
	{
		if (object == null) return null;
		
		java.awt.Shape clickbox = object.getClickbox();
		if (clickbox == null) return null;

		java.awt.Rectangle bounds = clickbox.getBounds();
		if (bounds.isEmpty()) return null;

		for (int i = 0; i < 10; i++)
		{
			int x = bounds.x + random.nextInt(bounds.width);
			int y = bounds.y + random.nextInt(bounds.height);
			if (clickbox.contains(x, y))
			{
				return new Point(x, y);
			}
		}
		
		return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
	}
} 