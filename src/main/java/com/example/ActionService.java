package com.example;

import com.example.utils.ClickObstructionChecker;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;
import net.runelite.api.WallObject;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;

import javax.inject.Inject;

import java.awt.*;
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
    private final EventService eventService;
    private volatile boolean isCurrentlyDropping = false;
    private final ClickObstructionChecker clickObstructionChecker;
    private volatile boolean isCurrentlyInteracting;

    @Inject
    public ActionService(RunepalPlugin plugin, PipeService pipeService, GameService gameService, EventService eventService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.pipeService = Objects.requireNonNull(pipeService, "pipeService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.clickObstructionChecker = new ClickObstructionChecker(plugin.getClient());
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
     * Interact with a game object
     * @param gameObject the game object to interact with
     * @param action the action to perform (e.g., "Open", "Mine", "Cut")
     * @return true if the interaction was initiated
     */
    public boolean interactWithGameObject(GameObject gameObject, String action) {
        if (gameObject == null) {
            log.warn("Cannot interact with null game object");
            return false;
        }

        // Prevent concurrent interactions
        if (isCurrentlyInteracting) {
            log.debug("Already interacting with an object, ignoring new interaction request");
            return false;
        }

        Point clickPoint = gameService.getRandomClickablePoint(gameObject);
        if (clickObstructionChecker.isClickObstructed(clickPoint)) {
            log.warn("Click point is obstructed");
            // TODO: Rotate the camera so we can try again
            eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "Click point obstructed"));
            return false;
        }
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for game object {}", gameObject.getId());
            eventService.publish(new InteractionCompletedEvent(gameObject, action, false, "Could not get clickable point"));
            return false;
        }

        log.info("Interacting with game object {} using action '{}'", gameObject.getId(), action);
        isCurrentlyInteracting = true;
        eventService.publish(new InteractionStartedEvent(gameObject, action));

        // If mouse is not already over the gameObject, move the mouse
        // TODO: isMouseOverObject could be a helper method
        Shape convexHull = gameObject.getConvexHull();
        net.runelite.api.Point mousePosition = plugin.getClient().getMouseCanvasPosition();

        if (!convexHull.contains(mousePosition.getX(), mousePosition.getY())) {
            log.info("Mouse is not over the object, moving mouse and scheduling interaction");
            sendMouseMoveRequest(clickPoint);
            
            // Schedule the actual interaction after mouse movement
            scheduler.schedule(() -> {
                performInteractionAfterMouseMove(gameObject, action, clickPoint);
            }, 600, TimeUnit.MILLISECONDS);
            return true; // Return true since we're handling the interaction asynchronously
        }

        // Perform the actual interaction logic
        return performInteractionLogic(gameObject, action);
    }

    /**
     * Performs the interaction logic after mouse positioning
     */
    private void performInteractionAfterMouseMove(GameObject gameObject, String action, Point clickPoint) {
        performInteractionLogic(gameObject, action);
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

        // If left-click option matches action, just click
        if (Text.removeTags(menuEntries[menuEntries.length - 1].getOption()).equals(action)) {
            log.info("Left-click action {} matches expected action {}, sending click", Text.removeTags(menuEntries[menuEntries.length - 1].getTarget()), action);
            sendClickRequest(null, false);
            isCurrentlyInteracting = false;
            eventService.publish(new InteractionCompletedEvent(gameObject, action, true));
            return true;
        }

        // Otherwise, right-click and schedule the click on the menu entry
        log.info("Left-click action did not match, right-clicking");
        sendRightClickRequest();
        scheduler.schedule(() -> {
            MenuEntry[] currentMenuEntries = plugin.getClient().getMenu().getMenuEntries();
            boolean foundAction = false;

            log.info("----Menu----");
            for (int i = 0; i < currentMenuEntries.length; i++) {
                MenuEntry entry = currentMenuEntries[i];
                int visualIndex = currentMenuEntries.length - 1 - i;
                log.info("Visual {}, Index {}: {}", visualIndex, i, entry.getOption());
                if (action.equals(entry.getOption())) {
                    // Menu entries are indexed bottom-up, but visually displayed top-down
                    // So array index 0 = bottom menu item, array index length-1 = top menu item
                    // For getMenuEntryBounds, we need the visual index from top (0 = top item)
                    log.info("CLICKING_MENU: options matched, {} and {}", action, entry.getOption());
                    log.info("Clicking menu option at array index {} (visual index {})", i, visualIndex);
                    java.awt.Point menuEntryClickPoint = gameService.getRandomPointInBounds(gameService.getMenuEntryBounds(entry, visualIndex));
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
        }, 600, TimeUnit.MILLISECONDS);
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

    public void sendClickRequest(Point point, boolean move) {
        log.info("Sending click request to point: {}, move: {}", point, move);
        if (!move) {
            if (!pipeService.sendClick(0, 0, false)) {
                log.warn("Failed to send click command via pipe");
                plugin.stopBot();
            }
            return;
        }
		if (point == null || point.x == -1) {
			log.warn("Invalid point provided to sendClickRequest.");
			return;
		}
        if (!pipeService.sendClick(point.x, point.y, true)) {
            log.warn("Failed to send click command via pipe");
            plugin.stopBot();
        }
	}

    public void sendRightClickRequest() {
        log.info("Sending right click request");
        if (!pipeService.sendRightClick(0, 0, false)) {
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

    /**
     * Send a spacebar key press (commonly used for "Cook All" or similar actions)
     */
    public void sendSpacebarRequest() {
        log.info("Sending spacebar key press");
        if (!pipeService.sendKeyPress("space")) {
            log.warn("Failed to send spacebar hold command via pipe");
            plugin.stopBot();
            return;
        }
    }

}
