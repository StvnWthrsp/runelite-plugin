package com.runepal.harness;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;

@Slf4j
public class HarnessSnapshotBuilder {
    private static final int INVENTORY_SLOTS = 28;
    private static final int MAX_MENU_ENTRIES = 12;
    private static final int MAX_NEARBY_NPCS = 25;
    private static final int MAX_NEARBY_OBJECTS = 25;
    private static final int WORLD_INDEX_MAX_AGE_TICKS = 200;

    private final RunepalPlugin plugin;
    private final Client client;
    private final BotConfig config;
    private final HarnessWorldIndex worldIndex;

    public HarnessSnapshotBuilder(RunepalPlugin plugin, BotConfig config, HarnessWorldIndex worldIndex) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.config = config;
        this.worldIndex = worldIndex;
    }

    public JsonObject buildSnapshot(long tick) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("snapshotVersion", 2);
        snapshot.addProperty("tick", tick);
        snapshot.addProperty("timestamp", Instant.now().toString());
        GameState gameState = client.getGameState();
        snapshot.addProperty("gameState", gameState == null ? "UNKNOWN" : gameState.name());

        Player localPlayer = client.getLocalPlayer();
        WorldPoint playerLocation = localPlayer == null ? null : localPlayer.getWorldLocation();

        snapshot.add("player", buildPlayer(localPlayer));
        snapshot.add("inventory", buildInventory());
        snapshot.add("menu", buildMenu());
        snapshot.add("nearbyNpcs", buildNearbyNpcs(playerLocation, tick));
        snapshot.add("nearbyObjects", buildNearbyObjects(playerLocation, tick));
        snapshot.add("ui", buildUiFlags());
        snapshot.add("camera", buildCamera());

        if (config.harnessIncludeScreenshots()) {
            JsonObject capture = captureScreenshot(640);
            if (capture != null) {
                snapshot.add("screenshot", capture);
            }
        }

        return snapshot;
    }

    public JsonObject captureScreenshot(int maxWidth) {
        Canvas canvas = client.getCanvas();
        if (canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return null;
        }

        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } catch (Exception e) {
            log.debug("Failed to capture harness screenshot", e);
            return null;
        } finally {
            graphics.dispose();
        }

        BufferedImage output = image;
        int safeMaxWidth = Math.max(200, maxWidth);
        if (image.getWidth() > safeMaxWidth) {
            int newHeight = Math.max(1, (int) Math.round(image.getHeight() * (safeMaxWidth / (double) image.getWidth())));
            Image scaled = image.getScaledInstance(safeMaxWidth, newHeight, Image.SCALE_SMOOTH);
            BufferedImage scaledImage = new BufferedImage(safeMaxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D scaledGraphics = scaledImage.createGraphics();
            try {
                scaledGraphics.drawImage(scaled, 0, 0, null);
            } finally {
                scaledGraphics.dispose();
            }
            output = scaledImage;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(output, "jpg", outputStream);
            JsonObject capture = new JsonObject();
            capture.addProperty("jpegBase64", Base64.getEncoder().encodeToString(outputStream.toByteArray()));
            capture.addProperty("width", output.getWidth());
            capture.addProperty("height", output.getHeight());
            return capture;
        } catch (Exception e) {
            log.debug("Failed to encode harness screenshot", e);
            return null;
        }
    }

    private JsonObject buildPlayer(Player player) {
        JsonObject playerJson = new JsonObject();
        if (player == null) {
            playerJson.addProperty("present", false);
            return playerJson;
        }

        playerJson.addProperty("present", true);
        playerJson.addProperty("name", player.getName() == null ? "" : player.getName());
        WorldPoint location = player.getWorldLocation();
        if (location != null) {
            playerJson.addProperty("worldX", location.getX());
            playerJson.addProperty("worldY", location.getY());
            playerJson.addProperty("plane", location.getPlane());
        }
        playerJson.addProperty("animation", player.getAnimation());
        playerJson.addProperty("idle", player.getAnimation() == -1);
        return playerJson;
    }

    private JsonArray buildInventory() {
        JsonArray inventory = new JsonArray();
        ItemContainer itemContainer = client.getItemContainer(InventoryID.INV);
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("slot", slot);
            Item item = itemContainer == null ? null : itemContainer.getItem(slot);
            if (item == null || item.getId() <= 0) {
                slotJson.addProperty("itemId", -1);
                slotJson.addProperty("quantity", 0);
            } else {
                slotJson.addProperty("itemId", item.getId());
                slotJson.addProperty("quantity", item.getQuantity());
            }
            inventory.add(slotJson);
        }
        return inventory;
    }

    private JsonObject buildMenu() {
        JsonObject menuJson = new JsonObject();
        menuJson.addProperty("open", client.isMenuOpen());
        JsonArray entries = new JsonArray();
        Menu menu = client.getMenu();
        if (menu != null) {
            MenuEntry[] menuEntries = menu.getMenuEntries();
            int limit = Math.min(MAX_MENU_ENTRIES, menuEntries.length);
            for (int i = 0; i < limit; i++) {
                MenuEntry entry = menuEntries[i];
                JsonObject entryJson = new JsonObject();
                entryJson.addProperty("option", entry.getOption() == null ? "" : entry.getOption());
                entryJson.addProperty("target", entry.getTarget() == null ? "" : entry.getTarget());
                entries.add(entryJson);
            }
        }
        menuJson.add("entries", entries);
        return menuJson;
    }

    private JsonArray buildNearbyNpcs(WorldPoint playerLocation, long tick) {
        JsonArray npcs = new JsonArray();
        if (playerLocation == null) {
            return npcs;
        }

        List<HarnessWorldIndex.IndexedNpc> indexedNpcs =
                worldIndex.getNearbyNpcs(playerLocation, client.getWorld(), tick, MAX_NEARBY_NPCS, WORLD_INDEX_MAX_AGE_TICKS);
        for (HarnessWorldIndex.IndexedNpc indexedNpc : indexedNpcs) {
            JsonObject npcJson = new JsonObject();
            npcJson.addProperty("id", indexedNpc.getId());
            npcJson.addProperty("name", indexedNpc.getName());
            WorldPoint location = indexedNpc.getLocation();
            npcJson.addProperty("distance", location == null ? -1 : playerLocation.distanceTo(location));
            if (location != null) {
                npcJson.addProperty("worldX", location.getX());
                npcJson.addProperty("worldY", location.getY());
                npcJson.addProperty("plane", location.getPlane());
            }
            JsonArray actions = new JsonArray();
            for (String action : indexedNpc.getActions()) {
                actions.add(action);
            }
            npcJson.add("actions", actions);
            npcs.add(npcJson);
        }
        return npcs;
    }

    private JsonArray buildNearbyObjects(WorldPoint playerLocation, long tick) {
        JsonArray objects = new JsonArray();
        if (playerLocation == null) {
            return objects;
        }

        List<HarnessWorldIndex.IndexedObject> indexedObjects =
                worldIndex.getNearbyObjects(playerLocation, client.getWorld(), tick, MAX_NEARBY_OBJECTS, WORLD_INDEX_MAX_AGE_TICKS);
        for (HarnessWorldIndex.IndexedObject indexedObject : indexedObjects) {
            JsonObject objectJson = new JsonObject();
            objectJson.addProperty("id", indexedObject.getId());
            objectJson.addProperty("name", indexedObject.getName());
            WorldPoint location = indexedObject.getLocation();
            objectJson.addProperty("distance", location == null ? -1 : playerLocation.distanceTo(location));
            if (location != null) {
                objectJson.addProperty("worldX", location.getX());
                objectJson.addProperty("worldY", location.getY());
                objectJson.addProperty("plane", location.getPlane());
            }
            objects.add(objectJson);
        }
        return objects;
    }

    private JsonObject buildUiFlags() {
        JsonObject uiJson = new JsonObject();
        uiJson.addProperty("inventoryVisible", client.getItemContainer(InventoryID.INV) != null);
        uiJson.addProperty("bankOpen", client.getItemContainer(InventoryID.BANK) != null);
        uiJson.addProperty("dialogueOpen", hasContinueMenuEntry());
        return uiJson;
    }

    private JsonObject buildCamera() {
        JsonObject camera = new JsonObject();
        camera.addProperty("yaw", client.getCameraYaw());
        camera.addProperty("pitch", readCameraPitch());
        return camera;
    }

    private int readCameraPitch() {
        try {
            Object result = Client.class.getMethod("getCameraPitch").invoke(client);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            return -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private boolean hasContinueMenuEntry() {
        Menu menu = client.getMenu();
        if (menu == null || menu.getMenuEntries() == null) {
            return false;
        }
        for (MenuEntry entry : menu.getMenuEntries()) {
            String option = entry == null || entry.getOption() == null ? "" : entry.getOption();
            if ("continue".equalsIgnoreCase(option.trim())) {
                return true;
            }
        }
        return false;
    }
}
