package com.example;

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
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }
    
    public void addDebugRect(Rectangle bounds, String label, Color color) {
        debugRects.clear(); // Clear previous rectangles
        debugRects.add(new DebugRect(bounds, label, color));
        log.info("Added debug rect: {} with label: {}", bounds, label);
    }
    
    public void clearDebugRects() {
        debugRects.clear();
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        for (DebugRect debugRect : debugRects) {
            graphics.setColor(debugRect.color);
            graphics.setStroke(new BasicStroke(2));
            graphics.drawRect(
                debugRect.bounds.x, 
                debugRect.bounds.y, 
                debugRect.bounds.width, 
                debugRect.bounds.height
            );
            
            // Draw label
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("Arial", Font.BOLD, 12));
            graphics.drawString(
                debugRect.label, 
                debugRect.bounds.x + 5, 
                debugRect.bounds.y + 15
            );
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