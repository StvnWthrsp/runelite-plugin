package com.runepal;

import lombok.Getter;

@Getter
public enum BotType {
    MINING_BOT("Mining"),
    COMBAT_BOT("Combat"),
    FISHING_BOT("Fishing"),
    // Future bot types can be added here
    // WOODCUTTING_BOT("Woodcutting Bot"),
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