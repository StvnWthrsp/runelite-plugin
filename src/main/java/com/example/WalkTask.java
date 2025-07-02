package com.example;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;
import shortestpath.Transport;
import shortestpath.TransportType;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Slf4j
public class WalkTask implements BotTask {

    private enum WalkState {
        IDLE,
        CALCULATING_PATH,
        WALKING,
        EXECUTING_TELEPORT,
        OPENING_DOOR,
        WAITING_FOR_TRANSPORT,
        FAILED,
        FINISHED
    }

    @Getter
    private final WorldPoint destination;
    private final RunepalPlugin plugin;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;
    private final GameService gameService;

    private WalkState currentState = WalkState.IDLE;
    private List<WorldPoint> path;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;
    private final ActionService actionService;
    
    // Transport execution state
    private Transport pendingTransport;
    private long transportStartTime;
    private static final long TRANSPORT_TIMEOUT_MS = 30000; // 30 seconds
    private int pathIndex = 0;

    public WalkTask(RunepalPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination, ActionService actionService, GameService gameService) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.pathfinderConfig = pathfinderConfig;
        this.destination = destination;
        this.gameService = gameService;
        ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("walk-task-%d").build();
        this.pathfinderExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
        this.actionService = actionService;
    }

    @Override
    public void onStart() {
        log.info("Starting enhanced walk task to {}", destination);
        this.currentState = WalkState.IDLE;
        this.pathIndex = 0;
    }

    @Override
    public void onLoop() {
        switch (currentState) {
            case IDLE:
                calculatePath();
                break;
            case CALCULATING_PATH:
                checkPathCalculation();
                break;
            case WALKING:
                handleWalking();
                break;
            case EXECUTING_TELEPORT:
                handleTeleportExecution();
                break;
            case OPENING_DOOR:
                handleDoorOpening();
                break;
            case WAITING_FOR_TRANSPORT:
                handleTransportWait();
                break;
            default:
                break;
        }
    }

    private void calculatePath() {
        WorldPoint start = client.getLocalPlayer().getWorldLocation();
        if (start.distanceTo(destination) < 2) {
            log.info("Already at destination.");
            currentState = WalkState.FINISHED;
            return;
        }

        log.info("Calculating enhanced path from {} to {}", start, destination);
        int startPacked = WorldPointUtil.packWorldPoint(start);
        int endPacked = WorldPointUtil.packWorldPoint(destination);

        pathfinderConfig.refresh();
        pathfinder = new Pathfinder(pathfinderConfig, startPacked, Collections.singleton(endPacked));
        pathfinderFuture = pathfinderExecutor.submit(pathfinder);
        currentState = WalkState.CALCULATING_PATH;
    }

    private void checkPathCalculation() {
        if (pathfinder == null || !pathfinder.isDone()) {
            return;
        }

        List<Integer> resultPath = pathfinder.getPath();
        if (resultPath.isEmpty()) {
            log.warn("No path found to {}", destination);
            currentState = WalkState.FAILED;
            return;
        }

        this.path = resultPath.stream()
                .map(WorldPointUtil::unpackWorldPoint)
                .collect(Collectors.toList());
        log.info("Enhanced path calculated with {} steps (including potential teleports/doors).", this.path.size());
        currentState = WalkState.WALKING;
        pathIndex = 0;
    }

    private void handleWalking() {
        WorldPoint currentLocation = client.getLocalPlayer().getWorldLocation();

        if (currentLocation.distanceTo(destination) < 2) {
            log.info("Arrived at destination {}", destination);
            currentState = WalkState.FINISHED;
            return;
        }

        // Update path index based on current location
        updatePathIndex(currentLocation);

        if (pathIndex >= path.size()) {
            log.warn("Reached end of path but not at destination. Recalculating...");
            currentState = WalkState.IDLE;
            return;
        }

        WorldPoint nextStep = path.get(Math.min(pathIndex + 1, path.size() - 1));
        
        // Check if the next step requires a transport (teleport/door)
        Transport transport = findTransportBetween(currentLocation, nextStep);
        
        if (transport != null) {
            log.info("Found transport: {} from {} to {}", transport.getDisplayInfo(), currentLocation, nextStep);
            pendingTransport = transport;
            
            if (TransportType.isTeleport(transport.getType())) {
                currentState = WalkState.EXECUTING_TELEPORT;
                transportStartTime = System.currentTimeMillis();
            } else if (isDoorTransport(transport)) {
                currentState = WalkState.OPENING_DOOR;
                transportStartTime = System.currentTimeMillis();
            } else {
                // Other transport types (boats, etc.)
                executeTransport(transport);
                currentState = WalkState.WAITING_FOR_TRANSPORT;
                transportStartTime = System.currentTimeMillis();
            }
            return;
        }

        // Normal walking logic
        if (client.getLocalDestinationLocation() == null) {
            WorldPoint target = getNextMinimapTarget();
            if (target != null) {
                walkTo(target);
            } else {
                log.warn("Could not find a reachable target on the minimap. Recalculating path.");
                currentState = WalkState.IDLE;
            }
        }
    }

    private void updatePathIndex(WorldPoint currentLocation) {
        // Update path index to current position
        for (int i = pathIndex; i < path.size(); i++) {
            if (path.get(i).distanceTo(currentLocation) < 3) {
                pathIndex = i;
            } else {
                break;
            }
        }
    }

    private Transport findTransportBetween(WorldPoint from, WorldPoint to) {
        int fromPacked = WorldPointUtil.packWorldPoint(from);
        int toPacked = WorldPointUtil.packWorldPoint(to);
        
        // Check regular transports
        Set<Transport> transports = pathfinderConfig.getTransports().get(fromPacked);
        if (transports != null) {
            for (Transport transport : transports) {
                if (transport.getDestination() == toPacked) {
                    return transport;
                }
            }
        }
        
        // Check teleports (origin = UNDEFINED)
        Set<Transport> teleports = pathfinderConfig.getTransports().get(WorldPointUtil.UNDEFINED);
        if (teleports != null) {
            for (Transport teleport : teleports) {
                if (teleport.getDestination() == toPacked) {
                    return teleport;
                }
            }
        }
        
        return null;
    }

    private boolean isDoorTransport(Transport transport) {
        return transport.getDisplayInfo() != null && 
               transport.getDisplayInfo().toLowerCase().contains("door");
    }

    private void handleTeleportExecution() {
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Teleport execution timed out");
            currentState = WalkState.FAILED;
            return;
        }

        if (pendingTransport == null) {
            log.error("No pending transport for teleport execution");
            currentState = WalkState.WALKING;
            return;
        }

        boolean success = executeTeleport(pendingTransport);
        if (success) {
            log.info("Teleport initiated: {}", pendingTransport.getDisplayInfo());
            currentState = WalkState.WAITING_FOR_TRANSPORT;
        } else {
            log.warn("Failed to execute teleport, continuing with walking");
            currentState = WalkState.WALKING;
        }
    }

    private void handleDoorOpening() {
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Door opening timed out");
            currentState = WalkState.WALKING;
            return;
        }

        if (pendingTransport == null) {
            log.error("No pending transport for door opening");
            currentState = WalkState.WALKING;
            return;
        }

        boolean success = openDoor(pendingTransport);
        if (success) {
            log.info("Door opening initiated: {}", pendingTransport.getDisplayInfo());
            currentState = WalkState.WAITING_FOR_TRANSPORT;
        } else {
            log.warn("Failed to open door, continuing with walking");
            currentState = WalkState.WALKING;
        }
    }

    private void handleTransportWait() {
        if (System.currentTimeMillis() - transportStartTime > TRANSPORT_TIMEOUT_MS) {
            log.warn("Transport wait timed out");
            currentState = WalkState.WALKING;
            pendingTransport = null;
            return;
        }

        // Check if transport completed (player moved to destination)
        WorldPoint currentLocation = client.getLocalPlayer().getWorldLocation();
        WorldPoint expectedDestination = WorldPointUtil.unpackWorldPoint(pendingTransport.getDestination());
        
        if (currentLocation.distanceTo(expectedDestination) < 5) {
            log.info("Transport completed successfully");
            pathIndex++; // Move to next step in path
            currentState = WalkState.WALKING;
            pendingTransport = null;
        }
        
        // If player is not moving and has been waiting, might need to continue walking
        if (client.getLocalDestinationLocation() == null && 
            System.currentTimeMillis() - transportStartTime > 5000) {
            log.info("Transport appears complete, resuming walking");
            currentState = WalkState.WALKING;
            pendingTransport = null;
        }
    }

    private boolean executeTeleport(Transport teleport) {
        switch (teleport.getType()) {
            case TELEPORTATION_ITEM:
                return useTeleportationItem(teleport);
            case TELEPORTATION_SPELL:
                return castTeleportSpell(teleport);
            case TELEPORTATION_MINIGAME:
                return useTeleportationMinigame(teleport);
            default:
                log.warn("Unsupported teleport type: {}", teleport.getType());
                return false;
        }
    }

    private boolean useTeleportationItem(Transport teleport) {
        // Parse item requirement from transport
        int[] itemIds = parseItemIds(teleport);
        
        if (itemIds.length == 0) {
            log.warn("No item IDs found for teleport: {}", teleport.getDisplayInfo());
            return false;
        }
        
        // Find the item in inventory
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return false;
        }
        
        for (int itemId : itemIds) {
            Item[] items = inventory.getItems();
            for (int i = 0; i < items.length; i++) {
                if (items[i].getId() == itemId) {
                    log.info("Using teleport item {} (ID: {})", teleport.getDisplayInfo(), itemId);
                    
                    // Determine the correct menu action based on teleport destination
                    String teleportOption = extractTeleportOption(teleport.getDisplayInfo());
                    if (teleportOption != null) {
                        return actionService.useInventoryItem(i, teleportOption);
                    } else {
                        // Default to "Break" for consumable teleports or right-click for others
                        return actionService.useInventoryItem(i, teleport.isConsumable() ? "Break" : null);
                    }
                }
            }
        }
        
        log.warn("Required teleport item not found in inventory for: {}", teleport.getDisplayInfo());
        return false;
    }

    private boolean castTeleportSpell(Transport teleport) {
        String spellName = teleport.getDisplayInfo();
        if (spellName == null) {
            return false;
        }
        
        log.info("Casting teleport spell: {}", spellName);
        
        // Check if we have required runes (this would need to be implemented in ActionService)
        if (!actionService.hasRequiredRunes(teleport)) {
            log.warn("Missing required runes for spell: {}", spellName);
            return false;
        }
        
        return actionService.castSpell(spellName);
    }

    private boolean useTeleportationMinigame(Transport teleport) {
        // Handle minigame teleports (like Pest Control portal, etc.)
        log.info("Using minigame teleport: {}", teleport.getDisplayInfo());
        
        // This would need specific logic for each minigame teleport
        // For now, just try to click on the destination area
        WorldPoint destination = WorldPointUtil.unpackWorldPoint(teleport.getDestination());
        walkTo(destination);
        return true; // Return true to indicate we attempted the teleport
    }

    private boolean openDoor(Transport doorTransport) {
        int objectId = extractObjectId(doorTransport.getDisplayInfo());
        if (objectId == -1) {
            log.warn("Could not extract object ID from door transport: {}", doorTransport.getDisplayInfo());
            return false;
        }
        
        // Find the door object
        GameObject door = gameService.findNearestGameObject(objectId);
        if (door == null) {
            log.warn("Door object {} not found nearby", objectId);
            return false;
        }
        
        log.info("Opening door: {} (Object ID: {})", doorTransport.getDisplayInfo(), objectId);
        return actionService.interactWithGameObject(door, "Open");
    }

    private void executeTransport(Transport transport) {
        // Handle other transport types like boats, etc.
        log.info("Executing transport: {}", transport.getDisplayInfo());
        
        // This could be expanded for specific transport types
        // For now, just try to interact with objects at the origin location
        WorldPoint origin = WorldPointUtil.unpackWorldPoint(transport.getOrigin());
        walkTo(origin);
    }

    private int[] parseItemIds(Transport teleport) {
        // Parse item IDs from the transport's item requirements
        if (teleport.getItemRequirements() == null) {
            return new int[0];
        }
        
        // This is a simplified implementation - the actual parsing would be more complex
        // based on the TransportItems structure
        return new int[0]; // Placeholder - would need to implement based on TransportItems
    }

    private String extractTeleportOption(String displayInfo) {
        if (displayInfo == null) return null;
        
        // Extract teleport destination from display info
        // Examples: "Games necklace: Barbarian Outpost" -> "Barbarian Outpost"
        if (displayInfo.contains(":")) {
            return displayInfo.split(":")[1].trim();
        }
        
        return null;
    }

    private int extractObjectId(String displayInfo) {
        if (displayInfo == null) return -1;
        
        // Extract object ID from display info like "Open Door 9398"
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(displayInfo);
        
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        return -1;
    }

    private WorldPoint getNextMinimapTarget() {
        for (int i = path.size() - 1; i >= 0; i--) {
            WorldPoint point = path.get(i);
            if (isPointOnMinimap(point)) {
                return point;
            }
        }
        
        // Fallback to current path position
        if (pathIndex < path.size()) {
            return path.get(pathIndex);
        }
        
        return null;
    }

    private boolean isPointOnMinimap(WorldPoint point) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), point);
        if (localPoint == null) {
            return false;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
        if (minimapPoint == null) {
            return false;
        }

        Widget minimapWidget = getMinimapDrawWidget();
        if (minimapWidget == null) {
            return false;
        }

        java.awt.Rectangle bounds = minimapWidget.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        int radius = Math.min(bounds.width / 2, bounds.height / 2);

        int distanceSq = (minimapPoint.getX() - centerX) * (minimapPoint.getX() - centerX) +
                         (minimapPoint.getY() - centerY) * (minimapPoint.getY() - centerY);

        int radiusSq = (radius - 5) * (radius - 5);

        return distanceSq <= radiusSq;
    }

    private Widget getMinimapDrawWidget() {
        Widget minimapWidget = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        }
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        }
        return minimapWidget;
    }

    @Override
    public void onStop() {
        log.info("Stopping enhanced walk task.");
        if (pathfinderFuture != null && !pathfinderFuture.isDone()) {
            pathfinderFuture.cancel(true);
        }
        pathfinderExecutor.shutdownNow();
    }

    @Override
    public boolean isFinished() {
        return currentState == WalkState.FINISHED || currentState == WalkState.FAILED;
    }

    @Override
    public boolean isStarted() {
        return currentState != null;
    }

    @Override
    public String getTaskName() {
        return "Enhanced Walking to " + destination.toString();
    }

    public void walkTo(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint != null) {
            net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
            if (minimapPoint != null) {
                log.info("Requesting walk to {} via minimap click at {}", worldPoint, minimapPoint);
                actionService.sendClickRequest(new java.awt.Point(minimapPoint.getX(), minimapPoint.getY()), true);
            } else {
                log.warn("Cannot walk to {}: not visible on minimap.", worldPoint);
            }
        } else {
            log.warn("Cannot walk to {}: not in scene.", worldPoint);
        }
    }
} 