package com.runepal;

import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

public class WoodcuttingTask extends AbstractGatheringTask {
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final GameService gameService;

    public WoodcuttingTask(RunepalPlugin plugin,
                           BotConfig config,
                           TaskManager taskManager,
                           PathfinderConfig pathfinderConfig,
                           ActionService actionService,
                           GameService gameService,
                           EventService eventService,
                           HumanizerService humanizerService) {
        super(plugin, config, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService);
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
    }

    @Override
    public String getTaskName() {
        return "Woodcutting";
    }

    @Override
    protected Skill getTrackedSkill() {
        return Skill.WOODCUTTING;
    }

    @Override
    protected boolean isGatheringAnimationActive() {
        return gameService.isCurrentlyWoodcutting();
    }

    @Override
    protected int[] getTargetObjectIds() {
        return plugin.getTreeIds();
    }

    @Override
    protected int[] getDropItemIds() {
        return plugin.getLogIds();
    }

    @Override
    protected String getInteractAction() {
        return "Chop down";
    }

    @Override
    protected String getTargetLabel() {
        return "tree";
    }

    @Override
    protected String getTargetLabelPlural() {
        return "trees";
    }

    @Override
    protected String getDropItemLabel() {
        return "log";
    }

    @Override
    protected void setTargetOverlay(GameObject gameObject) {
        plugin.setTargetTree(gameObject);
    }

    @Override
    protected boolean shouldBankWhenInventoryFull() {
        return config.woodcuttingMode() == WoodcuttingMode.BANK;
    }

    @Override
    protected WorldPoint getBankCoordinatesForTask() {
        return plugin.getBankCoordinates(config.woodcuttingBank());
    }
}
