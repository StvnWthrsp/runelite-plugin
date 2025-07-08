package com.example.services;

import com.example.entity.GameObjectEntity;
import com.example.entity.Interactable;
import com.example.entity.NpcEntity;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Service responsible for finding and managing entities in the game world,
 * including GameObjects and NPCs.
 */
@Singleton
@Slf4j
public class EntityService {
    private final Client client;
    private final GameStateService gameStateService;

    @Inject
    public EntityService(Client client, GameStateService gameStateService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.gameStateService = Objects.requireNonNull(gameStateService, "gameStateService cannot be null");
    }

    /**
     * Finds the nearest GameObject with any of the specified IDs.
     * Legacy method for backward compatibility.
     * 
     * @param ids the GameObject IDs to search for
     * @return the nearest matching GameObject, or null if none found
     */
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

    /**
     * Finds the nearest NPC with any of the specified names.
     * Legacy method for backward compatibility.
     * 
     * @param npcNames the NPC names to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpc(String[] npcNames) {
        if (npcNames.length == 0) {
            return null;
        }

        IndexedObjectSet<? extends NPC> npcs = client.getWorldView(-1).npcs();
        NPC nearestNpc = null;
        double nearestDistance = Double.MAX_VALUE;
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

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
            if (npc.getInteracting() != null && npc.getInteracting() != client.getLocalPlayer()) {
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
        
        WorldPoint playerLocation = gameStateService.getPlayerLocation();
        
        return allInteractables
                .filter(predicate)
                .min(Comparator.comparingInt(interactable -> 
                    interactable.getWorldLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    /**
     * Gets all interactable entities in the current scene.
     * This includes both GameObjects and NPCs wrapped in their respective entity adapters.
     * 
     * @return a stream of all interactable entities
     */
    public Stream<Interactable> getAllInteractables() {
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

    /**
     * Convenience method to find the nearest NPC with a specific ID.
     * 
     * @param npcId the NPC ID to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpc(int npcId) {
        Interactable result = findNearest(interactable -> {
            if (interactable instanceof NpcEntity) {
                NpcEntity npcEntity = (NpcEntity) interactable;
                return npcEntity.getId() == npcId;
            }
            return false;
        });
        
        return result instanceof NpcEntity ? ((NpcEntity) result).getNpc() : null;
    }

    /**
     * Convenience method to find the nearest GameObject with a specific ID.
     * 
     * @param gameObjectId the GameObject ID to search for
     * @return the nearest matching GameObject, or null if none found
     */
    public GameObject findNearestGameObject(int gameObjectId) {
        Interactable result = findNearest(interactable -> {
            if (interactable instanceof GameObjectEntity) {
                GameObjectEntity gameObjectEntity = (GameObjectEntity) interactable;
                return gameObjectEntity.getId() == gameObjectId;
            }
            return false;
        });
        
        return result instanceof GameObjectEntity ? ((GameObjectEntity) result).getGameObject() : null;
    }
}