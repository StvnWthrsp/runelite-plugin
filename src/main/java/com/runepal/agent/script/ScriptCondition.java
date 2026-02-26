package com.runepal.agent.script;

import com.google.gson.JsonObject;

import java.util.Objects;

public final class ScriptCondition {
    private final ScriptConditionType type;
    private final JsonObject params;

    public ScriptCondition(ScriptConditionType type, JsonObject params) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.params = params == null ? new JsonObject() : params.deepCopy();
    }

    public ScriptConditionType getType() {
        return type;
    }

    public JsonObject getParams() {
        return params.deepCopy();
    }
}
