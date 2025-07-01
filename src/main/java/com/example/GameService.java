package com.example;

import com.example.entity.GameObjectEntity;
import com.example.entity.Interactable;
import com.example.entity.NpcEntity;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class GameService {
    private final Client client;
    private final RunepalPlugin plugin;
    private final Random random = new Random();

    @Inject
    public GameService(Client client, RunepalPlugin plugin) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
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
        return getRandomPointInBounds(itemWidget.getBounds());
    }

    public Point getRandomClickablePoint(NPC npc) {
        Shape clickbox = npc.getConvexHull();
        if (clickbox != null) {
            return getRandomPointInBounds(clickbox.getBounds());
        }

        return null;
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

    public NPC findNearestNpc(String[] npcNames) {
        if (npcNames.length == 0) {
            return null;
        }

        IndexedObjectSet<? extends NPC> npcs = plugin.getClient().getWorldView(-1).npcs();
        NPC nearestNpc = null;
        double nearestDistance = Double.MAX_VALUE;
        WorldPoint playerLocation = plugin.getClient().getLocalPlayer().getWorldLocation();

        for (NPC npc : npcs) {
            if (npc == null || npc.getName() == null) {
                continue;
            }

            // Check if NPC name matches our target list
            boolean nameMatches = Arrays.stream(npcNames)
                    .anyMatch(targetName -> npc.getName().toLowerCase().contains(targetName.toLowerCase().trim()));

            if (!nameMatches) {
                continue;
            }

            // Check if NPC is alive
            if (npc.getHealthRatio() == 0) {
                continue;
            }

            // Check if NPC is already in combat with another player
            if (npc.getInteracting() != null && npc.getInteracting() != plugin.getClient().getLocalPlayer()) {
                continue;
            }

            // Calculate distance
            WorldPoint npcLocation = npc.getWorldLocation();
            double distance = npcLocation.distanceTo(playerLocation);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestNpc = npc;
            }
        }

        return nearestNpc;
    }

    /**
     * Generic method to find the nearest interactable entity that matches the given predicate.
     * This replaces the separate findNearestGameObject and findNearestNpc methods.
     * 
     * @param predicate the condition to match
     * @return the nearest matching Interactable, or null if none found
     */
    public Interactable findNearest(Predicate<Interactable> predicate) {
        Stream<Interactable> allInteractables = getAllInteractables();
        
        WorldPoint playerLocation = getPlayerLocation();
        
        return allInteractables
                .filter(predicate)
                .min(Comparator.comparingInt(interactable -> 
                    interactable.getWorldLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets a random clickable point within the bounds of any interactable entity.
     * This replaces the separate getRandomClickablePoint methods for GameObject and NPC.
     * 
     * @param interactable the interactable entity
     * @return a random point within the clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Interactable interactable) {
        if (interactable == null) {
            return new Point(-1, -1);
        }
        
        Shape clickbox = interactable.getClickbox();
        if (clickbox == null) {
            return new Point(-1, -1);
        }
        
        Rectangle bounds = clickbox.getBounds();
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }

        // Try to find a point within the actual shape (up to 10 attempts)
        for (int i = 0; i < 10; i++) {
            Point randomPoint = new Point(
                    bounds.x + random.nextInt(bounds.width),
                    bounds.y + random.nextInt(bounds.height)
            );

            if (clickbox.contains(randomPoint)) {
                return randomPoint;
            }
        }
        
        // Fallback to center if we fail to find a point within the shape
        return new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
    }

    /**
     * Gets all interactable entities in the current scene.
     * This includes both GameObjects and NPCs wrapped in their respective entity adapters.
     * 
     * @return a stream of all interactable entities
     */
    private Stream<Interactable> getAllInteractables() {
        List<Interactable> interactables = new ArrayList<>();
        
        // Add all GameObjects
        Scene scene = client.getWorldView(-1).getScene();
        Tile[][][] tiles = scene.getTiles();
        int z = client.getWorldView(-1).getPlane();

        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) {
                    continue;
                }
                for (GameObject gameObject : tile.getGameObjects()) {
                    if (gameObject != null) {
                        interactables.add(new GameObjectEntity(gameObject));
                    }
                }
            }
        }
        
        // Add all NPCs
        IndexedObjectSet<? extends NPC> npcs = client.getWorldView(-1).npcs();
        for (NPC npc : npcs) {
            if (npc != null) {
                interactables.add(new NpcEntity(npc));
            }
        }
        
        return interactables.stream();
    }

    // --- Convenience methods using the new unified approach ---
    
    /**
     * Convenience method to find the nearest GameObject with any of the specified IDs.
     * Uses the new unified findNearest method internally.
     * 
     * @param ids the GameObject IDs to search for
     * @return the nearest matching GameObject, or null if none found
     */
    public GameObject findNearestGameObjectNew(int... ids) {
        Interactable result = findNearest(interactable -> {
            if (interactable instanceof GameObjectEntity) {
                GameObjectEntity gameObjectEntity = (GameObjectEntity) interactable;
                return Arrays.stream(ids).anyMatch(id -> id == gameObjectEntity.getId());
            }
            return false;
        });
        
        return result instanceof GameObjectEntity ? ((GameObjectEntity) result).getGameObject() : null;
    }
    
    /**
     * Convenience method to find the nearest NPC with any of the specified names.
     * Uses the new unified findNearest method internally.
     * 
     * @param npcNames the NPC names to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpcNew(String... npcNames) {
        if (npcNames.length == 0) {
            return null;
        }
        
        Interactable result = findNearest(interactable -> {
            if (interactable instanceof NpcEntity) {
                NpcEntity npcEntity = (NpcEntity) interactable;
                NPC npc = npcEntity.getNpc();
                
                // Check if NPC name matches our target list
                if (npc.getName() == null) {
                    return false;
                }
                
                boolean nameMatches = Arrays.stream(npcNames)
                        .anyMatch(targetName -> npc.getName().toLowerCase().contains(targetName.toLowerCase().trim()));
                
                if (!nameMatches) {
                    return false;
                }
                
                // Check if NPC is alive
                if (npc.getHealthRatio() == 0) {
                    return false;
                }
                
                // Check if NPC is already in combat with another player
                if (npc.getInteracting() != null && npc.getInteracting() != client.getLocalPlayer()) {
                    return false;
                }
                
                return true;
            }
            return false;
        });
        
        return result instanceof NpcEntity ? ((NpcEntity) result).getNpc() : null;
    }

    public boolean isItemInList(int itemId, int[] list) {
        for (int id : list) {
            if (itemId == id) {
                return true;
            }
        }
        return false;
    }

    public Point getRandomPointInBounds(Rectangle bounds) {
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }
        int x = bounds.x + random.nextInt(bounds.width);
        int y = bounds.y + random.nextInt(bounds.height);
        return new Point(x, y);
    }

    public boolean verifyHoverAction(String expectedAction, String expectedTarget) {
        // Get the menu entries that are present on hover
        MenuEntry[] menuEntries = client.getMenu().getMenuEntries();

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

    public WorldPoint getPlayerLocation() {
        return client.getLocalPlayer().getWorldLocation();
    }
} 