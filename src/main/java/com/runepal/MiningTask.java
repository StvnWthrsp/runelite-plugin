package com.runepal;

import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

public class MiningTask extends AbstractGatheringTask {
    private static final WorldPoint VARROCK_EAST_MINE = new WorldPoint(3285, 3365, 0);

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final GameService gameService;

    public MiningTask(RunepalPlugin plugin,
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
        return "Mining";
    }

    @Override
    protected Skill getTrackedSkill() {
        return Skill.MINING;
    }

    @Override
    protected boolean isGatheringAnimationActive() {
        return gameService.isCurrentlyMining();
    }

    @Override
    protected int[] getTargetObjectIds() {
        return plugin.getRockIds();
    }

    @Override
    protected int[] getDropItemIds() {
        return plugin.getOreIds();
    }

    @Override
    protected String getInteractAction() {
        return "Mine";
    }

    @Override
    protected String getTargetLabel() {
        return "rock";
    }

    @Override
    protected String getTargetLabelPlural() {
        return "rocks";
    }

    @Override
    protected String getDropItemLabel() {
        return "ore";
    }

    @Override
    protected void setTargetOverlay(GameObject gameObject) {
        plugin.setTargetRock(gameObject);
    }

    @Override
    protected boolean shouldBankWhenInventoryFull() {
        return config.miningMode() == MiningMode.BANK;
    }

    @Override
    protected WorldPoint getBankCoordinatesForTask() {
        return plugin.getBankCoordinates(config.miningBank());
    }

    @Override
    protected WorldPoint getInitialDestination() {
        return gameService.getPlayerLocation().distanceTo(VARROCK_EAST_MINE) > 10 ? VARROCK_EAST_MINE : null;
    }
}
