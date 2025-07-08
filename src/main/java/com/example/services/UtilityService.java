package com.example.services;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.util.Objects;

/**
 * Service containing general utility methods that don't belong to specific domains.
 */
@Singleton
@Slf4j
public class UtilityService {
    private final Client client;

    @Inject
    public UtilityService(Client client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    /**
     * Checks if an item ID is present in a list of item IDs.
     * 
     * @param itemId the item ID to search for
     * @param list the list of item IDs to search in
     * @return true if the item ID is found in the list, false otherwise
     */
    public boolean isItemInList(int itemId, int[] list) {
        for (int id : list) {
            if (itemId == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies that the current hover action matches the expected action.
     * Useful for ensuring that the correct menu option will be triggered by a left-click.
     * 
     * @param expectedAction the expected action text (e.g., "Mine", "Attack", "Take")
     * @param expectedTarget the expected target name (optional, can be null)
     * @return true if the hover action matches expectations, false otherwise
     */
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
        boolean actionMatches = action.equalsIgnoreCase(expectedAction);
        
        // If expectedTarget is provided, verify it as well
        if (expectedTarget != null && !expectedTarget.isEmpty()) {
            boolean targetMatches = target.toLowerCase().contains(expectedTarget.toLowerCase());
            return actionMatches && targetMatches;
        }
        
        return actionMatches;
    }
}