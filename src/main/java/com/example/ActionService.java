package com.example;

import lombok.extern.slf4j.Slf4j;
import javax.inject.Inject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ActionService {
    private final AndromedaPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private volatile boolean isCurrentlyDropping = false;

    @Inject
    public ActionService(AndromedaPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
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

        plugin.sendKeyRequest("/key_hold", "shift");

        long delay = (long) (Math.random() * (250 - 350)) + 350; // Initial delay before first click

        for (int i = 0; i < 28; i++) {
            int itemId = plugin.getInventoryItemId(i);
            if (plugin.isItemInList(itemId, itemIds)) {
                final int finalI = i;
                scheduler.schedule(() -> {
                    plugin.sendClickRequest(plugin.getInventoryItemPoint(finalI), true);
                }, delay, TimeUnit.MILLISECONDS);
                delay += (long) (Math.random() * (250 - 350)) + 350; // Stagger subsequent clicks
            }
        }

        // Schedule the final actions after all drops are scheduled
        scheduler.schedule(() -> {
            plugin.sendKeyRequest("/key_release", "shift");
            log.info("Finished dropping inventory.");
            isCurrentlyDropping = false; // Signal to the main loop
        }, delay, TimeUnit.MILLISECONDS);
    }

    public boolean isDropping() {
        return isCurrentlyDropping;
    }

}
