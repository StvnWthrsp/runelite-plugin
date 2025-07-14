package com.runepal;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MouseIndicatorOverlay extends Overlay {
    private final Client client;

    @Inject
    private MouseIndicatorOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(0.75f);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Point mousePos = client.getMouseCanvasPosition();
        if (mousePos != null && mousePos.getX() != -1 && mousePos.getY() != -1) {
            graphics.setColor(Color.RED);
            graphics.fillOval(mousePos.getX() - 3, mousePos.getY() - 3, 6, 6);
        }
        return null;
    }
} 