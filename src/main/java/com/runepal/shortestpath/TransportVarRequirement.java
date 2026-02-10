package com.runepal.shortestpath;

import lombok.Getter;

import java.util.Map;

public abstract class TransportVarRequirement {
    @Getter
    private final int id;
    private final int value;
    private final TransportVarCheck check;

    protected TransportVarRequirement(int id, int value, TransportVarCheck check) {
        this.id = id;
        this.value = value;
        this.check = check;
    }

    public boolean check(Map<Integer, Integer> values) {
        Integer currentValue = values.get(id);
        if (currentValue == null) {
            return false;
        }

        switch (check) {
            case EQUAL:
                return currentValue == value;
            case GREATER:
                return currentValue > value;
            case SMALLER:
                return currentValue < value;
            case BIT_SET:
                return (currentValue & value) > 0;
            case COOLDOWN_MINUTES:
                return ((System.currentTimeMillis() / 60000) - currentValue) > value;
            default:
                return false;
        }
    }
}
