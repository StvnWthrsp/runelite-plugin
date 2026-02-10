package com.runepal;

import net.runelite.client.config.ConfigManager;

public class WoodcuttingBotPanel extends AbstractGatheringBotPanel<WoodcuttingMode> {
    public WoodcuttingBotPanel(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        super(plugin, config, configManager, WoodcuttingMode.values());
    }

    @Override
    protected String getConfigurationTitle() {
        return "Woodcutting Configuration";
    }

    @Override
    protected String getModeLabel() {
        return "Woodcutting Mode:";
    }

    @Override
    protected String getResourceTypesLabel() {
        return "Tree Types:";
    }

    @Override
    protected WoodcuttingMode getConfiguredMode() {
        return config.woodcuttingMode();
    }

    @Override
    protected String getConfiguredResourceTypes() {
        return config.treeTypes();
    }

    @Override
    protected String getConfiguredBank() {
        return config.woodcuttingBank();
    }

    @Override
    protected void saveMode(WoodcuttingMode mode) {
        configManager.setConfiguration("runepal", "woodcuttingMode", mode.name());
    }

    @Override
    protected void saveResourceTypes(String value) {
        configManager.setConfiguration("runepal", "treeTypes", value);
    }

    @Override
    protected void saveBank(Banks bank) {
        configManager.setConfiguration("runepal", "woodcuttingBank", bank.name());
    }
}
