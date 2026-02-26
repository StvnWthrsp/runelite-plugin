package com.runepal.agent;

import com.runepal.BotType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AgentSkillTemplate {
    MINE_POWER("MINE_POWER", "Power-mine selected rock types", BotType.MINING_BOT),
    MINE_BANK("MINE_BANK", "Mine selected rocks and bank ores", BotType.MINING_BOT),
    WOODCUT_POWER("WOODCUT_POWER", "Power-chop selected tree types", BotType.WOODCUTTING_BOT),
    WOODCUT_BANK("WOODCUT_BANK", "Chop selected trees and bank logs", BotType.WOODCUTTING_BOT),
    FISH_POWER("FISH_POWER", "Power-drop fish at a selected spot and area", BotType.FISHING_BOT),
    FISH_BANK("FISH_BANK", "Fish at a selected spot and bank catches", BotType.FISHING_BOT),
    COMBAT_BASIC("COMBAT_BASIC", "Attack configured NPC names with existing combat logic", BotType.COMBAT_BOT),
    STOP_ALL("STOP_ALL", "Stop the currently running template skill", null);

    private final String wireName;
    private final String description;
    private final BotType botType;

    AgentSkillTemplate(String wireName, String description, BotType botType) {
        this.wireName = wireName;
        this.description = description;
        this.botType = botType;
    }

    public String getWireName() {
        return wireName;
    }

    public String getDescription() {
        return description;
    }

    public BotType getBotType() {
        return botType;
    }

    public boolean startsBot() {
        return botType != null;
    }

    public static Optional<AgentSkillTemplate> fromWireName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        final String normalizedInput = normalize(value);
        return Arrays.stream(values())
                .filter(template -> normalize(template.wireName).equals(normalizedInput)
                        || normalize(template.name()).equals(normalizedInput))
                .findFirst();
    }

    private static String normalize(String value) {
        return value.trim()
                .toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
