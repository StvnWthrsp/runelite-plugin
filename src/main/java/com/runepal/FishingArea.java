package com.runepal;

import lombok.Getter;

@Getter
public enum FishingArea {
    LUMBRIDGE_SWAMP("Lumbridge Swamp"),
    BARBARIAN_VILLAGE("Barbarian Village");

    private final String displayName;

    FishingArea(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}