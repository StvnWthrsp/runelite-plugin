package com.example;

import lombok.Getter;

@Getter
public enum FishingMode {
    POWER_DROP("Power Drop"),
    BANK("Bank");

    private final String displayName;

    FishingMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}