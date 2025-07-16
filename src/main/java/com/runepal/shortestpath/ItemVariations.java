package com.runepal.shortestpath;

import net.runelite.api.ItemID;
import static net.runelite.api.ItemID.*;
import lombok.Getter;

public enum ItemVariations {
    AIR_RUNE(ItemID.AIR_RUNE,
        ItemID.MIST_RUNE,
        ItemID.DUST_RUNE,
        ItemID.SMOKE_RUNE),
    ARDOUGNE_CLOAK(ItemID.ARDOUGNE_CLOAK_1,
        ItemID.ARDOUGNE_CLOAK_2,
        ItemID.ARDOUGNE_CLOAK_3,
        ItemID.ARDOUGNE_CLOAK_4,
        ItemID.ARDOUGNE_MAX_CAPE),
    ASTRAL_RUNE(ItemID.ASTRAL_RUNE),
    AXE(BRONZE_AXE,
        IRON_AXE,
        STEEL_AXE,
        BLACK_AXE,
        MITHRIL_AXE,
        ADAMANT_AXE,
        RUNE_AXE,
        DRAGON_AXE,
        INFERNAL_AXE,
        _3RD_AGE_AXE,
        CRYSTAL_AXE),
    BANANA(ItemID.BANANA),
    BLOOD_RUNE(ItemID.BLOOD_RUNE),
    BROWN_APRON(ItemID.BROWN_APRON,
        ItemID.GOLDEN_APRON,
        ItemID.CRAFTING_CAPE,
        ItemID.CRAFTING_CAPET,
        ItemID.CRAFTING_HOOD),
    BRYOPHYTAS_STAFF(ItemID.BRYOPHYTAS_STAFF),
    CAPESLOT(CASTLEWARS_HOOD, // TODO: also use slot or item category
        CASTLEWARS_HOOD_4515),
    CLIMBING_BOOTS(ItemID.CLIMBING_BOOTS,
        ItemID.CLIMBING_BOOTS_G),
    COINS(ItemID.COINS_995),
    CROSSBOW(ItemID.CROSSBOW,
        PHOENIX_CROSSBOW,
        DORGESHUUN_CROSSBOW,
        HUNTERS_CROSSBOW,
        BRONZE_CROSSBOW,
        IRON_CROSSBOW,
        STEEL_CROSSBOW,
        MITHRIL_CROSSBOW,
        ADAMANT_CROSSBOW,
        RUNE_CROSSBOW,
        DRAGON_CROSSBOW,
        DRAGON_HUNTER_CROSSBOW,
        KARILS_CROSSBOW,
        KARILS_CROSSBOW_0,
        KARILS_CROSSBOW_25,
        KARILS_CROSSBOW_50,
        KARILS_CROSSBOW_75,
        KARILS_CROSSBOW_100,
        ARMADYL_CROSSBOW,
        ZARYTE_CROSSBOW),
    DUST_BATTLESTAFF(ItemID.DUST_BATTLESTAFF,
        ItemID.MYSTIC_DUST_STAFF),
    DUST_RUNE(ItemID.DUST_RUNE),
    DUSTY_KEY(ItemID.DUSTY_KEY),
    EARTH_RUNE(ItemID.EARTH_RUNE,
        ItemID.DUST_RUNE,
        ItemID.MUD_RUNE,
        ItemID.LAVA_RUNE),
    ECTO_TOKEN(ItemID.ECTOTOKEN),
    FIRE_RUNE(ItemID.FIRE_RUNE,
        ItemID.SMOKE_RUNE,
        ItemID.STEAM_RUNE,
        ItemID.LAVA_RUNE),
    GAMES_NECKLACE(ItemID.GAMES_NECKLACE8,
        ItemID.GAMES_NECKLACE7,
        ItemID.GAMES_NECKLACE6,
        ItemID.GAMES_NECKLACE5,
        ItemID.GAMES_NECKLACE4,
        ItemID.GAMES_NECKLACE3,
        ItemID.GAMES_NECKLACE2,
        ItemID.GAMES_NECKLACE1),
    GLOWING_FUNGUS(ItemID.GLOWING_FUNGUS),
    HEADSLOT(CASTLEWARS_CLOAK, // TODO: also use slot or item category
        CASTLEWARS_CLOAK_4516),
    LAVA_BATTLESTAFF(ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    LAVA_RUNE(ItemID.LAVA_RUNE),
    LAW_RUNE(ItemID.LAW_RUNE),
    MAX_CAPE(ItemID.MAX_CAPE,
        MAX_CAPE_13342,
        FIRE_MAX_CAPE,
        FIRE_MAX_CAPE_21186,
        FIRE_MAX_CAPE_L,
        SARADOMIN_MAX_CAPE,
        ZAMORAK_MAX_CAPE,
        GUTHIX_MAX_CAPE,
        ACCUMULATOR_MAX_CAPE,
        ARDOUGNE_MAX_CAPE,
        INFERNAL_MAX_CAPE,
        INFERNAL_MAX_CAPE_21285,
        INFERNAL_MAX_CAPE_L,
        IMBUED_SARADOMIN_MAX_CAPE,
        IMBUED_SARADOMIN_MAX_CAPE_L,
        IMBUED_ZAMORAK_MAX_CAPE,
        IMBUED_ZAMORAK_MAX_CAPE_L,
        IMBUED_GUTHIX_MAX_CAPE,
        IMBUED_GUTHIX_MAX_CAPE_L,
        ASSEMBLER_MAX_CAPE,
        ASSEMBLER_MAX_CAPE_L,
        MYTHICAL_MAX_CAPE,
        MASORI_ASSEMBLER_MAX_CAPE,
        MASORI_ASSEMBLER_MAX_CAPE_L,
        DIZANAS_MAX_CAPE,
        DIZANAS_MAX_CAPE_L),
    MAX_HOOD(ItemID.MAX_HOOD,
        FIRE_MAX_HOOD,
        SARADOMIN_MAX_HOOD,
        ZAMORAK_MAX_HOOD,
        GUTHIX_MAX_HOOD,
        ACCUMULATOR_MAX_HOOD,
        ARDOUGNE_MAX_HOOD,
        INFERNAL_MAX_HOOD,
        IMBUED_SARADOMIN_MAX_HOOD,
        IMBUED_ZAMORAK_MAX_HOOD,
        IMBUED_GUTHIX_MAX_HOOD,
        ASSEMBLER_MAX_HOOD,
        MYTHICAL_MAX_HOOD,
        MASORI_ASSEMBLER_MAX_HOOD,
        DIZANAS_MAX_HOOD),
    MAZE_KEY(ItemID.MAZE_KEY),
    MIND_RUNE(ItemID.MIND_RUNE),
    MIST_BATTLESTAFF(ItemID.MIST_BATTLESTAFF,
        ItemID.MYSTIC_MIST_STAFF),
    MIST_RUNE(ItemID.MIST_RUNE),
    MITH_GRAPPLE(ItemID.MITH_GRAPPLE_9419),
    MUD_BATTLESTAFF(ItemID.MUD_BATTLESTAFF,
        ItemID.MYSTIC_MUD_STAFF),
    MUD_RUNE(ItemID.MUD_RUNE),
    MYSTIC_DUST_STAFF(ItemID.MYSTIC_DUST_STAFF),
    MYSTIC_LAVA_STAFF(ItemID.MYSTIC_LAVA_STAFF),
    MYSTIC_MIST_STAFF(ItemID.MYSTIC_MIST_STAFF),
    MYSTIC_MUD_STAFF(ItemID.MYSTIC_MUD_STAFF),
    MYSTIC_SMOKE_STAFF(ItemID.MYSTIC_SMOKE_STAFF),
    MYSTIC_STEAM_STAFF(ItemID.MYSTIC_STEAM_STAFF),
    NATURE_RUNE(ItemID.NATURE_RUNE),
    ROPE(ItemID.ROPE),
    SHANTAY_PASS(ItemID.SHANTAY_PASS),
    SKAVID_MAP(ItemID.SKAVID_MAP),
    SMOKE_BATTLESTAFF(ItemID.SMOKE_BATTLESTAFF,
        ItemID.MYSTIC_SMOKE_STAFF),
    SMOKE_RUNE(ItemID.SMOKE_RUNE),
    SOUL_RUNE(ItemID.SOUL_RUNE),
    STAFF_OF_AIR(ItemID.STAFF_OF_AIR,
        ItemID.MIST_BATTLESTAFF,
        ItemID.DUST_BATTLESTAFF,
        ItemID.SMOKE_BATTLESTAFF,
        ItemID.MYSTIC_MIST_STAFF,
        ItemID.MYSTIC_DUST_STAFF,
        ItemID.MYSTIC_SMOKE_STAFF),
    STAFF_OF_EARTH(ItemID.STAFF_OF_EARTH,
        ItemID.DUST_BATTLESTAFF,
        ItemID.MUD_BATTLESTAFF,
        ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_DUST_STAFF,
        ItemID.MYSTIC_MUD_STAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    STAFF_OF_FIRE(ItemID.STAFF_OF_FIRE,
        ItemID.SMOKE_BATTLESTAFF,
        ItemID.STEAM_BATTLESTAFF,
        ItemID.LAVA_BATTLESTAFF,
        ItemID.MYSTIC_SMOKE_STAFF,
        ItemID.MYSTIC_STEAM_STAFF,
        ItemID.MYSTIC_LAVA_STAFF),
    STAFF_OF_WATER(ItemID.STAFF_OF_WATER,
        ItemID.MIST_BATTLESTAFF,
        ItemID.MUD_BATTLESTAFF,
        ItemID.STEAM_BATTLESTAFF,
        ItemID.MYSTIC_MIST_STAFF,
        ItemID.MYSTIC_MUD_STAFF,
        ItemID.MYSTIC_STEAM_STAFF),
    STEAM_BATTLESTAFF(ItemID.STEAM_BATTLESTAFF,
        ItemID.MYSTIC_STEAM_STAFF),
    STEAM_RUNE(ItemID.STEAM_RUNE),
    TOME_OF_EARTH(ItemID.TOME_OF_EARTH),    
    TOME_OF_FIRE(ItemID.TOME_OF_FIRE),
    TOME_OF_WATER(ItemID.TOME_OF_WATER),
    WATER_RUNE(ItemID.WATER_RUNE,
        ItemID.MIST_RUNE,
        ItemID.MUD_RUNE,
        ItemID.STEAM_RUNE),
    ;

    @Getter
    private final int[] ids;

    ItemVariations(int... ids) {
        this.ids = ids;
    }

    public static int[] staves(ItemVariations itemVariation) {
        if (itemVariation == null) {
            return null;
        }
        switch (itemVariation) {
            case AIR_RUNE:
                return STAFF_OF_AIR.ids;
            case DUST_RUNE:
                return DUST_BATTLESTAFF.ids;
            case EARTH_RUNE:
                return STAFF_OF_EARTH.ids;
            case FIRE_RUNE:
                return STAFF_OF_FIRE.ids;
            case LAVA_RUNE:
                return LAVA_BATTLESTAFF.ids;
            case MIST_RUNE:
                return MIST_BATTLESTAFF.ids;
            case MUD_RUNE:
                return MUD_BATTLESTAFF.ids;
            case NATURE_RUNE:
                return BRYOPHYTAS_STAFF.ids;
            case SMOKE_RUNE:
                return SMOKE_BATTLESTAFF.ids;
            case STEAM_RUNE:
                return STEAM_BATTLESTAFF.ids;
            case WATER_RUNE:
                return STAFF_OF_WATER.ids;
            default:
                return null;
        }
    }

    public static int[] offhands(ItemVariations itemVariation) {
        if (itemVariation == null) {
            return null;
        }
        switch (itemVariation) {
            case EARTH_RUNE:
                return TOME_OF_EARTH.ids;
            case FIRE_RUNE:
                return TOME_OF_FIRE.ids;
            case WATER_RUNE:
                return TOME_OF_WATER.ids;
            default:
                return null;
        }
    }

    public static ItemVariations fromName(String name) {
        for (ItemVariations itemVariations : ItemVariations.values()) {
            if (itemVariations.name().equals(name)) {
                return itemVariations;
            }
        }
        return null;
    }
}