package com.runepal.services;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.Random;

/**
 * Service responsible for game state queries including inventory management,
 * player status, and animation checking.
 */
@Singleton
@Slf4j
public class GameStateService {
    private final Client client;
    private final Random random = new Random();

    @Inject
    public GameStateService(Client client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    /**
     * Checks if the player's inventory is full (28 items).
     * 
     * @return true if inventory is full, false otherwise
     */
    public boolean isInventoryFull() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        return inventory != null && inventory.count() >= 28;
    }

    /**
     * Checks if the player's inventory is empty or only contains one item (like a pickaxe).
     * 
     * @return true if inventory is empty or only has one item, false otherwise
     */
    public boolean isInventoryEmpty() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        // We consider the inventory "empty" if it only contains a pickaxe (or is fully empty)
        return inventory != null && inventory.count() <= 1;
    }

    /**
     * Checks if the player is currently idle (no animation playing).
     * 
     * @return true if player is idle, false otherwise
     */
    public boolean isPlayerIdle() {
        return client.getLocalPlayer().getAnimation() == -1;
    }

    /**
     * Gets the item ID at a specific inventory slot.
     * 
     * @param slot the inventory slot (0-27)
     * @return the item ID, or -1 if slot is empty or invalid
     */
    public int getInventoryItemId(int slot) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return -1;
        Item item = inventory.getItem(slot);
        if (item == null) return -1;
        return item.getId();
    }

    /**
     * Gets a random point within an inventory item slot for clicking.
     * 
     * @param slot the inventory slot (0-27)
     * @return a random point within the item bounds, or Point(-1, -1) if not available
     */
    public Point getInventoryItemPoint(int slot) {
        Widget inventoryWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inventoryWidget == null || inventoryWidget.isHidden()) return new Point(-1, -1);
        Widget itemWidget = inventoryWidget.getChild(slot);
        if (itemWidget == null) return new Point(-1, -1);
        return getRandomPointInBounds(itemWidget.getBounds());
    }

    /**
     * Gets a random point within a bank item slot for clicking.
     * 
     * @param slot the bank slot
     * @return a random point within the item bounds, or Point(-1, -1) if not available
     */
    public Point getBankItemPoint(int slot) {
        Widget inventoryWidget = client.getWidget(InterfaceID.Bankmain.ITEMS);
        if (inventoryWidget == null) return new Point(-1, -1);
        Widget itemWidget = inventoryWidget.getChild(slot);
        if (itemWidget == null) return new Point(-1, -1);
        return getRandomPointInBounds(itemWidget.getBounds());
    }

    /**
     * Checks if the player has a specific item in their inventory.
     * 
     * @param itemId the item ID to check for
     * @return true if the player has the item, false otherwise
     */
    public boolean hasItem(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return false;
        }
        
        for (Item item : inventory.getItems()) {
            if (item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the current animation ID of the local player.
     * 
     * @return the current animation ID, or -1 if no animation is playing
     */
    public int getCurrentAnimation() {
        return client.getLocalPlayer().getAnimation();
    }

    /**
     * Checks if the local player is currently performing a specific animation.
     * 
     * @param animationId the animation ID to check for
     * @return true if the player is performing the specified animation, false otherwise
     */
    public boolean isCurrentAnimation(int animationId) {
        return getCurrentAnimation() == animationId;
    }

    /**
     * Checks if the local player is currently performing any mining animation.
     * 
     * @return true if the player is performing a mining animation, false otherwise
     */
    public boolean isCurrentlyMining() {
        int currentAnimation = getCurrentAnimation();
        return currentAnimation == AnimationID.HUMAN_MINING_BRONZE_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_IRON_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_STEEL_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_BLACK_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_MITHRIL_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_ADAMANT_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_RUNE_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_DRAGON_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY ||
                currentAnimation == AnimationID.HUMAN_MINING_INFERNAL_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_3A_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE ||
                currentAnimation == AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE;
    }

    /**
     * Checks if the local player is currently performing any cooking animation.
     * 
     * @return true if the player is performing a cooking animation, false otherwise
     */
    public boolean isCurrentlyCooking() {
        int currentAnimation = getCurrentAnimation();
        return currentAnimation == AnimationID.HUMAN_COOKING ||
                currentAnimation == AnimationID.HUMAN_COOKING_LOOP ||
                currentAnimation == AnimationID.HUMAN_FIRECOOKING;
    }

    /**
     * Checks if the local player is currently performing any woodcutting animation.
     * 
     * @return true if the player is performing a woodcutting animation, false otherwise
     */
    public boolean isCurrentlyWoodcutting() {
        int currentAnimation = getCurrentAnimation();
        return currentAnimation == AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_IRON_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_STEEL_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_BLACK_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_RUNE_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_3A_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE ||
                currentAnimation == AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE;
    }

    /**
     * Gets the current world location of the local player.
     * 
     * @return the player's current world location
     */
    public WorldPoint getPlayerLocation() {
        return client.getLocalPlayer().getWorldLocation();
    }

    /**
     * Gets a random point within the specified bounds.
     * 
     * @param bounds the rectangle bounds
     * @return a random point within the bounds, or Point(-1, -1) if bounds are empty
     */
    private Point getRandomPointInBounds(Rectangle bounds) {
        if (bounds.isEmpty()) {
            return new Point(-1, -1);
        }
        int x = bounds.x + random.nextInt(bounds.width);
        int y = bounds.y + random.nextInt(bounds.height);
        return new Point(x, y);
    }

    public Rectangle getMenuEntryBounds(MenuEntry entry, int entryIndex) {
        Menu menu = client.getMenu();

        if (!client.isMenuOpen()) {
            return null;
        }

        // Get menu base position and dimensions
        int menuX = menu.getMenuX();
        int menuY = menu.getMenuY();
        int menuWidth = menu.getMenuWidth();

        // Calculate font height (typical menu entry height)
        // RuneLite menus typically use a fixed height per entry
        int entryHeight = 15; // Standard menu entry height in pixels
        
        // Account for the menu header at the top
        // The menu has a header section before the actual entries start
        int menuHeaderHeight = 19; // Typical RuneLite menu header height

        // Calculate Y position for this specific entry
        // Add header offset to account for the space above the first menu entry
        int entryY = menuY + menuHeaderHeight + (entryIndex * entryHeight);

        // Compress the bounds slightly to avoid clicking outside the entry
        final int DEADZONE = 1;
        return new Rectangle(menuX, entryY + DEADZONE, menuWidth, entryHeight - (DEADZONE * 2));
    }

    public boolean isMouseOverObject(GameObject object) {
        return object.getConvexHull().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
    }

    public boolean isMouseOverNpc(NPC npc) {
        return npc.getConvexHull().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY());
    }

    public int getInventoryItemIndex(int itemId) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null) return -1;
        for (int i = 0; i < inventory.count(); i++) {
            Item item = inventory.getItem(i);
            if (item == null) continue;
            if (item.getId() == itemId) return i;
        }
        return -1;
    }
}