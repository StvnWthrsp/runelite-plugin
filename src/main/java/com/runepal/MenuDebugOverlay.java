package com.runepal;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MenuDebugOverlay extends Overlay {
    private final Client client;
    private final List<DebugRect> debugRects = new ArrayList<>();
    
    @Inject
    public MenuDebugOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(OverlayPriority.HIGHEST);
    }
    
    public void addDebugRect(Rectangle bounds, String label, Color color) {
        if (bounds == null) {
            log.warn("Cannot add debug rect with null bounds");
            return;
        }
        if (color == null) {
            color = Color.RED; // Default color
        }
        
        debugRects.clear(); // Clear previous rectangles
        debugRects.add(new DebugRect(bounds, label, color));
        log.info("Added debug rect: {} with label: {}", bounds, label);
    }
    
    public void clearDebugRects() {
        debugRects.clear();
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (graphics == null) {
            return null;
        }
        
        for (DebugRect debugRect : debugRects) {
            if (debugRect == null || debugRect.bounds == null || debugRect.color == null) {
                log.warn("Skipping null debug rect or bounds");
                continue;
            }
            
            try {
                graphics.setColor(debugRect.color);
                graphics.setStroke(new BasicStroke(2));
                graphics.drawRect(
                    debugRect.bounds.x, 
                    debugRect.bounds.y, 
                    debugRect.bounds.width, 
                    debugRect.bounds.height
                );
            } catch (Exception e) {
                log.warn("Error rendering debug rect: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    private static class DebugRect {
        final Rectangle bounds;
        final String label;
        final Color color;
        
        DebugRect(Rectangle bounds, String label, Color color) {
            this.bounds = bounds;
            this.label = label;
            this.color = color;
        }
    }
}