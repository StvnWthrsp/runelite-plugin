package com.runepal;

import net.runelite.api.*;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import java.awt.*;

public class CombatBotNpcOverlay extends Overlay {
    private final BotConfig config;
    private final RunepalPlugin plugin;
    private final ModelOutlineRenderer modelOutlineRenderer;

    @Inject
    private CombatBotNpcOverlay(BotConfig config, RunepalPlugin plugin, ModelOutlineRenderer modelOutlineRenderer) {
        this.config = config;
        this.plugin = plugin;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.highlightTargetNpc()) {
            return null;
        }

        NPC targetNpc = plugin.getTargetNpc();
        if (targetNpc == null) {
            return null;
        }

        // Get the current combat state to determine color
        String currentState = plugin.getCurrentState();
        Color highlightColor;
        
        if (currentState.contains("ATTACKING")) {
            highlightColor = Color.RED;  // Red when attacking
        } else if (currentState.contains("FINDING_NPC")) {
            highlightColor = Color.GREEN;  // Green when finding/targeting
        } else {
            highlightColor = Color.YELLOW;  // Yellow for other states
        }

        // Highlight the NPC model
        modelOutlineRenderer.drawOutline(targetNpc, 2, highlightColor, 4);

        return null;
    }
} 