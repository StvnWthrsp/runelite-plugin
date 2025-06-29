package com.example;

import lombok.Getter;

@Getter
public enum BotType {
    MINING_BOT("Mining"),
    COMBAT_BOT("Combat"),
    // Future bot types can be added here
    // WOODCUTTING_BOT("Woodcutting Bot"),
    // FISHING_BOT("Fishing Bot"),
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