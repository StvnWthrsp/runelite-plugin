package com.runepal;

import com.runepal.services.WindmouseService;
import com.runepal.utils.ClickObstructionChecker;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.runelite.api.WallObject;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;
import com.runepal.entity.Interactable;
import com.runepal.entity.NpcEntity;
import com.runepal.entity.GameObjectEntity;

import javax.inject.Inject;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class ActionService {
    private final RunepalPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private final PipeService pipeService;
    private final GameService gameService;
    private final EventService eventService;
    private final BotConfig config;
    private final WindmouseService windmouseService;
    private volatile boolean isCurrentlyDropping = false;
    private final ClickObstructionChecker clickObstructionChecker;
    private volatile boolean isCurrentlyInteracting;
    
    // Map to track pending click actions for Windmouse movements
    private final ConcurrentHashMap<String, PendingClickAction> pendingClickActions = new ConcurrentHashMap<>();
    
    // Map to track pending interactions for Windmouse movements
    private final ConcurrentHashMap<String, PendingInteraction> pendingInteractions = new ConcurrentHashMap<>();

    @Inject
    public ActionService(RunepalPlugin plugin, PipeService pipeService, GameService gameService, EventService eventService, BotConfig config, WindmouseService windmouseService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.pipeService = Objects.requireNonNull(pipeService, "pipeService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.windmouseService = Objects.requireNonNull(windmouseService, "windmouseService cannot be null");
        this.clickObstructionChecker = new ClickObstructionChecker(plugin.getClient());
        
        // Subscribe to mouse movement completion events
        eventService.subscribe(MouseMovementCompletedEvent.class, this::handleMouseMovementCompleted);
    }
    
    /**
     * Internal class to track pending click actions after Windmouse movement
     */
    private static class PendingClickAction {
        final Point targetPoint;
        final boolean isRightClick;
        final long timestamp;
        
        PendingClickAction(Point targetPoint, boolean isRightClick) {
            this.targetPoint = targetPoint;
            this.isRightClick = isRightClick;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Internal class to track pending interactions after Windmouse movement
     */
    private static class PendingInteraction {
        final Interactable entity;
        final String action;
        final long timestamp;
        
        PendingInteraction(Interactable entity, String action) {
            this.entity = entity;
            this.action = action;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Handle mouse movement completion events from WindmouseService
     */
    private void handleMouseMovementCompleted(MouseMovementCompletedEvent event) {
        String movementId = event.getMovementId();
        PendingClickAction pendingAction = pendingClickActions.remove(movementId);
        PendingInteraction pendingInteraction = pendingInteractions.remove(movementId);
        
        if (pendingAction == null && pendingInteraction == null) {
            // No pending action for this movement
            return;
        }
        
        if (event.isCancelled()) {
            log.debug("Mouse movement {} was cancelled, skipping pending action", movementId);
            return;
        }
        
        // Calculate delay before action (2-5ms for realism)
        int minDelay = config.windmousePreClickDelay();
        int maxDelay = config.windmouseMaxPreClickDelay();
        int delay = ThreadLocalRandom.current().nextInt(minDelay, maxDelay + 1);
        
        // Handle pending click action
        if (pendingAction != null) {
            scheduler.schedule(() -> {
                if (pendingAction.isRightClick) {
                    executeRightClick(event.getFinalPosition());
                } else {
                    executeLeftClick(event.getFinalPosition());
                }
            }, delay, TimeUnit.MILLISECONDS);
            log.debug("Scheduled click for movement {} with {}ms delay", movementId, delay);
        }
        
        // Handle pending interaction
        if (pendingInteraction != null) {
            scheduler.schedule(() -> {
                if (pendingInteraction.entity instanceof GameObjectEntity) {
                    GameObject gameObject = ((GameObjectEntity) pendingInteraction.entity).getGameObject();
                    performInteractionLogic(gameObject, pendingInteraction.action);
                } else if (pendingInteraction.entity instanceof NpcEntity) {
                    NPC npc = ((NpcEntity) pendingInteraction.entity).getNpc();
                    performNpcInteractionLogic(npc, pendingInteraction.action);
                }
            }, delay, TimeUnit.MILLISECONDS);
            log.debug("Scheduled interaction for movement {} with {}ms delay", movementId, delay);
        }
    }

    /**
     * Drops all items currently in the inventory matching any IDs in the list
     * @param itemIds list of IDs of items to drop
     */
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

    /**
     * Check whether dropping is currently happening
     * @return true if power drop is not yet finished
     */
    public boolean isDropping() {
        return isCurrentlyDropping;
    }

        /**
     * Check whether interacting with an object is currently happening
     * @return true if interacting with an object is not yet finished
     */
    public boolean isInteracting() {
        return isCurrentlyInteracting;
    }

    /**
     * Cast a teleport spell
     * @param spellName the name of the spell to cast
     * @return true if the spell casting was initiated
     */
    public boolean castSpell(String spellName) {
        log.info("Attempting to cast spell: {}", spellName);
        
        if (spellName == null) {
            log.warn("Cannot cast null spell");
            return false;
        }
        
        // Handle home teleports - these are free and always available
        if (spellName.toLowerCase().contains("home teleport")) {
            return castHomeTeleport(spellName);
        }
        
        // Handle other common teleports
        if (spellName.toLowerCase().contains("varrock")) {
            return castCityTeleport("Varrock Teleport");
        }
        if (spellName.toLowerCase().contains("lumbridge")) {
            return castCityTeleport("Lumbridge Teleport");
        }
        if (spellName.toLowerCase().contains("falador")) {
            return castCityTeleport("Falador Teleport");
        }
        if (spellName.toLowerCase().contains("camelot")) {
            return castCityTeleport("Camelot Teleport");
        }
        
        // For other spells, try generic spell casting
        return castGenericSpell(spellName);
    }
    
    /**
     * Cast a home teleport spell (free spell)
     * @param spellName the home teleport spell name
     * @return true if casting was initiated
     */
    private boolean castHomeTeleport(String spellName) {
        // TODO: This retrieves widget info from the client. It works since it will run in client thread. But does it make sense in ActionService?
        log.info("Casting home teleport: {}", spellName);

        try {
            Widget homeTeleportSpell = findHomeTeleportWidget();
            if (homeTeleportSpell != null) {
                Point spellPoint = new Point(
                    homeTeleportSpell.getCanvasLocation().getX() + homeTeleportSpell.getWidth() / 2,
                    homeTeleportSpell.getCanvasLocation().getY() + homeTeleportSpell.getHeight() / 2
                );
                sendClickRequest(spellPoint, true);
                log.info("Clicked home teleport spell");
                return true;
            }
        } catch (Exception e) {
            log.error("Error casting home teleport: {}", e.getMessage());
            return false;
        }
        return true;
    }
    
    /**
     * Find the home teleport widget in the magic interface
     * @return the home teleport widget or null if not found
     */
    private Widget findHomeTeleportWidget() {
        // Home teleport is typically at a known position in the standard spellbook
        // Widget IDs may vary, so we'll try common locations
        
        // Standard spellbook home teleport widget
        Widget homeSpell = plugin.getClient().getWidget(MagicSpellbook.TELEPORT_HOME_STANDARD);
        if (homeSpell != null && !homeSpell.isHidden()) {
            return homeSpell;
        }

        // Lunar spellbook home teleport widget
        homeSpell = plugin.getClient().getWidget(MagicSpellbook.TELEPORT_HOME_LUNAR);
        if (homeSpell != null && !homeSpell.isHidden()) {
            return homeSpell;
        }

        // Arceuus spellbook home teleport widget
        homeSpell = plugin.getClient().getWidget(MagicSpellbook.TELEPORT_HOME_ARCEUUS);
        if (homeSpell != null && !homeSpell.isHidden()) {
            return homeSpell;
        }

        // Edgeville spellbook home teleport widget
        homeSpell = plugin.getClient().getWidget(MagicSpellbook.TELEPORT_HOME_ZAROS);
        if (homeSpell != null && !homeSpell.isHidden()) {
            return homeSpell;
        }
        log.info("Failed to find home teleport widget");
        return null;
    }
    
    /**
     * Open magic interface and cast home teleport
     */
    public void openMagicInterface() {
        int delay = 100;
        try {
            sendKeyRequest("/key_hold", "esc");
            scheduler.schedule(() -> {
                sendKeyRequest("/key_release", "esc");
            }, delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Error sending key press {}: {}", "esc", e.getMessage());
        }

        delay += 600;

        // Try using F6 hotkey to open spellbook
        try {
            sendKeyRequest("/key_hold", "F6");
            scheduler.schedule(() -> {
                sendKeyRequest("/key_release", "F6");
            }, delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Error sending key press {}: {}", "F6", e.getMessage());
        }
    }
    
    /**
     * Cast a city teleport spell (requires runes)
     * @param spellName the city teleport spell name
     * @return true if casting was initiated
     */
    private boolean castCityTeleport(String spellName) {
        log.info("Casting city teleport: {}", spellName);
        // Similar to home teleport but for city teleports
        // This would require finding the specific spell widget and clicking it
        // For now, return false as this needs more specific implementation
        log.warn("City teleport casting not fully implemented yet for: {}", spellName);
        return false;
    }
    
    /**
     * Cast a generic spell
     * @param spellName the spell name
     * @return true if casting was initiated
     */
    private boolean castGenericSpell(String spellName) {
        log.info("Attempting to cast generic spell: {}", spellName);
        // Generic spell casting logic would go here
        log.warn("Generic spell casting not fully implemented yet for: {}", spellName);
        return false;
    }

    /**
     * Interact with any interactable entity (NPC, GameObject, etc.)
     * @param entity the interactable entity to interact with
     * @param action the action to perform (e.g., "Attack", "Open", "Mine", "Cut")
     */
    public void interactWithEntity(Interactable entity, String action) {
        if (entity == null) {
            log.warn("Cannot interact with null entity");
            return;
        }

        // Prevent concurrent interactions
        if (isCurrentlyInteracting) {
            log.debug("Already interacting with an entity, ignoring new interaction request");
            return;
        }

        // Handle different entity types
        if (entity instanceof NpcEntity) {
            NPC npc = ((NpcEntity) entity).getNpc();
            interactWithNpcInternal(npc, action);
        } else if (entity instanceof GameObjectEntity) {
            GameObject gameObject = ((GameObjectEntity) entity).getGameObject();
            interactWithGameObjectInternal(gameObject, action);
        } else {
            log.warn("Unknown entity type: {}", entity.getClass().getSimpleName());
        }
    }

    /**
     * Interact with a game object
     * @param gameObject the game object to interact with
     * @param action the action to perform (e.g., "Open", "Mine", "Cut")
     */
    public void interactWithGameObject(GameObject gameObject, String action) {
        interactWithGameObjectInternal(gameObject, action);
    }

    /**
     * Internal method for interacting with game objects
     * @param gameObject the game object to interact with
     * @param action the action to perform
     */
    private void interactWithGameObjectInternal(GameObject gameObject, String action) {
        if (gameObject == null) {
            log.warn("Cannot interact with null game object");
            return;
        }

        // Prevent concurrent interactions
        if (isCurrentlyInteracting) {
            log.debug("Already interacting with an object, ignoring new interaction request");
            return;
        }

        Point clickPoint = gameService.getRandomClickablePoint(gameObject);
        if (clickObstructionChecker.isClickObstructed(clickPoint)) {
            log.warn("Click point is obstructed");
            eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "Click point obstructed"));
            return;
        }
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for game object {}", gameObject.getId());
            eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "Could not get clickable point"));
            return;
        }

        log.info("Interacting with game object {} using action '{}'", gameObject.getId(), action);
        isCurrentlyInteracting = true;
        eventService.publish(new InteractionStartedEvent(gameObject, action));

        // If mouse is not already over the gameObject, move the mouse
        if (!gameService.isMouseOverObject(gameObject)) {
            log.info("Mouse is not over the object, moving mouse and scheduling interaction");
            sendMouseMoveRequestWithPendingInteraction(clickPoint, new GameObjectEntity(gameObject), action);
            return;
        }

        // Perform the actual interaction logic
        performInteractionLogic(gameObject, action);
    }

    /**
     * Internal method for interacting with NPCs
     * @param npc the NPC to interact with
     * @param action the action to perform
     */
    private void interactWithNpcInternal(NPC npc, String action) {
        if (npc == null) {
            log.warn("Cannot interact with null NPC");
            return;
        }

        // Check if NPC is still valid (not dead, still exists)
        if (npc.getHealthRatio() == 0 && npc.getInteracting() != null) {
            log.warn("Cannot interact with dead NPC: {}", npc.getName());
            eventService.publish(new InteractionCompletedEvent(npc, action, false, "NPC is dead"));
            return;
        }

        Point clickPoint = gameService.getRandomClickablePoint(new NpcEntity(npc));
        if (clickObstructionChecker.isClickObstructed(clickPoint)) {
            log.warn("Click point is obstructed for NPC: {}", npc.getName());
            eventService.publish(new InteractionCompletedEvent(npc, action, false, "Click point obstructed"));
            return;
        }
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for NPC: {}", npc.getName());
            eventService.publish(new InteractionCompletedEvent(npc, action, false, "Could not get clickable point"));
            return;
        }

        log.info("Interacting with NPC {} using action '{}'", npc.getName(), action);
        isCurrentlyInteracting = true;
        eventService.publish(new InteractionStartedEvent(npc, action));

        // If mouse is not already over the NPC, move the mouse
        if (!gameService.isMouseOverNpc(npc)) {
            log.info("Mouse is not over the NPC, moving mouse and scheduling interaction");
            sendMouseMoveRequestWithPendingInteraction(clickPoint, new NpcEntity(npc), action);
            return;
        }

        // Perform the actual interaction logic
        performNpcInteractionLogic(npc, action);
    }

    /**
     * Core interaction logic for NPCs
     */
    private boolean performNpcInteractionLogic(NPC npc, String action) {
        // Check if NPC is still valid before interaction
        if (npc.getHealthRatio() == 0 && npc.getInteracting() != null) {
            log.warn("NPC {} died before interaction could complete", npc.getName());
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(npc, action, false, "NPC died"));
            return false;
        }

        // Get the menu entries that are present on hover
        MenuEntry[] menuEntries = plugin.getClient().getMenu().getMenuEntries();

        // If there is no menu, we have a problem
        if (menuEntries.length == 0) {
            log.warn("No menu detected for NPC {}", npc.getName());
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(npc, action, false, "No menu detected"));
            return false;
        }

        Point currentMousePoint = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());

        // If left-click option matches action, just click
        if (Text.removeTags(menuEntries[menuEntries.length - 1].getOption()).equals(action)) {
            log.info("Left-click action {} matches expected action {}, sending click", Text.removeTags(menuEntries[menuEntries.length - 1].getTarget()), action);
            sendClickRequest(currentMousePoint, false);
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(npc, action, true));
            return true;
        }

        // Otherwise, right-click and schedule the click on the menu entry
        log.info("Left-click action did not match, right-clicking");
        sendRightClickRequest(currentMousePoint);
        scheduler.schedule(() -> {
            MenuEntry[] currentMenuEntries = plugin.getClient().getMenu().getMenuEntries();
            boolean foundAction = false;

            log.trace("----Menu----");
            for (int i = 0; i < currentMenuEntries.length; i++) {
                MenuEntry entry = currentMenuEntries[i];
                int visualIndex = currentMenuEntries.length - 1 - i;
                log.trace("Visual {}, Index {}: {}", visualIndex, i, entry.getOption());
                if (action.equals(entry.getOption())) {
                    log.debug("CLICKING_MENU: options matched, {} and {}", action, entry.getOption());
                    log.debug("Clicking menu option at array index {} (visual index {})", i, visualIndex);
                    
                    // DEBUG: Show the menu entry bounds as an overlay (if enabled in config)
                    Rectangle menuBounds = gameService.getMenuEntryBounds(entry, visualIndex);
                    if (menuBounds != null) {
                        if (config.showMenuDebugOverlay()) {
                            plugin.getMenuDebugOverlay().addDebugRect(
                                menuBounds, 
                                String.format("Menu: %s (arr:%d vis:%d)", action, i, visualIndex), 
                                Color.RED
                            );
                            log.debug("DEBUG: Menu bounds for '{}': {}", action, menuBounds);
                            
                            // Clear the debug overlay after 1 seconds
                            scheduler.schedule(() -> {
                                plugin.getMenuDebugOverlay().clearDebugRects();
                            }, 1000, TimeUnit.MILLISECONDS);
                        }
                    } else {
                        log.warn("WARN: getMenuEntryBounds returned null for entry '{}' at visual index {}", action, visualIndex);
                    }

                    java.awt.Point menuEntryClickPoint = gameService.getRandomPointInBounds(menuBounds);
                    sendClickRequest(menuEntryClickPoint, true);
                    
                    isCurrentlyInteracting = false;
                    foundAction = true;
                    break;
                }
            }
            
            // Always reset the interaction flag and publish completion event
            isCurrentlyInteracting = false;
            if (foundAction) {
                eventService.publish(new InteractionCompletedEvent(npc, action, true));
            } else {
                log.warn("Right-click menu did not contain expected option {}", action);
                eventService.publish(new InteractionCompletedEvent(npc, action, false, "Menu option not found"));
            }
        }, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Core interaction logic shared by both immediate and delayed interactions
     */
    private boolean performInteractionLogic(GameObject gameObject, String action) {
        // Get the menu entries that are present on hover
        MenuEntry[] menuEntries = plugin.getClient().getMenu().getMenuEntries();

        // If there is no menu, we have a problem
        if (menuEntries.length == 0) {
            log.warn("No menu detected for game object {}", gameObject.getId());
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "No menu detected"));
            return false;
        }

        Point currentMousePoint = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());

        // If left-click option matches action, just click
        if (Text.removeTags(menuEntries[menuEntries.length - 1].getOption()).equals(action)) {
            log.info("Left-click action {} matches expected action {}, sending click", Text.removeTags(menuEntries[menuEntries.length - 1].getTarget()), action);
            sendClickRequest(currentMousePoint, false);
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(gameObject, action, true));
            return true;
        }

        // Otherwise, right-click and schedule the click on the menu entry
        log.info("Left-click action did not match, right-clicking");
        sendRightClickRequest(currentMousePoint);
        scheduler.schedule(() -> {
            MenuEntry[] currentMenuEntries = plugin.getClient().getMenu().getMenuEntries();
            boolean foundAction = false;

            log.trace("----Menu----");
            for (int i = 0; i < currentMenuEntries.length; i++) {
                MenuEntry entry = currentMenuEntries[i];
                int visualIndex = currentMenuEntries.length - 1 - i;
                log.trace("Visual {}, Index {}: {}", visualIndex, i, entry.getOption());
                if (action.equals(entry.getOption())) {
                    // Menu entries are indexed bottom-up, but visually displayed top-down
                    // So array index 0 = bottom menu item, array index length-1 = top menu item
                    // For getMenuEntryBounds, we need the visual index from top (0 = top item)
                    log.debug("CLICKING_MENU: options matched, {} and {}", action, entry.getOption());
                    log.debug("Clicking menu option at array index {} (visual index {})", i, visualIndex);
                    
                    // DEBUG: Show the menu entry bounds as an overlay (if enabled in config)
                    Rectangle menuBounds = gameService.getMenuEntryBounds(entry, visualIndex);
                    if (menuBounds != null) {
                        if (config.showMenuDebugOverlay()) {
                            plugin.getMenuDebugOverlay().addDebugRect(
                                menuBounds, 
                                String.format("Menu: %s (arr:%d vis:%d)", action, i, visualIndex), 
                                Color.RED
                            );
                            log.debug("DEBUG: Menu bounds for '{}': {}", action, menuBounds);
                            
                            // Clear the debug overlay after 1 seconds
                            scheduler.schedule(() -> {
                                plugin.getMenuDebugOverlay().clearDebugRects();
                            }, 1000, TimeUnit.MILLISECONDS);
                        }
                    } else {
                        log.warn("WARN: getMenuEntryBounds returned null for entry '{}' at visual index {}", action, visualIndex);
                    }

                    java.awt.Point menuEntryClickPoint = gameService.getRandomPointInBounds(menuBounds);
                    sendClickRequest(menuEntryClickPoint, true);
                    
                    isCurrentlyInteracting = false;
                    foundAction = true;
                    break;
                }
            }
            
            // Always reset the interaction flag and publish completion event
            isCurrentlyInteracting = false;
            if (foundAction) {
                eventService.publish(new InteractionCompletedEvent(gameObject, action, true));
            } else {
                log.warn("Right-click menu did not contain expected option {}", action);
                eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "Menu option not found"));
            }
        }, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean interactWithWallObject(WallObject wallObject, String action) {
        if (wallObject == null) {
            log.warn("Cannot interact with null wall object");
            return false;
        }

        Point clickPoint = gameService.getRandomClickablePoint(wallObject);
        if (clickObstructionChecker.isClickObstructed(clickPoint)) {
            log.warn("Click point is obstructed");
            // TODO: Rotate the camera so we can try again
            return false;
        }
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for wall object {}", wallObject.getId());
            return false;
        }

        log.info("Interacting with wall object {} using action '{}'", wallObject.getId(), action);
        sendClickRequest(clickPoint, true);
        return true;
    }

    public void sendClickRequest(Point clickPoint, boolean move) {
        log.info("Sending click request to point: {}, move: {}", clickPoint, move);
        if (!move) {
            if (plugin.isAutomationConnected()) {
                if (!pipeService.sendClick(0, 0, false)) {
                    log.warn("Failed to send click command via pipe");
                    plugin.stopBot();
                }
                return;
            }
            MouseEvent mousePressed = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON1);
            plugin.getClient().getCanvas().dispatchEvent(mousePressed);
            MouseEvent mouseReleased = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON1);
            plugin.getClient().getCanvas().dispatchEvent(mouseReleased);
            return;
        }
		if (clickPoint == null || clickPoint.x == -1) {
			log.warn("Invalid point provided to sendClickRequest.");
			return;
		}
        if (plugin.isAutomationConnected()) {
            if (!pipeService.sendClick(clickPoint.x, clickPoint.y, true)) {
                log.warn("Failed to send click command via pipe");
                plugin.stopBot();
            }
            return;
        }
        // Use Windmouse for movement and schedule click after completion
        Point currentMousePos = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());
        String movementId = UUID.randomUUID().toString();
        
        // Store pending click action
        pendingClickActions.put(movementId, new PendingClickAction(clickPoint, false));
        
        // Start Windmouse movement
        windmouseService.moveToPoint(currentMousePos, clickPoint, movementId);
        return;  // Click will be executed after movement completes
        // plugin.getClient().getCanvas().dispatchEvent(mouseReleased);
	}

    public void sendRightClickRequest(Point clickPoint) {
        log.info("Sending right click request");
        if (plugin.isAutomationConnected()) {
            if (!pipeService.sendRightClick(0, 0, false)) {
                log.warn("Failed to send click command via pipe");
                plugin.stopBot();
            }
            return;
        }
        // Use Windmouse for movement and schedule right-click after completion
        Point currentMousePos = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());
        String movementId = UUID.randomUUID().toString();
        
        // Store pending right-click action
        pendingClickActions.put(movementId, new PendingClickAction(clickPoint, true));
        
        // Start Windmouse movement
        windmouseService.moveToPoint(currentMousePos, clickPoint, movementId);
        return;  // Right-click will be executed after movement completes
        // plugin.getClient().getCanvas().dispatchEvent(mouseReleased);

    }

    public void sendMouseMoveRequest(Point point) {
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendMouseMoveRequest.");
			return;
		}
        if (plugin.isAutomationConnected()) {
            if (!pipeService.sendMouseMove(point.x, point.y)) {
                log.warn("Failed to send mouse move command via pipe");
                plugin.stopBot();
            }
            return;
        }
        // Use Windmouse for human-like movement when not connected
        Point currentMousePos = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());
        String movementId = UUID.randomUUID().toString();
        windmouseService.moveToPoint(currentMousePos, point, movementId);
	}

    /**
     * Send mouse move request with a pending interaction to be executed after movement completes
     */
    private void sendMouseMoveRequestWithPendingInteraction(Point point, Interactable entity, String action) {
        if (point == null || point.x == -1) {
            log.warn("Invalid point provided to sendMouseMoveRequestWithPendingInteraction.");
            return;
        }
        
        if (plugin.isAutomationConnected()) {
            // For Python automation, we still need to handle the delay differently
            // as the Python server doesn't generate movement completion events
            if (!pipeService.sendMouseMove(point.x, point.y)) {
                log.warn("Failed to send mouse move command via pipe");
                plugin.stopBot();
                return;
            }
            // Schedule the interaction with a fixed delay for Python automation
            scheduler.schedule(() -> {
                if (entity instanceof GameObjectEntity) {
                    GameObject gameObject = ((GameObjectEntity) entity).getGameObject();
                    performInteractionLogic(gameObject, action);
                } else if (entity instanceof NpcEntity) {
                    NPC npc = ((NpcEntity) entity).getNpc();
                    performNpcInteractionLogic(npc, action);
                }
            }, 100, TimeUnit.MILLISECONDS);
            return;
        }
        
        // Use Windmouse for human-like movement when not connected
        Point currentMousePos = new Point(plugin.getClient().getMouseCanvasPosition().getX(), plugin.getClient().getMouseCanvasPosition().getY());
        String movementId = UUID.randomUUID().toString();
        
        // Store pending interaction
        pendingInteractions.put(movementId, new PendingInteraction(entity, action));
        
        // Start Windmouse movement - interaction will be executed after movement completes
        windmouseService.moveToPoint(currentMousePos, point, movementId);
    }

	public void sendKeyRequest(String endpoint, String key) {
		boolean success;
        int keyCode;
        switch (key) {
            case "esc":
                keyCode = KeyEvent.VK_ESCAPE;
                break;
            case "F6":
                keyCode = KeyEvent.VK_F6;
                break;
            case "space":
                keyCode = KeyEvent.VK_SPACE;
                break;
            case "shift":
                keyCode = KeyEvent.VK_SHIFT;
                break;
            default:
                log.warn("Unknown key: {}", key);
                return;
        }
		switch (endpoint) {
			case "/key_hold":
                if (plugin.isAutomationConnected()) {
                    success = pipeService.sendKeyHold(key);
                } else {
                    KeyEvent keyPressed = new KeyEvent(plugin.getClient().getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, key.charAt(0));
                    plugin.getClient().getCanvas().dispatchEvent(keyPressed);
                    success = true;
                }
				break;
			case "/key_release":
                if (plugin.isAutomationConnected()) {
                    success = pipeService.sendKeyRelease(key);
                } else {
                    KeyEvent keyReleased = new KeyEvent(plugin.getClient().getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode, key.charAt(0));
                    plugin.getClient().getCanvas().dispatchEvent(keyReleased);
                    success = true;
                }
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

    /**
     * Send a spacebar key press (commonly used for "Cook All" or similar actions)
     */
    public void sendSpacebarRequest() {
        log.info("Sending spacebar key press");
        if (plugin.isAutomationConnected()) {
            if (!pipeService.sendKeyPress("space")) {
                log.warn("Failed to send spacebar hold command via pipe");
                plugin.stopBot();
            }
            return;
        }
        KeyEvent keyPressed = new KeyEvent(plugin.getClient().getCanvas(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        plugin.getClient().getCanvas().dispatchEvent(keyPressed);
        KeyEvent keyReleased = new KeyEvent(plugin.getClient().getCanvas(), KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' ');
        plugin.getClient().getCanvas().dispatchEvent(keyReleased);
    }

    
    /**
     * Execute a left click at the specified point
     */
    private void executeLeftClick(Point clickPoint) {
        MouseEvent mousePressed = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON1);
        plugin.getClient().getCanvas().dispatchEvent(mousePressed);
        MouseEvent mouseReleased = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON1);
        plugin.getClient().getCanvas().dispatchEvent(mouseReleased);
    }
    
    /**
     * Execute a right click at the specified point
     */
    private void executeRightClick(Point clickPoint) {
        MouseEvent mousePressed = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON3);
        plugin.getClient().getCanvas().dispatchEvent(mousePressed);
        MouseEvent mouseReleased = new MouseEvent(plugin.getClient().getCanvas(), MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, clickPoint.x, clickPoint.y, 1, false, MouseEvent.BUTTON3);
        plugin.getClient().getCanvas().dispatchEvent(mouseReleased);
    }

}
