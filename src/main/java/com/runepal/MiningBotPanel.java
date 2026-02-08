package com.runepal;

import net.runelite.client.config.ConfigManager;

public class MiningBotPanel extends AbstractGatheringBotPanel<MiningMode> {
    public MiningBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super(plugin, config, configManager, MiningMode.values());
    }

    @Override
    protected String getConfigurationTitle() {
        return "Mining Configuration";
    }

    @Override
    protected String getModeLabel() {
        return "Mining Mode:";
    }

    @Override
    protected String getResourceTypesLabel() {
        return "Rock Types:";
    }

    @Override
    protected MiningMode getConfiguredMode() {
        return config.miningMode();
    }

    @Override
    protected String getConfiguredResourceTypes() {
        return config.rockTypes();
    }

    @Override
    protected String getConfiguredBank() {
        return config.miningBank();
    }

    @Override
    protected void saveMode(MiningMode mode) {
        configManager.setConfiguration("runepal", "miningMode", mode.name());
    }

    @Override
    protected void saveResourceTypes(String value) {
        configManager.setConfiguration("runepal", "rockTypes", value);
    }

    @Override
    protected void saveBank(Banks bank) {
        configManager.setConfiguration("runepal", "miningBank", bank.name());
    }
}
