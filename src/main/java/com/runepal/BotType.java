package com.runepal;

import lombok.Getter;

@Getter
public enum BotType {
    MINING_BOT("Mining"),
    COMBAT_BOT("Combat"),
    FISHING_BOT("Fishing"),
    WOODCUTTING_BOT("Woodcutting"),
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