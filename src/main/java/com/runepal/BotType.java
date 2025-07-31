package com.runepal;

import lombok.Getter;

@Getter
public enum BotType {
    MINING_BOT("Mining"),
    COMBAT_BOT("Combat"),
    FISHING_BOT("Fishing"),
    WOODCUTTING_BOT("Woodcutting"),
    SAND_CRAB_BOT("Sand Crab"),
    GEMSTONE_CRAB_BOT("Gemstone Crab"),
    HIGH_ALCH_BOT("High Alch"),
    GRINDING_BOT("Item Grinder")
    ;

    private final String displayName;

    BotType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
} 