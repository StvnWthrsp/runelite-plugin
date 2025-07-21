package com.runepal;

import com.runepal.entity.Interactable;
import com.runepal.services.GameStateService;
import com.runepal.services.EntityService;
import com.runepal.services.ClickService;
import com.runepal.services.UtilityService;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@Singleton
public class GameService {
    private final GameStateService gameStateService;
    private final EntityService entityService;
    private final ClickService clickService;
    private final UtilityService utilityService;

    @Inject
    public GameService(GameStateService gameStateService, EntityService entityService, 
                      ClickService clickService, UtilityService utilityService) {
        this.gameStateService = Objects.requireNonNull(gameStateService, "gameStateService cannot be null");
        this.entityService = Objects.requireNonNull(entityService, "entityService cannot be null");
        this.clickService = Objects.requireNonNull(clickService, "clickService cannot be null");
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService cannot be null");
    }

    public boolean isInventoryFull() {
        return gameStateService.isInventoryFull();
    }

    public boolean isInventoryEmpty() {
        return gameStateService.isInventoryEmpty();
    }

    public boolean isPlayerIdle() {
        return gameStateService.isPlayerIdle();
    }

    public GameObject findNearestGameObject(int... ids) {
        return entityService.findNearestGameObject(ids);
    }

    public int getInventoryItemId(int slot) {
        return gameStateService.getInventoryItemId(slot);
    }

    public Point getInventoryItemPoint(int slot) {
        return gameStateService.getInventoryItemPoint(slot);
    }

    public Point getBankItemPoint(int slot) {
        return gameStateService.getBankItemPoint(slot);
    }

    public Point getRandomClickablePoint(NPC npc) {
        return clickService.getRandomClickablePoint(npc);
    }

    public Point getRandomClickablePoint(GameObject gameObject) {
        return clickService.getRandomClickablePoint(gameObject);
    }

    public Point getRandomClickablePoint(TileObject tileObject) {
        return clickService.getRandomClickablePoint(tileObject);
    }

    public NPC findNearestNpc(String[] npcNames) {
        return entityService.findNearestNpc(npcNames);
    }

    /**
     * Generic method to find the nearest interactable entity that matches the given predicate.
     * This replaces the separate findNearestGameObject and findNearestNpc methods.
     * 
     * @param predicate the condition to match
     * @return the nearest matching Interactable, or null if none found
     */
    public Interactable findNearest(Predicate<Interactable> predicate) {
        return entityService.findNearest(predicate);
    }

    /**
     * Gets a random clickable point within the bounds of any interactable entity.
     * This replaces the separate getRandomClickablePoint methods for GameObject and NPC.
     * 
     * @param interactable the interactable entity
     * @return a random point within the clickable area, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Interactable interactable) {
        return clickService.getRandomClickablePoint(interactable);
    }

    /**
     * Gets a random clickable point within the bounds of a Widget.
     * This method handles UI widgets like bank buttons, interfaces, etc.
     * 
     * @param widget the widget to get a click point for
     * @return a random point within the widget bounds, or Point(-1, -1) if not available
     */
    public Point getRandomClickablePoint(Widget widget) {
        return clickService.getRandomClickablePoint(widget);
    }

    /**
     * Gets a random point within the specified bounds.
     * Useful for clicking within arbitrary rectangular areas.
     * 
     * @param bounds the rectangle bounds
     * @return a random point within the bounds, or Point(-1, -1) if bounds are empty
     */
    public Point getRandomPointInBounds(Rectangle bounds) {
        return clickService.getRandomPointInBounds(bounds);
    }

    /**
     * Gets all interactable entities in the current scene.
     * This includes both GameObjects and NPCs wrapped in their respective entity adapters.
     * 
     * @return a stream of all interactable entities
     */
    public Stream<Interactable> getAllInteractables() {
        return entityService.getAllInteractables();
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
        return entityService.findNearestGameObjectNew(ids);
    }
    
    /**
     * Convenience method to find the nearest NPC with any of the specified names.
     * Uses the new unified findNearest method internally.
     * 
     * @param npcNames the NPC names to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpcNew(String... npcNames) {
        return entityService.findNearestNpcNew(npcNames);
    }

    public boolean isItemInList(int itemId, int[] list) {
        return utilityService.isItemInList(itemId, list);
    }


    public boolean verifyHoverAction(String expectedAction, String expectedTarget) {
        return utilityService.verifyHoverAction(expectedAction, expectedTarget);
    }

    public WorldPoint getPlayerLocation() {
        return gameStateService.getPlayerLocation();
    }

    /**
     * Convenience method to find the nearest NPC with a specific ID.
     * 
     * @param npcId the NPC ID to search for
     * @return the nearest matching NPC, or null if none found
     */
    public NPC findNearestNpc(int npcId) {
        return entityService.findNearestNpc(npcId);
    }

    /**
     * Convenience method to find the nearest GameObject with a specific ID.
     * 
     * @param gameObjectId the GameObject ID to search for
     * @return the nearest matching GameObject, or null if none found
     */
    public GameObject findNearestGameObject(int gameObjectId) {
        return entityService.findNearestGameObject(gameObjectId);
    }

    /**
     * Checks if the player has a specific item in their inventory.
     * 
     * @param itemId the item ID to check for
     * @return true if the player has the item, false otherwise
     */
    public boolean hasItem(int itemId) {
        return gameStateService.hasItem(itemId);
    }

    /**
     * Gets the current animation ID of the local player.
     * 
     * @return the current animation ID, or -1 if no animation is playing
     */
    public int getCurrentAnimation() {
        return gameStateService.getCurrentAnimation();
    }

    /**
     * Checks if the local player is currently performing a specific animation.
     * 
     * @param animationId the animation ID to check for
     * @return true if the player is performing the specified animation, false otherwise
     */
    public boolean isCurrentAnimation(int animationId) {
        return gameStateService.isCurrentAnimation(animationId);
    }

    /**
     * Checks if the local player is currently performing any mining animation.
     * 
     * @return true if the player is performing a mining animation, false otherwise
     */
    public boolean isCurrentlyMining() {
        return gameStateService.isCurrentlyMining();
    }

    /**
     * Checks if the local player is currently performing any cooking animation.
     * 
     * @return true if the player is performing a cooking animation, false otherwise
     */
    public boolean isCurrentlyCooking() {
        return gameStateService.isCurrentlyCooking();
    }

    /**
     * Checks if the local player is currently performing any woodcutting animation.
     * 
     * @return true if the player is performing a woodcutting animation, false otherwise
     */
    public boolean isCurrentlyWoodcutting() {
        return gameStateService.isCurrentlyWoodcutting();
    }

    public Rectangle getMenuEntryBounds(MenuEntry entry, int entryIndex) {
        return gameStateService.getMenuEntryBounds(entry, entryIndex);
    }

    public boolean isMouseOverObject(GameObject object) {
        return gameStateService.isMouseOverObject(object);
    }

    public boolean isMouseOverNpc(NPC npc) {
        return gameStateService.isMouseOverNpc(npc);
    }

    public int getInventoryItemIndex(int itemId) {
        return gameStateService.getInventoryItemIndex(itemId);
    }
} 