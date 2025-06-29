package com.example;

import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;

public class MiningBotInventoryOverlay extends Overlay
{
    private final Client client;
    private final BotConfig config;
    private final AndromedaPlugin plugin;

    @Inject
    public MiningBotInventoryOverlay(Client client, AndromedaPlugin plugin, BotConfig config)
    {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(0.25f);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightInventoryItems())
        {
            return null;
        }

        Widget inventoryWidget = client.getWidget(InterfaceID.Inventory.ITEMS);
        if (inventoryWidget == null || inventoryWidget.isHidden())
        {
            return null;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory == null)
        {
            return null;
        }

        // Get configured ore IDs
        int[] oreIds = plugin.getOreIds();


        if (oreIds.length == 0)
        {
            return null;
        }

        // Highlight ore items in inventory
        for (Widget item : inventoryWidget.getDynamicChildren())
        {
            if (item != null && !item.isHidden())
            {
                int itemId = item.getItemId();
                
                // Check if this item is one of our target ores
                if (Arrays.stream(oreIds).anyMatch(id -> id == itemId))
                {
                    highlightInventorySlot(graphics, item);
                }
            }
        }

        return null;
    }

    private void highlightInventorySlot(Graphics2D graphics, Widget item)
    {
        Rectangle bounds = item.getBounds();
        if (bounds != null)
        {
            Stroke originalStroke = graphics.getStroke();
            Color originalColor = graphics.getColor();
            
            // Draw thick green border around ore items
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.setColor(Color.GREEN);
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            
            // Draw smaller inner border for better visibility
            graphics.setColor(Color.YELLOW);
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);
            
            // Restore original graphics settings
            graphics.setStroke(originalStroke);
            graphics.setColor(originalColor);
        }
    }
} 