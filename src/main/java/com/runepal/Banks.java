package com.runepal;

import net.runelite.api.coords.WorldPoint;

public enum Banks {
    VARROCK_EAST("Varrock East", new WorldPoint(3253, 3420, 0)),
    INTERIOR_TEST("Interior Test", new WorldPoint(3249, 3405, 0)),
    LUMBRIDGE("Lumbridge", new WorldPoint(3208, 3220, 2)),
    VARROCK_WEST("Varrock West", new WorldPoint(3183, 3436, 0)),
    HUNTER_GUILD("Hunter Guild", new WorldPoint(1542, 3040, 0));

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