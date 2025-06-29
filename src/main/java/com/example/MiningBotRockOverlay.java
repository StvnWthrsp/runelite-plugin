package com.example;

import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MiningBotRockOverlay extends Overlay
{
    @SuppressWarnings("unused")
    private final Client client;
    @SuppressWarnings("unused")
    private final AndromedaPlugin plugin;
    private final BotConfig config;
    @Setter
    private GameObject target;

    @Inject
    public MiningBotRockOverlay(Client client, AndromedaPlugin plugin, BotConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightTargetRock() || target == null)
        {
            return null;
        }

        renderRockHighlight(graphics, target, Color.GREEN);

        return null;
    }

    private void renderRockHighlight(Graphics2D graphics, GameObject gameObject, Color color)
    {
        Shape objectClickbox = gameObject.getClickbox();
        if (objectClickbox != null)
        {
            // Draw colored outline
            Stroke originalStroke = graphics.getStroke();
            graphics.setStroke(new BasicStroke(2.0f));
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
    }
} 