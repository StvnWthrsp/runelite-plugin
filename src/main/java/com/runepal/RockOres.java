package com.runepal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum RockOres {
    COPPER(436, 10943, 11161),
    TIN(438, 11360, 11361),
    IRON(440, 11364, 11365, 11366), // Includes coal rocks that can yield iron
    COAL(453, 11366, 11367),
    MITHRIL(447, 11370, 11371),
    ADAMANTITE(449, 11372, 11373),
    RUNITE(451, 11374, 11375);

    private final int oreId;
    private final List<Integer> rockIds;

    RockOres(int oreId, Integer... rockIds) {
        this.oreId = oreId;
        this.rockIds = Arrays.asList(rockIds);
    }

    public int getOreId() {
        return oreId;
    }

    public List<Integer> getRockIds() {
        return Collections.unmodifiableList(rockIds);
    }

    /**
     * Finds the corresponding ore ID for a given rock ID.
     *
     * @param rockId The ID of the rock GameObject.
     * @return The item ID of the ore, or -1 if no mapping is found.
     */
    public static int getOreForRock(int rockId) {
        for (RockOres ore : values()) {
            if (ore.getRockIds().contains(rockId)) {
                return ore.getOreId();
            }
        }
        return -1; // No mapping found
    }
} 