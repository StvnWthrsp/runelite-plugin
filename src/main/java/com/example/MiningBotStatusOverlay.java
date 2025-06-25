package com.example;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;

public class MiningBotStatusOverlay extends OverlayPanel
{
    private final Client client;
    private final MiningBotPlugin plugin;
    private final MiningBotConfig config;

    @Inject
    public MiningBotStatusOverlay(Client client, MiningBotPlugin plugin, MiningBotConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showBotStatus())
        {
            return null;
        }

        // Clear previous content
        panelComponent.getChildren().clear();

        // Add title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mining Bot Debug")
                .color(Color.CYAN)
                .build());

        // Current state
        BotState currentState = plugin.getCurrentState();
        Color stateColor = getStateColor(currentState);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(currentState.toString())
                .rightColor(stateColor)
                .build());

        // Mining statistics
        long totalXpGained = plugin.getTotalXpGained();
        long sessionXpGained = plugin.getSessionXpGained();
        
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Session XP:")
                .right(String.valueOf(sessionXpGained))
                .rightColor(Color.GREEN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total XP Gained:")
                .right(String.valueOf(totalXpGained))
                .rightColor(Color.LIGHT_GRAY)
                .build());

        // Current mining level
        int miningLevel = client.getBoostedSkillLevel(Skill.MINING);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mining Level:")
                .right(String.valueOf(miningLevel))
                .rightColor(Color.ORANGE)
                .build());

        // Session runtime
        Duration runtime = plugin.getSessionRuntime();
        if (runtime != null)
        {
            String runtimeStr = formatDuration(runtime);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Runtime:")
                    .right(runtimeStr)
                    .rightColor(Color.WHITE)
                    .build());

            // XP per hour calculation
            if (runtime.toMinutes() > 0)
            {
                long xpPerHour = (sessionXpGained * 60) / runtime.toMinutes();
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("XP/Hour:")
                        .right(String.valueOf(xpPerHour))
                        .rightColor(Color.YELLOW)
                        .build());
            }
        }

        // Target rock info
        if (plugin.getTargetRock() != null)
        {
            int rockId = plugin.getTargetRock().getId();
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Target Rock ID:")
                    .right(String.valueOf(rockId))
                    .rightColor(Color.MAGENTA)
                    .build());
        }

        // Inventory status
        boolean inventoryFull = plugin.isInventoryFull();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventory:")
                .right(inventoryFull ? "FULL" : "Space Available")
                .rightColor(inventoryFull ? Color.RED : Color.GREEN)
                .build());

        return super.render(graphics);
    }

    private Color getStateColor(BotState state)
    {
        switch (state)
        {
            case IDLE:
                return Color.GRAY;
            case FINDING_ROCK:
                return Color.ORANGE;
            case MINING:
                return Color.GREEN;
            case WAIT_MINING:
                return Color.YELLOW;
            case CHECK_INVENTORY:
                return Color.BLUE;
            case DROPPING:
                return Color.RED;
            case WALKING_TO_BANK:
            case BANKING:
                return Color.MAGENTA;
            default:
                return Color.WHITE;
        }
    }

    private String formatDuration(Duration duration)
    {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        else
        {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
} 