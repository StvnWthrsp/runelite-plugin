package com.example;

import net.runelite.api.coords.WorldPoint;

public enum Banks {
    VARROCK_EAST("Varrock East", new WorldPoint(3253, 3420, 0)),
    INTERIOR_TEST("Interior Test", new WorldPoint(3249, 3405, 0));

    private final String name;
    private final WorldPoint worldPoint;

    Banks(String name,WorldPoint worldPoint) {
        this.name = name;
        this.worldPoint = worldPoint;
    }

    public WorldPoint getBankCoordinates() {
        return worldPoint;
    }

    public String getBankName() {
        return name;
    }

    @Override 
    public String toString() {
        return name;
    }
} 