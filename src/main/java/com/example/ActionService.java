package com.example;

import com.example.utils.ClickObstructionChecker;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.widgets.Widget;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.api.WallObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InterfaceID.MagicSpellbook;
import shortestpath.Transport;

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
    private final ClickObstructionChecker clickObstructionChecker;

    @Inject
    public ActionService(RunepalPlugin plugin, PipeService pipeService, GameService gameService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.pipeService = Objects.requireNonNull(pipeService, "pipeService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.clickObstructionChecker = new ClickObstructionChecker(plugin.getClient());
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

    /**
     * Use an inventory item with an optional menu action
     * @param slot inventory slot (0-27)
     * @param action the menu action to use (e.g., "Break", "Rub", destination name), null for left-click
     * @return true if the action was sent successfully
     */
    public boolean useInventoryItem(int slot, String action) {
        Point itemPoint = gameService.getInventoryItemPoint(slot);
        if (itemPoint.x == -1) {
            log.warn("Invalid inventory slot: {}", slot);
            return false;
        }

        if (action == null) {
            // Left click
            sendClickRequest(itemPoint, true);
            return true;
        } else {
            // Right click for menu action
            sendClickRequest(itemPoint, false); // Right click to open context menu
            
            // TODO: Add logic to select the specific menu option
            // For now, we'll simulate a left click after a short delay
            scheduler.schedule(() -> {
                sendClickRequest(itemPoint, true);
            }, 100, TimeUnit.MILLISECONDS);
            
            log.info("Used inventory item at slot {} with action '{}'", slot, action);
            return true;
        }
    }

    /**
     * Check if the player has the required runes for a teleport spell
     * @param teleport the teleport transport containing rune requirements
     * @return true if all required runes are available
     */
    public boolean hasRequiredRunes(Transport teleport) {
        if (teleport == null) {
            return false;
        }
        
        // Home teleports and some other teleports are free (no rune cost)
        if (teleport.getItemRequirements() == null || isHomeTeleport(teleport)) {
            log.info("Teleport '{}' requires no runes (free teleport)", teleport.getDisplayInfo());
            return true;
        }
        
        ItemContainer inventory = plugin.getClient().getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            log.warn("Cannot check runes - inventory not accessible");
            return false;
        }

        // For now, we'll assume most teleports with requirements are available
        // A full implementation would parse the Transport's TransportItems structure
        // to check for specific rune requirements
        
        String displayInfo = teleport.getDisplayInfo();
        if (displayInfo != null) {
            // Home teleports are always free
            if (displayInfo.toLowerCase().contains("home teleport")) {
                log.info("Home teleport '{}' requires no runes", displayInfo);
                return true;
            }
            
            // For other teleports, we'll do basic checks for common runes
            // This is still simplified but more accurate than always requiring law runes
            if (displayInfo.toLowerCase().contains("varrock") || 
                displayInfo.toLowerCase().contains("lumbridge") ||
                displayInfo.toLowerCase().contains("falador") ||
                displayInfo.toLowerCase().contains("camelot")) {
                // Basic city teleports typically need law runes
                boolean hasLawRunes = hasItem(inventory, ItemID.LAW_RUNE);
                log.info("Checking law runes for city teleport '{}': {}", displayInfo, hasLawRunes);
                return hasLawRunes;
            }
        }
        
        // For other teleports, assume they're available for now
        // In a production implementation, you'd parse the full requirements
        log.info("Assuming teleport '{}' requirements are met (simplified check)", displayInfo);
        return true;
    }
    
    /**
     * Check if this is a home teleport (which are free)
     * @param teleport the transport to check
     * @return true if this is a home teleport
     */
    private boolean isHomeTeleport(Transport teleport) {
        String displayInfo = teleport.getDisplayInfo();
        if (displayInfo == null) {
            return false;
        }
        
        String lowerDisplayInfo = displayInfo.toLowerCase();
        return lowerDisplayInfo.contains("home teleport") ||
               lowerDisplayInfo.equals("lumbridge home teleport") ||
               lowerDisplayInfo.equals("edgeville home teleport") ||
               lowerDisplayInfo.equals("lunar home teleport") ||
               lowerDisplayInfo.equals("arceuus home teleport");
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
        log.info("Casting home teleport: {}", spellName);
        
        // Home teleports can be cast by:
        // 1. Opening magic interface
        // 2. Clicking on home teleport spell (usually at fixed positions)
        // 3. Or using hotkeys if configured
        
        try {
            // Try opening the magic interface first
            // Magic interface widget ID varies by client mode
            Widget magicTab = plugin.getClient().getWidget(InterfaceID.MagicSpellbook.UNIVERSE); // Standard spellbook
            if (magicTab == null) {
                log.warn("Could not find magic interface.");
            }
            if (magicTab != null && !magicTab.isHidden()) {
                // Magic interface is already open, look for home teleport
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
            }
            
            // If magic interface isn't open, try to open it first
            // Click on magic tab (F6 hotkey or tab click)
            return openMagicInterfaceAndCastHome();
            
        } catch (Exception e) {
            log.error("Error casting home teleport: {}", e.getMessage());
            return false;
        }
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

        return null;
    }
    
    /**
     * Open magic interface and cast home teleport
     * @return true if successful
     */
    private boolean openMagicInterfaceAndCastHome() {
        // Try clicking on the magic tab first
        Widget magicTab = plugin.getClient().getWidget(164, 1);
        if (magicTab == null) {
            // Try using F6 hotkey to open magic
            log.info("Attempting to open magic interface with F6 hotkey");
            sendKeyRequest("/key_hold", "F6");
            
            // Schedule the home teleport click after a short delay
            scheduler.schedule(() -> {
                sendKeyRequest("/key_release", "F6");
                Widget homeTeleportSpell = findHomeTeleportWidget();
                if (homeTeleportSpell != null) {
                    Point spellPoint = new Point(
                        homeTeleportSpell.getCanvasLocation().getX() + homeTeleportSpell.getWidth() / 2,
                        homeTeleportSpell.getCanvasLocation().getY() + homeTeleportSpell.getHeight() / 2
                    );
                    sendClickRequest(spellPoint, true);
                    log.info("Clicked home teleport after opening magic interface");
                } else {
                    log.warn("Could not find home teleport widget after opening magic interface");
                }
            }, 200, TimeUnit.MILLISECONDS);
            
            return true;
        }
        
        return false;
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

        Point clickPoint = gameService.getRandomClickablePoint(gameObject);
        if (clickObstructionChecker.isClickObstructed(clickPoint)) {
            log.warn("Click point is obstructed");
            return false;
        }
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for game object {}", gameObject.getId());
            return false;
        }

        log.info("Interacting with game object {} using action '{}'", gameObject.getId(), action);
        
        // For most interactions, a left click will work
        // For specific actions, you might need right-click menu handling
        sendClickRequest(clickPoint, true);
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

    /**
     * Check if inventory contains a specific item
     * @param inventory the inventory container
     * @param itemId the item ID to check for
     * @return true if the item is found
     */
    private boolean hasItem(ItemContainer inventory, int itemId) {
        Item[] items = inventory.getItems();
        for (Item item : items) {
            if (item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    public void sendClickRequest(Point point, boolean move) {
        log.info("Sending click request to point: {}, move: {}", point, move);
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

    /**
     * Use an item on a game object (e.g., raw fish on cooking range)
     * @param itemId the item ID to use
     * @param gameObject the game object to use the item on
     */
    public void sendUseItemOnObjectRequest(int itemId, GameObject gameObject) {
        if (gameObject == null) {
            log.warn("Cannot use item on null game object");
            return;
        }
        
        // This would involve finding the item in inventory and using it on the object
        // For now, we'll simulate a right-click -> use item workflow
        Point clickPoint = gameService.getRandomClickablePoint(gameObject);
        if (clickPoint.x == -1) {
            log.warn("Could not get clickable point for game object {}", gameObject.getId());
            return;
        }
        
        log.info("Using item {} on game object {}", itemId, gameObject.getId());
        // In a real implementation, this would require:
        // 1. Right-click on the item in inventory
        // 2. Select "Use" from the context menu  
        // 3. Click on the target object
        // For now, we'll just click on the object directly
        sendClickRequest(clickPoint, true);
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
