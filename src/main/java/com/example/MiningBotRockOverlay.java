package com.example;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Stroke;

public class MiningBotRockOverlay extends Overlay
{
    private final Client client;
    private final MiningBotPlugin plugin;
    private final MiningBotConfig config;

    @Inject
    public MiningBotRockOverlay(Client client, MiningBotPlugin plugin, MiningBotConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightTargetRock())
        {
            return null;
        }

        GameObject targetRock = plugin.getTargetRock();
        if (targetRock == null)
        {
            return null;
        }

        BotState currentState = plugin.getCurrentState();
        Color highlightColor = getHighlightColor(currentState);
        
        if (highlightColor != null)
        {
            renderRockHighlight(graphics, targetRock, highlightColor);
        }

        return null;
    }

    private Color getHighlightColor(BotState state)
    {
        switch (state)
        {
            case FINDING_ROCK:
            case MINING:
                return Color.GREEN; // Rock detected/targeted
            case WAIT_MINING:
                return Color.YELLOW; // Currently mining
            default:
                return null; // No highlighting needed
        }
    }

    private void renderRockHighlight(Graphics2D graphics, GameObject gameObject, Color color)
    {
        Shape objectClickbox = gameObject.getClickbox();
        if (objectClickbox != null)
        {
            // Draw thick colored outline
            Stroke originalStroke = graphics.getStroke();
            graphics.setStroke(new BasicStroke(3.0f));
            graphics.setColor(color);
            graphics.draw(objectClickbox);
            graphics.setStroke(originalStroke);
        }
        else
        {
            // Fallback: draw outline around convex hull
            Shape convexHull = gameObject.getConvexHull();
            if (convexHull != null)
            {
                Stroke originalStroke = graphics.getStroke();
                graphics.setStroke(new BasicStroke(2.0f));
                graphics.setColor(color);
                graphics.draw(convexHull);
                graphics.setStroke(originalStroke);
            }
        }

        // Draw a small colored dot at the center for better visibility
        LocalPoint localPoint = gameObject.getLocalLocation();
        if (localPoint != null)
        {
            Polygon canvasTilePoly = Perspective.getCanvasTilePoly(client, localPoint);
            if (canvasTilePoly != null)
            {
                int centerX = (int) canvasTilePoly.getBounds().getCenterX();
                int centerY = (int) canvasTilePoly.getBounds().getCenterY();
                
                graphics.setColor(color);
                graphics.fillOval(centerX - 4, centerY - 4, 8, 8);
                graphics.setColor(Color.BLACK);
                graphics.drawOval(centerX - 4, centerY - 4, 8, 8);
            }
        }
    }
} 