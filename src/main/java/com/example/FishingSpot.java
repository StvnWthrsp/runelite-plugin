package com.example;

import lombok.Getter;

@Getter
public enum FishingSpot {
    NET("Net"),
    LURE("Lure");

    private final String displayName;

    FishingSpot(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}