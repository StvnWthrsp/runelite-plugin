package com.runepal;

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

public class MiningBotStatusOverlay extends OverlayPanel
{
    private final Client client;
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final GameService gameService;

    @Inject
    public MiningBotStatusOverlay(Client client, RunepalPlugin plugin, BotConfig config, GameService gameService)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.gameService = gameService;
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
        String currentState = plugin.getCurrentState();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(currentState)
                .rightColor(Color.WHITE)
                .build());

        // Mining statistics
//        long sessionXpGained = gameService.getSessionXpGained();
//
//        panelComponent.getChildren().add(LineComponent.builder()
//                .left("Session XP:")
//                .right(String.valueOf(sessionXpGained))
//                .rightColor(Color.GREEN)
//                .build());
//
//        panelComponent.getChildren().add(LineComponent.builder()
//                .left("XP/Hour:")
//                .right(gameService.getXpPerHour())
//                .rightColor(Color.YELLOW)
//                .build());

        // Current mining level
        int miningLevel = client.getBoostedSkillLevel(Skill.MINING);
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mining Level:")
                .right(String.valueOf(miningLevel))
                .rightColor(Color.ORANGE)
                .build());

        // Session runtime
//        Duration runtime = gameService.getSessionRuntime();
//        if (runtime != null)
//        {
//            String runtimeStr = formatDuration(runtime);
//            panelComponent.getChildren().add(LineComponent.builder()
//                    .left("Runtime:")
//                    .right(runtimeStr)
//                    .rightColor(Color.WHITE)
//                    .build());
//        }

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
        boolean inventoryFull = gameService.isInventoryFull();
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Inventory:")
                .right(inventoryFull ? "FULL" : "Space Available")
                .rightColor(inventoryFull ? Color.RED : Color.GREEN)
                .build());

        return super.render(graphics);
    }
} 