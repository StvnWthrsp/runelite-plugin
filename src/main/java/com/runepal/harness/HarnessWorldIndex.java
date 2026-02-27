package com.runepal.harness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;

public class HarnessWorldIndex {
    private final Map<Integer, IndexedNpc> npcsByIndex = new ConcurrentHashMap<>();
    private final Map<String, IndexedObject> objectsByKey = new ConcurrentHashMap<>();

    public void onNpcSpawned(NPC npc, long tick, int worldId) {
        if (npc == null || npc.getWorldLocation() == null) {
            return;
        }

        IndexedNpc indexedNpc = new IndexedNpc();
        indexedNpc.npcIndex = npc.getIndex();
        indexedNpc.id = npc.getId();
        indexedNpc.name = sanitize(npc.getName());
        indexedNpc.location = npc.getWorldLocation();
        indexedNpc.actions = readNpcActions(npc.getComposition());
        indexedNpc.lastSeenTick = tick;
        indexedNpc.worldId = worldId;
        npcsByIndex.put(indexedNpc.npcIndex, indexedNpc);
    }

    public void onNpcDespawned(NPC npc) {
        if (npc == null) {
            return;
        }
        npcsByIndex.remove(npc.getIndex());
    }

    public void onObjectSpawned(GameObject gameObject, ObjectComposition composition, long tick, int worldId) {
        if (gameObject == null || gameObject.getWorldLocation() == null) {
            return;
        }

        IndexedObject indexedObject = new IndexedObject();
        indexedObject.id = gameObject.getId();
        indexedObject.name = composition == null ? "" : sanitize(composition.getName());
        indexedObject.location = gameObject.getWorldLocation();
        indexedObject.lastSeenTick = tick;
        indexedObject.worldId = worldId;

        objectsByKey.put(buildObjectKey(gameObject), indexedObject);
    }

    public void onObjectDespawned(GameObject gameObject) {
        if (gameObject == null) {
            return;
        }
        objectsByKey.remove(buildObjectKey(gameObject));
    }

    public List<IndexedNpc> getNearbyNpcs(WorldPoint playerLocation, int worldId, long tick, int maxItems, int maxAgeTicks) {
        List<IndexedNpc> nearby = new ArrayList<>();
        if (playerLocation == null) {
            return nearby;
        }

        for (IndexedNpc npc : npcsByIndex.values()) {
            if (npc == null || npc.location == null) {
                continue;
            }
            if (npc.worldId != worldId) {
                continue;
            }
            if ((tick - npc.lastSeenTick) > maxAgeTicks) {
                continue;
            }
            nearby.add(npc.copy());
        }

        nearby.sort(Comparator.comparingInt(entry -> playerLocation.distanceTo(entry.location)));
        if (nearby.size() > maxItems) {
            return new ArrayList<>(nearby.subList(0, maxItems));
        }
        return nearby;
    }

    public List<IndexedObject> getNearbyObjects(WorldPoint playerLocation, int worldId, long tick, int maxItems, int maxAgeTicks) {
        List<IndexedObject> nearby = new ArrayList<>();
        if (playerLocation == null) {
            return nearby;
        }

        for (IndexedObject object : objectsByKey.values()) {
            if (object == null || object.location == null) {
                continue;
            }
            if (object.worldId != worldId) {
                continue;
            }
            if ((tick - object.lastSeenTick) > maxAgeTicks) {
                continue;
            }
            nearby.add(object.copy());
        }

        nearby.sort(Comparator.comparingInt(entry -> playerLocation.distanceTo(entry.location)));
        if (nearby.size() > maxItems) {
            return new ArrayList<>(nearby.subList(0, maxItems));
        }
        return nearby;
    }

    private List<String> readNpcActions(NPCComposition composition) {
        List<String> actions = new ArrayList<>();
        if (composition == null || composition.getActions() == null) {
            return actions;
        }
        for (String action : composition.getActions()) {
            if (action != null && !action.trim().isEmpty()) {
                actions.add(action.trim());
            }
        }
        return actions;
    }

    private String buildObjectKey(GameObject gameObject) {
        WorldPoint location = gameObject.getWorldLocation();
        return gameObject.getId() + ":" + location.getX() + ":" + location.getY() + ":" + location.getPlane();
    }

    private String sanitize(String value) {
        return value == null ? "" : value;
    }

    public static final class IndexedNpc {
        private int npcIndex;
        private int id;
        private String name;
        private WorldPoint location;
        private List<String> actions = new ArrayList<>();
        private long lastSeenTick;
        private int worldId;

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public WorldPoint getLocation() {
            return location;
        }

        public List<String> getActions() {
            return actions;
        }

        private IndexedNpc copy() {
            IndexedNpc copy = new IndexedNpc();
            copy.npcIndex = npcIndex;
            copy.id = id;
            copy.name = name;
            copy.location = location;
            copy.actions = new ArrayList<>(actions);
            copy.lastSeenTick = lastSeenTick;
            copy.worldId = worldId;
            return copy;
        }
    }

    public static final class IndexedObject {
        private int id;
        private String name;
        private WorldPoint location;
        private long lastSeenTick;
        private int worldId;

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public WorldPoint getLocation() {
            return location;
        }

        private IndexedObject copy() {
            IndexedObject copy = new IndexedObject();
            copy.id = id;
            copy.name = name;
            copy.location = location;
            copy.lastSeenTick = lastSeenTick;
            copy.worldId = worldId;
            return copy;
        }
    }
}
