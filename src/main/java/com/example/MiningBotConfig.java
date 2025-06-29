package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("miningbot")
public interface MiningBotConfig extends Config
{
	@ConfigSection(
			name = "General",
			description = "General settings",
			position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigItem(
			keyName = "startBot",
			name = "Start Bot",
			description = "Toggles the bot on and off",
			position = 1,
			section = generalSettings
	)
	default boolean startBot()
	{
		return false;
	}

	@ConfigItem(
			keyName = "miningMode",
			name = "Mining Mode",
			description = "Choose between power mining and banking",
			position = 2,
			section = generalSettings
	)
	default MiningMode miningMode()
	{
		return MiningMode.POWER_MINE;
	}

	@ConfigItem(
			keyName = "rockTypes",
			name = "Rock Types",
			description = "Comma-separated list of rock types to mine (e.g., Copper)",
			position = 3
	)
	default String rockTypes()
	{
		return "Copper"; // Default copper rock
	}

	// @ConfigItem(
	// 		keyName = "oreIds",
	// 		name = "Ore IDs",
	// 		description = "Comma-separated list of ore item IDs to drop (e.g., 436 for copper ore)",
	// 		position = 4
	// )
	// default String oreIds()
	// {
	// 	return "436"; // Default copper ore ID
	// }

	@ConfigSection(
			name = "Debugging",
			description = "Visual debugging options",
			position = 5
	)
	String debugSection = "debugging";

	@ConfigItem(
			keyName = "highlightTargetRock",
			name = "Highlight Target Rock",
			description = "Visually highlight the targeted rock (Green: detected, Yellow: mining)",
			position = 0,
			section = debugSection
	)
	default boolean highlightTargetRock()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showBotStatus",
			name = "Show Bot Status Overlay",
			description = "Display current bot state and mining statistics on screen",
			position = 1,
			section = debugSection
	)
	default boolean showBotStatus()
	{
		return false;
	}

	@ConfigItem(
			keyName = "highlightInventoryItems",
			name = "Highlight Inventory Items",
			description = "Highlight ore items in inventory with colored borders",
			position = 2,
			section = debugSection
	)
	default boolean highlightInventoryItems()
	{
		return false;
	}
} 