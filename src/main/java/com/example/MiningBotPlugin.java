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
		log.info("Mining Bot started! Attempting to connect to the automation server...");

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