package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("runepal")
public interface BotConfig extends Config
{
	// Bot type selection for internal use (not shown in config panel)
	@ConfigItem(
			keyName = "botType",
			name = "Bot Type",
			description = "Internal bot type selection",
			position = 0,
			hidden = true
	)
	default BotType botType()
	{
		return BotType.MINING_BOT;
	}

	// Start bot toggle for internal use (not shown in config panel)
	@ConfigItem(
			keyName = "startBot",
			name = "Start Bot",
			description = "Internal bot start/stop toggle",
			position = 1,
			hidden = true
	)
	default boolean startBot()
	{
		return false;
	}

	// Mining Bot specific settings (now hidden since they're in the main panel)
	@ConfigItem(
			keyName = "miningMode",
			name = "Mining Mode",
			description = "Choose between power mining and banking",
			position = 10,
			hidden = true
	)
	default MiningMode miningMode()
	{
		return MiningMode.POWER_MINE;
	}

	@ConfigItem(
			keyName = "rockTypes",
			name = "Rock Types",
			description = "Comma-separated list of rock types to mine (e.g., Copper)",
			position = 11,
			hidden = true
	)
	default String rockTypes()
	{
		return "Copper"; // Default copper rock
	}

	@ConfigItem(
			keyName = "miningBank",
			name = "Bank Name",
			description = "The name of the bank to use in banking mode for the mining bot",
			position = 12,
			hidden = true
	)
	default String miningBank()
	{
		return "VARROCK_EAST"; // Default bank
	}

	// Fishing Bot specific settings
	@ConfigItem(
			keyName = "fishingSpot",
			name = "Fishing Spot",
			description = "Choose fishing method - Net (shrimp/anchovies) or Lure (trout/salmon)",
			position = 15,
			hidden = true
	)
	default FishingSpot fishingSpot()
	{
		return FishingSpot.NET;
	}

	@ConfigItem(
			keyName = "fishingArea",
			name = "Fishing Area",
			description = "Choose the fishing area to use",
			position = 16,
			hidden = true
	)
	default FishingArea fishingArea()
	{
		return FishingArea.LUMBRIDGE_SWAMP;
	}

	@ConfigItem(
			keyName = "cookFish",
			name = "Cook Fish",
			description = "Cook caught fish before banking/dropping",
			position = 17,
			hidden = true
	)
	default boolean cookFish()
	{
		return false;
	}

	@ConfigItem(
			keyName = "fishingMode",
			name = "Fishing Mode",
			description = "Choose between power dropping and banking",
			position = 18,
			hidden = true
	)
	default FishingMode fishingMode()
	{
		return FishingMode.POWER_DROP;
	}

	// Combat Bot specific settings
	@ConfigItem(
			keyName = "combatNpcNames",
			name = "Combat NPC Names",
			description = "Comma-separated list of NPC names to attack (e.g., Goblin,Cow)",
			position = 20,
			hidden = true
	)
	default String combatNpcNames()
	{
		return "Goblin"; // Default target
	}

	@ConfigItem(
			keyName = "combatEatAtHealthPercent",
			name = "Eat at Health Percent",
			description = "Health percentage threshold at which to eat food (1-99)",
			position = 21,
			hidden = true
	)
	default int combatEatAtHealthPercent()
	{
		return 50; // Default to 50% health
	}

	// Combat Bot debugging section
	@ConfigSection(
			name = "Combat Bot - Debugging",
			description = "Visual debugging options for combat bot",
			position = 30
	)
	String combatDebugSection = "combatDebugging";

	@ConfigItem(
			keyName = "highlightTargetNpc",
			name = "Highlight Target NPC",
			description = "Visually highlight the targeted NPC (Green: targeting, Red: attacking, Yellow: other)",
			position = 0,
			section = combatDebugSection
	)
	default boolean highlightTargetNpc()
	{
		return false;
	}

	// Mining Bot debugging section
	@ConfigSection(
			name = "Mining Bot - Debugging",
			description = "Visual debugging options for mining bot",
			position = 20
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

	@ConfigItem(
			keyName = "showMenuDebugOverlay",
			name = "Show Menu Debug Overlay",
			description = "Display debug rectangles around menu entries when right-clicking objects",
			position = 3,
			section = debugSection
	)
	default boolean showMenuDebugOverlay()
	{
		return false;
	}

	// Windmouse section
	@ConfigSection(
			name = "Windmouse - Mouse Movement",
			description = "Configuration for human-like mouse movement algorithm",
			position = 40
	)
	String windmouseSection = "windmouse";

	@ConfigItem(
			keyName = "windmouseGravity",
			name = "Gravity Force",
			description = "Gravitational force magnitude (higher = more direct movement)",
			position = 0,
			section = windmouseSection
	)
	default double windmouseGravity()
	{
		return 9.0;
	}

	@ConfigItem(
			keyName = "windmouseWind",
			name = "Wind Force",
			description = "Wind force fluctuation magnitude (higher = more randomness)",
			position = 1,
			section = windmouseSection
	)
	default double windmouseWind()
	{
		return 3.0;
	}

	@ConfigItem(
			keyName = "windmouseMaxVel",
			name = "Max Velocity",
			description = "Maximum step size/velocity threshold",
			position = 2,
			section = windmouseSection
	)
	default double windmouseMaxVel()
	{
		return 15.0;
	}

	@ConfigItem(
			keyName = "windmouseTargetArea",
			name = "Target Area",
			description = "Distance threshold where wind behavior changes",
			position = 3,
			section = windmouseSection
	)
	default double windmouseTargetArea()
	{
		return 12.0;
	}

	@ConfigItem(
			keyName = "windmouseMinStepDelay",
			name = "Min Step Delay (ms)",
			description = "Minimum milliseconds between movement steps",
			position = 4,
			section = windmouseSection
	)
	default int windmouseMinStepDelay()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "windmouseMaxStepDelay",
			name = "Max Step Delay (ms)",
			description = "Maximum milliseconds between movement steps",
			position = 5,
			section = windmouseSection
	)
	default int windmouseMaxStepDelay()
	{
		return 10;
	}

	@ConfigItem(
			keyName = "windmousePreClickDelay",
			name = "Min Pre-Click Delay (ms)",
			description = "Minimum milliseconds before clicking after movement",
			position = 6,
			section = windmouseSection
	)
	default int windmousePreClickDelay()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "windmouseMaxPreClickDelay",
			name = "Max Pre-Click Delay (ms)",
			description = "Maximum milliseconds before clicking after movement",
			position = 7,
			section = windmouseSection
	)
	default int windmouseMaxPreClickDelay()
	{
		return 5;
	}
} 