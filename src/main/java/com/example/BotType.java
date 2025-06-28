package com.example;

public enum BotType {
    MINING_BOT("Mining Bot"),
    COMBAT_BOT("Combat"),
    // Future bot types can be added here
    // WOODCUTTING_BOT("Woodcutting Bot"),
    // FISHING_BOT("Fishing Bot"),
    ;

    private final String displayName;

    BotType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
} 