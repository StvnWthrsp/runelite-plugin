package com.example;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

import java.awt.Point;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class ActionService {
    private final RunepalPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private final PipeService pipeService;
    private final GameService gameService;
    private volatile boolean isCurrentlyDropping = false;

    @Inject
    public ActionService(RunepalPlugin plugin, PipeService pipeService, GameService gameService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.pipeService = Objects.requireNonNull(pipeService, "pipeService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
    }

    public void powerDrop(int[] itemIds) {
        if (isCurrentlyDropping) return;
        if (itemIds.length == 0) {
            log.info("No ore ids found. Cannot drop inventory. Stopping bot.");
            plugin.stopBot();
            return;
        }

        isCurrentlyDropping = true;
        log.info("Starting to drop inventory.");

        sendKeyRequest("/key_hold", "shift");

        long delay = (long) (Math.random() * (250 - 350)) + 350; // Initial delay before first click

        for (int i = 0; i < 28; i++) {
            int itemId = gameService.getInventoryItemId(i);
            if (gameService.isItemInList(itemId, itemIds)) {
                final int finalI = i;
                scheduler.schedule(() -> {
                    sendClickRequest(gameService.getInventoryItemPoint(finalI), true);
                }, delay, TimeUnit.MILLISECONDS);
                delay += (long) (Math.random() * (250 - 350)) + 350; // Stagger subsequent clicks
            }
        }

        // Schedule the final actions after all drops are scheduled
        scheduler.schedule(() -> {
            sendKeyRequest("/key_release", "shift");
            log.info("Finished dropping inventory.");
            isCurrentlyDropping = false; // Signal to the main loop
        }, delay, TimeUnit.MILLISECONDS);
    }

    public boolean isDropping() {
        return isCurrentlyDropping;
    }

    public void sendClickRequest(Point point, boolean move) {
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendClickRequest.");
			return;
		}
        if (!pipeService.sendClick(point.x, point.y, move)) {
            log.warn("Failed to send click command via pipe");
            plugin.stopBot();
        }
	}

    public void sendMouseMoveRequest(Point point) {
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendMouseMoveRequest.");
			return;
		}
        if (!pipeService.sendMouseMove(point.x, point.y)) {
            log.warn("Failed to send mouse move command via pipe");
            plugin.stopBot();
        }
	}

	public void sendKeyRequest(String endpoint, String key) {
		boolean success;
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
			plugin.stopBot();
		}
	}

}
