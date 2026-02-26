package com.runepal.agent.script;

public enum ScriptConditionType {
    ALWAYS,
    INVENTORY_FULL,
    INVENTORY_EMPTY,
    PLAYER_IDLE,
    HAS_ITEM,
    IS_DROPPING,
    IS_INTERACTING,
    CURRENT_STATE_CONTAINS,
    RANDOM_CHANCE
}
