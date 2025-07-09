package com.example.core;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import com.example.BotConfig;
import com.example.Banks;
import com.example.RockOres;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Helper class for bot configuration management and data conversion.
 */
@Singleton
@Slf4j
public class ConfigurationHelper {
    private final BotConfig config;

    public ConfigurationHelper(BotConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Get rock IDs based on configured rock types.
     * 
     * @return array of rock IDs
     */
    public int[] getRockIds() {
        String[] rockTypes = config.rockTypes().split(",");
        List<Integer> idList = new ArrayList<>();
        
        for (String rockType : rockTypes) {
            switch (rockType.trim()) {
                case "Copper":
                    idList.addAll(RockOres.COPPER.getRockIds());
                    break;
                case "Tin":
                    idList.addAll(RockOres.TIN.getRockIds());
                    break;
                case "Iron":
                    idList.addAll(RockOres.IRON.getRockIds());
                    break;
                case "Coal":
                    idList.addAll(RockOres.COAL.getRockIds());
                    break;
                case "Mithril":
                    idList.addAll(RockOres.MITHRIL.getRockIds());
                    break;
                case "Adamantite":
                    idList.addAll(RockOres.ADAMANTITE.getRockIds());
                    break;
                case "Runite":
                    idList.addAll(RockOres.RUNITE.getRockIds());
                    break;
                default:
                    log.warn("Unknown rock type: {}", rockType);
                    break;
            }
        }
        
        return idList.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Get ore IDs corresponding to the configured rock types.
     * 
     * @return array of ore IDs
     */
    public int[] getOreIds() {
        return Arrays.stream(getRockIds())
                .map(RockOres::getOreForRock)
                .filter(oreId -> oreId != -1)
                .toArray();
    }

    /**
     * Get bank coordinates based on configured bank.
     * 
     * @return WorldPoint of the bank or null if unknown
     */
    public WorldPoint getBankCoordinates() {
        String bankName = config.miningBank();
        log.info("Bank name: {}", bankName);
        
        switch (bankName) {
            case "VARROCK_EAST":
                return Banks.VARROCK_EAST.getBankCoordinates();
            case "INTERIOR_TEST":
                return Banks.INTERIOR_TEST.getBankCoordinates();
            default:
                log.warn("Unknown bank: {}", bankName);
                return null;
        }
    }

    /**
     * Get the current bot configuration.
     * 
     * @return BotConfig instance
     */
    public BotConfig getConfig() {
        return config;
    }
}