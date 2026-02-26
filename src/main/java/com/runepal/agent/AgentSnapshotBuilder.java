package com.runepal.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;

import javax.imageio.ImageIO;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
public class AgentSnapshotBuilder {
    private static final int INVENTORY_SLOTS = 28;
    private static final int MAX_MENU_ENTRIES = 12;
    private static final int MAX_NEARBY_NPCS = 20;
    private static final int MAX_NEARBY_OBJECTS = 20;
    private static final int MAX_OBJECT_DISTANCE = 20;

    private final RunepalPlugin plugin;
    private final Client client;
    private final BotConfig config;
    private final AgentSkillExecutor skillExecutor;

    public AgentSnapshotBuilder(RunepalPlugin plugin, BotConfig config, AgentSkillExecutor skillExecutor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.client = Objects.requireNonNull(plugin.getClient(), "client cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.skillExecutor = Objects.requireNonNull(skillExecutor, "skillExecutor cannot be null");
    }

    public JsonObject buildSnapshot(long tickNumber) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("timestamp", Instant.now().toString());
        snapshot.addProperty("gameTick", tickNumber);
        snapshot.addProperty("gameState", client.getGameState().name());

        JsonObject bot = new JsonObject();
        bot.addProperty("running", config.startBot());
        bot.addProperty("botType", config.botType().name());
        bot.addProperty("state", plugin.getCurrentState());
        snapshot.add("bot", bot);
        snapshot.add("agent", skillExecutor.getStatusSnapshot());

        Player localPlayer = client.getLocalPlayer();
        WorldPoint playerLocation = localPlayer != null ? localPlayer.getWorldLocation() : null;

        snapshot.add("player", buildPlayerSnapshot(localPlayer));
        snapshot.add("inventory", buildInventorySnapshot());
        snapshot.add("menu", buildMenuSnapshot());
        snapshot.add("nearbyNpcs", buildNearbyNpcsSnapshot(playerLocation));
        snapshot.add("nearbyObjects", buildNearbyObjectsSnapshot(playerLocation));

        if (config.agentIncludeScreenshots()) {
            String screenshot = captureScreenshotBase64Jpeg();
            if (screenshot != null) {
                snapshot.addProperty("screenshotJpegBase64", screenshot);
            }
        }

        return snapshot;
    }

    private JsonObject buildPlayerSnapshot(Player localPlayer) {
        JsonObject player = new JsonObject();
        if (localPlayer == null) {
            player.addProperty("present", false);
            return player;
        }

        player.addProperty("present", true);
        player.addProperty("name", localPlayer.getName() == null ? "" : localPlayer.getName());

        WorldPoint location = localPlayer.getWorldLocation();
        if (location != null) {
            player.addProperty("worldX", location.getX());
            player.addProperty("worldY", location.getY());
            player.addProperty("plane", location.getPlane());
        }

        player.addProperty("animation", localPlayer.getAnimation());
        player.addProperty("idle", localPlayer.getAnimation() == -1);
        player.addProperty("runEnergy", client.getEnergy());

        JsonObject health = new JsonObject();
        health.addProperty("current", client.getBoostedSkillLevel(Skill.HITPOINTS));
        health.addProperty("max", client.getRealSkillLevel(Skill.HITPOINTS));
        player.add("health", health);

        JsonObject prayer = new JsonObject();
        prayer.addProperty("current", client.getBoostedSkillLevel(Skill.PRAYER));
        prayer.addProperty("max", client.getRealSkillLevel(Skill.PRAYER));
        player.add("prayer", prayer);

        Actor interacting = localPlayer.getInteracting();
        if (interacting != null) {
            player.addProperty("interactingWith", getActorLabel(interacting));
        }

        return player;
    }

    private JsonArray buildInventorySnapshot() {
        JsonArray inventory = new JsonArray();
        ItemContainer itemContainer = client.getItemContainer(InventoryID.INV);

        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", slot);
            if (itemContainer == null) {
                entry.addProperty("itemId", -1);
                entry.addProperty("quantity", 0);
                inventory.add(entry);
                continue;
            }

            Item item = itemContainer.getItem(slot);
            if (item == null || item.getId() <= 0) {
                entry.addProperty("itemId", -1);
                entry.addProperty("quantity", 0);
            } else {
                entry.addProperty("itemId", item.getId());
                entry.addProperty("quantity", item.getQuantity());
            }
            inventory.add(entry);
        }

        return inventory;
    }

    private JsonObject buildMenuSnapshot() {
        JsonObject menuSnapshot = new JsonObject();
        menuSnapshot.addProperty("open", client.isMenuOpen());

        JsonArray entries = new JsonArray();
        Menu menu = client.getMenu();
        if (menu != null) {
            MenuEntry[] menuEntries = menu.getMenuEntries();
            int limit = Math.min(menuEntries.length, MAX_MENU_ENTRIES);
            for (int i = 0; i < limit; i++) {
                MenuEntry menuEntry = menuEntries[i];
                JsonObject entry = new JsonObject();
                entry.addProperty("option", menuEntry.getOption());
                entry.addProperty("target", menuEntry.getTarget());
                entries.add(entry);
            }
        }

        menuSnapshot.add("entries", entries);
        return menuSnapshot;
    }

    private JsonArray buildNearbyNpcsSnapshot(WorldPoint playerLocation) {
        JsonArray npcs = new JsonArray();
        IndexedObjectSet<? extends NPC> indexedNpcs = client.getWorldView(-1).npcs();
        List<NPC> sortedNpcs = new ArrayList<>();

        for (NPC npc : indexedNpcs) {
            if (npc == null || npc.getWorldLocation() == null) {
                continue;
            }
            sortedNpcs.add(npc);
        }

        sortedNpcs.sort(Comparator.comparingInt(npc -> distanceToPlayer(playerLocation, npc.getWorldLocation())));

        int limit = Math.min(sortedNpcs.size(), MAX_NEARBY_NPCS);
        for (int i = 0; i < limit; i++) {
            NPC npc = sortedNpcs.get(i);
            JsonObject npcSnapshot = new JsonObject();
            npcSnapshot.addProperty("id", npc.getId());
            npcSnapshot.addProperty("name", npc.getName() == null ? "" : npc.getName());
            npcSnapshot.addProperty("distance", distanceToPlayer(playerLocation, npc.getWorldLocation()));
            npcSnapshot.addProperty("animation", npc.getAnimation());

            WorldPoint location = npc.getWorldLocation();
            npcSnapshot.addProperty("worldX", location.getX());
            npcSnapshot.addProperty("worldY", location.getY());
            npcSnapshot.addProperty("plane", location.getPlane());

            Actor interacting = npc.getInteracting();
            if (interacting != null) {
                npcSnapshot.addProperty("interactingWith", getActorLabel(interacting));
            }

            npcs.add(npcSnapshot);
        }

        return npcs;
    }

    private JsonArray buildNearbyObjectsSnapshot(WorldPoint playerLocation) {
        JsonArray objects = new JsonArray();
        Scene scene = client.getWorldView(-1).getScene();
        if (scene == null) {
            return objects;
        }

        Tile[][][] tiles = scene.getTiles();
        int plane = client.getWorldView(-1).getPlane();
        List<GameObject> nearbyObjects = new ArrayList<>();

        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) {
                    continue;
                }

                for (GameObject gameObject : tile.getGameObjects()) {
                    if (gameObject == null || gameObject.getWorldLocation() == null) {
                        continue;
                    }

                    int distance = distanceToPlayer(playerLocation, gameObject.getWorldLocation());
                    if (playerLocation != null && distance > MAX_OBJECT_DISTANCE) {
                        continue;
                    }

                    nearbyObjects.add(gameObject);
                }
            }
        }

        nearbyObjects.sort(Comparator.comparingInt(obj -> distanceToPlayer(playerLocation, obj.getWorldLocation())));

        int limit = Math.min(nearbyObjects.size(), MAX_NEARBY_OBJECTS);
        for (int i = 0; i < limit; i++) {
            GameObject object = nearbyObjects.get(i);
            JsonObject objectSnapshot = new JsonObject();
            objectSnapshot.addProperty("id", object.getId());
            objectSnapshot.addProperty("distance", distanceToPlayer(playerLocation, object.getWorldLocation()));

            WorldPoint location = object.getWorldLocation();
            objectSnapshot.addProperty("worldX", location.getX());
            objectSnapshot.addProperty("worldY", location.getY());
            objectSnapshot.addProperty("plane", location.getPlane());
            objects.add(objectSnapshot);
        }

        return objects;
    }

    private int distanceToPlayer(WorldPoint playerLocation, WorldPoint otherLocation) {
        if (playerLocation == null || otherLocation == null) {
            return Integer.MAX_VALUE;
        }
        return playerLocation.distanceTo(otherLocation);
    }

    private String getActorLabel(Actor actor) {
        if (actor instanceof Player) {
            Player player = (Player) actor;
            return player.getName() == null ? "Player" : player.getName();
        }

        if (actor instanceof NPC) {
            NPC npc = (NPC) actor;
            return npc.getName() == null ? "NPC:" + npc.getId() : npc.getName();
        }

        return actor.getClass().getSimpleName();
    }

    private String captureScreenshotBase64Jpeg() {
        Canvas canvas = client.getCanvas();
        if (canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            return null;
        }

        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        try {
            canvas.paint(graphics);
        } catch (Exception e) {
            log.debug("Failed to paint canvas for screenshot", e);
            return null;
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.debug("Failed to encode screenshot", e);
            return null;
        }
    }
}
