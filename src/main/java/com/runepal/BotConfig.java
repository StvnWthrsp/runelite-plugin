package com.runepal;

import com.runepal.shortestpath.TeleportationItem;
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

	// Woodcutting Bot specific settings
	@ConfigItem(
			keyName = "woodcuttingMode",
			name = "Woodcutting Mode",
			description = "Choose between power chopping and banking",
			position = 19,
			hidden = true
	)
	default WoodcuttingMode woodcuttingMode()
	{
		return WoodcuttingMode.POWER_CHOP;
	}

	@ConfigItem(
			keyName = "treeTypes",
			name = "Tree Types",
			description = "Comma-separated list of tree types (e.g., Oak, Willow)",
			position = 20,
			hidden = true
	)
	default String treeTypes()
	{
		return "Oak";
	}

	@ConfigItem(
			keyName = "woodcuttingBank",
			name = "Bank Name",
			description = "Bank location for banking mode",
			position = 21,
			hidden = true
	)
	default String woodcuttingBank()
	{
		return "VARROCK_EAST";
	}

	// Combat Bot specific settings
	@ConfigItem(
			keyName = "combatNpcNames",
			name = "Combat NPC Names",
			description = "Comma-separated list of NPC names to attack (e.g., Goblin,Cow)",
			position = 22,
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
			position = 23,
			hidden = true
	)
	default int combatEatAtHealthPercent()
	{
		return 50; // Default to 50% health
	}

	// Combat Bot - Potion Settings
	@ConfigItem(
			keyName = "combatUsePrayerPotions",
			name = "Use Prayer Potions",
			description = "Enable automatic prayer potion consumption",
			position = 24,
			hidden = true
	)
	default boolean combatUsePrayerPotions()
	{
		return true;
	}

	@ConfigItem(
			keyName = "combatPrayerPotionThreshold",
			name = "Prayer Potion Threshold",
			description = "Prayer percentage threshold at which to drink prayer potion (1-99)",
			position = 25,
			hidden = true
	)
	default int combatPrayerPotionThreshold()
	{
		return 20; // Default to 20% prayer
	}

	@ConfigItem(
			keyName = "combatUseCombatPotions",
			name = "Use Combat Potions",
			description = "Enable automatic combat potion consumption",
			position = 24,
			hidden = true
	)
	default boolean combatUseCombatPotions()
	{
		return true;
	}

	@ConfigItem(
			keyName = "combatUseAntipoison",
			name = "Use Antipoison",
			description = "Enable automatic antipoison consumption when poisoned",
			position = 25,
			hidden = true
	)
	default boolean combatUseAntipoison()
	{
		return false;
	}

	// Combat Bot - Prayer Settings
	@ConfigItem(
			keyName = "combatUsePrayers",
			name = "Use Prayers",
			description = "Enable automatic prayer activation during combat",
			position = 26,
			hidden = true
	)
	default boolean combatUsePrayers()
	{
		return false;
	}

	@ConfigItem(
			keyName = "combatOffensivePrayer",
			name = "Offensive Prayer",
			description = "Preferred offensive prayer for combat (Piety, Chivalry, Ultimate Strength, etc.)",
			position = 27,
			hidden = true
	)
	default String combatOffensivePrayer()
	{
		return "Ultimate Strength";
	}

	@ConfigItem(
			keyName = "combatDefensivePrayer",
			name = "Defensive Prayer",
			description = "Preferred defensive prayer for combat (Protect from Melee, Missiles, Magic)",
			position = 28,
			hidden = true
	)
	default String combatDefensivePrayer()
	{
		return "None";
	}

	@ConfigItem(
			keyName = "combatPrayerPointThreshold",
			name = "Prayer Point Threshold",
			description = "Prayer point percentage below which to deactivate prayers (1-99)",
			position = 29,
			hidden = true
	)
	default int combatPrayerPointThreshold()
	{
		return 10; // Default to 10% prayer points
	}

	// Combat Bot - Banking Settings
	@ConfigItem(
			keyName = "combatMinFoodCount",
			name = "Min Food Count",
			description = "Minimum food count before banking",
			position = 30,
			hidden = true
	)
	default int combatMinFoodCount()
	{
		return 5;
	}

	@ConfigItem(
			keyName = "combatMinPrayerPotions",
			name = "Min Prayer Potions",
			description = "Minimum prayer potion count before banking",
			position = 31,
			hidden = true
	)
	default int combatMinPrayerPotions()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "combatMinCombatPotions",
			name = "Min Combat Potions",
			description = "Minimum combat potion count before banking",
			position = 32,
			hidden = true
	)
	default int combatMinCombatPotions()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "combatBankLocation",
			name = "Bank Location",
			description = "Preferred bank location for the combat bot",
			position = 33,
			hidden = true
	)
	default String combatBankLocation()
	{
		return "VARROCK_EAST";
	}

	// Combat Bot - Loot Settings
	@ConfigItem(
			keyName = "combatAutoLoot",
			name = "Auto Loot",
			description = "Enable automatic loot collection",
			position = 34,
			hidden = true
	)
	default boolean combatAutoLoot()
	{
		return false;
	}

	@ConfigItem(
			keyName = "combatLootValueThreshold",
			name = "Loot Value Threshold",
			description = "Minimum GP value of items to loot",
			position = 35,
			hidden = true
	)
	default int combatLootValueThreshold()
	{
		return 100;
	}

	@ConfigItem(
			keyName = "combatLootWhitelist",
			name = "Loot Whitelist",
			description = "Comma-separated list of item names to always loot",
			position = 36,
			hidden = true
	)
	default String combatLootWhitelist()
	{
		return "";
	}

	// Sand Crab Bot specific settings
	@ConfigItem(
			keyName = "sandCrabFood",
			name = "Sand Crab Food",
			description = "Type of food to use for sand crab training",
			position = 37,
			hidden = true
	)
	default String sandCrabFood()
	{
		return "COOKED_KARAMBWAN";
	}

	@ConfigItem(
			keyName = "sandCrabFoodQuantity",
			name = "Sand Crab Food Quantity",
			description = "Number of food items to withdraw from bank (0-28)",
			position = 38,
			hidden = true
	)
	default int sandCrabFoodQuantity()
	{
		return 20;
	}

	@ConfigItem(
			keyName = "sandCrabPotion",
			name = "Sand Crab Potion",
			description = "Type of potion to use for sand crab training",
			position = 39,
			hidden = true
	)
	default String sandCrabPotion()
	{
		return "SUPER_COMBAT";
	}

	@ConfigItem(
			keyName = "sandCrabPotionQuantity",
			name = "Sand Crab Potion Quantity",
			description = "Number of potion items to withdraw from bank (0-28)",
			position = 40,
			hidden = true
	)
	default int sandCrabPotionQuantity()
	{
		return 1;
	}

	@ConfigItem(
			keyName = "sandCrabCount",
			name = "Sand Crab Count",
			description = "Number of sand crabs to engage simultaneously (1-4)",
			position = 41,
			hidden = true
	)
	default int sandCrabCount()
	{
		return 3;
	}

	@ConfigItem(
			keyName = "sandCrabEatAtHp",
			name = "Sand Crab Eat At HP",
			description = "HP threshold for eating food (1-99)",
			position = 42,
			hidden = true
	)
	default int sandCrabEatAtHp()
	{
		return 50;
	}

	@ConfigItem(
			keyName = "sandCrabInventoryAction",
			name = "Sand Crab Inventory Action",
			description = "Action to take when consumables are depleted",
			position = 43,
			hidden = true
	)
	default String sandCrabInventoryAction()
	{
		return "BANK";
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

	// Woodcutting Bot debugging section
	@ConfigSection(
			name = "Woodcutting Bot - Debugging",
			description = "Visual debugging options for woodcutting bot",
			position = 25
	)
	String woodcuttingDebugSection = "woodcuttingDebugging";

	@ConfigItem(
			keyName = "highlightTargetTree",
			name = "Highlight Target Tree",
			description = "Visually highlight the targeted tree (Green: detected, Yellow: cutting)",
			position = 0,
			section = woodcuttingDebugSection
	)
	default boolean highlightTargetTree()
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

	// Shortest Path section
	@ConfigSection(
			name = "Shortest Path - Navigation",
			description = "Configuration for pathfinding and navigation system",
			position = 50
	)
	String shortestPathSection = "shortestPath";

	@ConfigItem(
			keyName = "spAvoidWilderness",
			name = "Avoid wilderness",
			description = "Whether the wilderness should be avoided if possible (otherwise, will e.g. use wilderness lever from Edgeville to Ardougne)",
			position = 1,
			section = shortestPathSection
	)
	default boolean spAvoidWilderness() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseAgilityShortcuts",
			name = "Use agility shortcuts",
			description = "Whether to include agility shortcuts in the path. You must also have the required agility level",
			position = 2,
			section = shortestPathSection
	)
	default boolean spUseAgilityShortcuts() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseGrappleShortcuts",
			name = "Use grapple shortcuts",
			description = "Whether to include crossbow grapple agility shortcuts in the path. You must also have the required agility, ranged and strength levels",
			position = 3,
			section = shortestPathSection
	)
	default boolean spUseGrappleShortcuts() {
		return false;
	}

	@ConfigItem(
			keyName = "spUseBoats",
			name = "Use boats",
			description = "Whether to include small boats in the path (e.g. the boat to Fishing Platform)",
			position = 4,
			section = shortestPathSection
	)
	default boolean spUseBoats() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseCanoes",
			name = "Use canoes",
			description = "Whether to include canoes in the path",
			position = 5,
			section = shortestPathSection
	)
	default boolean spUseCanoes() {
		return false;
	}

	@ConfigItem(
			keyName = "spUseCharterShips",
			name = "Use charter ships",
			description = "Whether to include charter ships in the path",
			position = 6,
			section = shortestPathSection
	)
	default boolean spUseCharterShips() {
		return false;
	}

	@ConfigItem(
			keyName = "spUseShips",
			name = "Use ships",
			description = "Whether to include passenger ships in the path (e.g. the customs ships to Karamja)",
			position = 7,
			section = shortestPathSection
	)
	default boolean spUseShips() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseFairyRings",
			name = "Use fairy rings",
			description = "Whether to include fairy rings in the path. You must also have completed the required quests or miniquests",
			position = 8,
			section = shortestPathSection
	)
	default boolean spUseFairyRings() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseGnomeGliders",
			name = "Use gnome gliders",
			description = "Whether to include gnome gliders in the path",
			position = 9,
			section = shortestPathSection
	)
	default boolean spUseGnomeGliders() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseHotAirBalloons",
			name = "Use hot air balloons",
			description = "Whether to include hot air balloons in the path",
			position = 10,
			section = shortestPathSection
	)
	default boolean spUseHotAirBalloons() {
		return false;
	}

	@ConfigItem(
			keyName = "spUseMinecarts",
			name = "Use minecarts",
			description = "Whether to include minecarts in the path (e.g. the Keldagrim and Lovakengj minecart networks)",
			position = 11,
			section = shortestPathSection
	)
	default boolean spUseMinecarts() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseQuetzals",
			name = "Use quetzals",
			description = "Whether to include quetzals in the path",
			position = 12,
			section = shortestPathSection
	)
	default boolean spUseQuetzals() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseSpiritTrees",
			name = "Use spirit trees",
			description = "Whether to include spirit trees in the path",
			position = 13,
			section = shortestPathSection
	)
	default boolean spUseSpiritTrees() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseTeleportationItems",
			name = "Use teleportation items",
			description = "Whether to include teleportation items from the player's inventory and equipment. Options labelled (perm) only use permanent non-charge items.",
			position = 14,
			section = shortestPathSection
	)
	default TeleportationItem spUseTeleportationItems() {
		return TeleportationItem.INVENTORY_NON_CONSUMABLE;
	}

	@ConfigItem(
			keyName = "spUseTeleportationLevers",
			name = "Use teleportation levers",
			description = "Whether to include teleportation levers in the path (e.g. the lever from Edgeville to Wilderness)",
			position = 15,
			section = shortestPathSection
	)
	default boolean spUseTeleportationLevers() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseTeleportationPortals",
			name = "Use teleportation portals",
			description = "Whether to include teleportation portals in the path (e.g. the portal from Ferox Enclave to Castle Wars)",
			position = 16,
			section = shortestPathSection
	)
	default boolean spUseTeleportationPortals() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseTeleportationSpells",
			name = "Use teleportation spells",
			description = "Whether to include teleportation spells in the path",
			position = 17,
			section = shortestPathSection
	)
	default boolean spUseTeleportationSpells() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseTeleportationMinigames",
			name = "Use teleportation to minigames",
			description = "Whether to include teleportation to minigames/activities/grouping in the path (e.g. the Nightmare Zone minigame teleport). These teleports share a 20 minute cooldown.",
			position = 18,
			section = shortestPathSection
	)
	default boolean spUseTeleportationMinigames() {
		return true;
	}

	@ConfigItem(
			keyName = "spUseWildernessObelisks",
			name = "Use wilderness obelisks",
			description = "Whether to include wilderness obelisks in the path",
			position = 19,
			section = shortestPathSection
	)
	default boolean spUseWildernessObelisks() {
		return true;
	}

	@ConfigItem(
			keyName = "spCurrencyThreshold",
			name = "Currency threshold",
			description = "The maximum amount of currency to use on a single transportation method. The currencies affected by the threshold are coins, trading sticks, ecto-tokens and warrior guild tokens.",
			position = 20,
			section = shortestPathSection
	)
	default int spCurrencyThreshold() {
		return 100000;
	}

	@ConfigItem(
			keyName = "spRecalculateDistance",
			name = "Recalculate distance",
			description = "Distance from the path the player should be for it to be recalculated (-1 for never)",
			position = 22,
			section = shortestPathSection
	)
	default int spRecalculateDistance() {
		return 10;
	}

	@ConfigItem(
			keyName = "spReachedDistance",
			name = "Finish distance",
			description = "Distance from the target tile at which the path should be ended (-1 for never)",
			position = 23,
			section = shortestPathSection
	)
	default int spReachedDistance() {
		return 5;
	}

	@ConfigItem(
			keyName = "spCalculationCutoff",
			name = "Calculation cutoff",
			description = "The cutoff threshold in number of ticks (0.6 seconds) of no progress being made towards the path target before the calculation will be stopped",
			position = 26,
			section = shortestPathSection
	)
	default int spCalculationCutoff() {
		return 5;
	}
} 