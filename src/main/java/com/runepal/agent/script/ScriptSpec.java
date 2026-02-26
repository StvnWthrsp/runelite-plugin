package com.runepal.agent.script;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ScriptSpec {
    private final String name;
    private final String entryState;
    private final Map<String, List<ScriptStep>> states;
    private final JsonObject source;

    public ScriptSpec(String name, String entryState, Map<String, List<ScriptStep>> states, JsonObject source) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.entryState = Objects.requireNonNull(entryState, "entryState cannot be null");
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(states, "states cannot be null")));
        this.source = source == null ? new JsonObject() : source.deepCopy();
    }

    public String getName() {
        return name;
    }

    public String getEntryState() {
        return entryState;
    }

    public Map<String, List<ScriptStep>> getStates() {
        return states;
    }

    public JsonObject toJson() {
        return source.deepCopy();
    }
}
