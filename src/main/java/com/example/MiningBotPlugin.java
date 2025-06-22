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

@Slf4j
@PluginDescriptor(
	name = "Mining Bot"
)
public class MiningBotPlugin extends Plugin
{
	private final HttpClient httpClient = HttpClient.newHttpClient();

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

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/status"))
			.POST(HttpRequest.BodyPublishers.noBody())
			.timeout(Duration.ofSeconds(5))
			.build();

		httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				log.info("Status check response: {}", response.statusCode());
				if (response.statusCode() == 200)
				{
					// Send a test click to the center of the screen
					sendTestClick();
				}
			})
			.exceptionally(e -> {
				log.warn("Failed to connect to automation server: {}", e.getMessage());
				return null;
			});
	}

	private void sendTestClick()
	{
		int canvasWidth = client.getCanvas().getWidth();
		int canvasHeight = client.getCanvas().getHeight();
		int centerX = canvasWidth / 2;
		int centerY = canvasHeight / 2;

		// Create a simple map for the JSON payload
		java.util.Map<String, Integer> payload = new java.util.HashMap<>();
		payload.put("x", centerX);
		payload.put("y", centerY);

		String jsonPayload = gson.toJson(payload);

		HttpRequest clickRequest = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:8000/click"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
			.timeout(Duration.ofSeconds(5))
			.build();

		httpClient.sendAsync(clickRequest, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {
				log.info("Test click response: {}", response.statusCode());
			})
			.exceptionally(e -> {
				log.warn("Failed to send test click: {}", e.getMessage());
				return null;
			});
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
} 