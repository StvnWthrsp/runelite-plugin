package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentToolRegistry {
    private final Map<String, AgentTool> toolsByName = new LinkedHashMap<>();

    public synchronized void register(AgentTool tool) {
        if (tool == null) {
            return;
        }
        toolsByName.put(tool.getName(), tool);
    }

    public synchronized AgentTool get(String name) {
        return toolsByName.get(name);
    }

    public synchronized Collection<AgentTool> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(toolsByName.values()));
    }

    public synchronized JsonArray toManifestJson() {
        JsonArray manifest = new JsonArray();
        for (AgentTool tool : toolsByName.values()) {
            JsonObject item = new JsonObject();
            item.addProperty("name", tool.getName());
            item.addProperty("description", tool.getDescription());
            item.add("arguments", tool.getArgumentHints());
            manifest.add(item);
        }
        return manifest;
    }
}
