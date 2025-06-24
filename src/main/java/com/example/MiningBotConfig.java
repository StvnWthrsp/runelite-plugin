package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("miningbot")
public interface MiningBotConfig extends Config
{
	@ConfigItem(
			keyName = "startBot",
			name = "Start Bot",
			description = "Toggles the bot on and off",
			position = 1
	)
	default boolean startBot()
	{
		return false;
	}

	@ConfigItem(
			keyName = "miningMode",
			name = "Mining Mode",
			description = "Sets the mining mode (power mine or bank)",
			position = 2
	)
	default MiningMode miningMode()
	{
		return MiningMode.POWER_MINE_DROP;
	}

	@ConfigItem(
			keyName = "rockIds",
			name = "Rock IDs",
			description = "Comma-separated list of rock IDs to mine",
			position = 3
	)
	default String rockIds()
	{
		return "";
	}

	@ConfigItem(
			keyName = "oreIds",
			name = "Ore IDs",
			description = "Comma-separated list of ORE item IDs to drop",
			position = 4
	)
	default String oreIds()
	{
		return "";
	}
} 