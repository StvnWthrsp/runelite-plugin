package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runepal.agent.memory.AgentMemoryEntry;

import java.util.ArrayList;
import java.util.List;

public final class MemorySearchTool implements AgentTool {
    @Override
    public String getName() {
        return "memory.search";
    }

    @Override
    public String getDescription() {
        return "Searches local memory entries";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject query = new JsonObject();
        query.addProperty("name", "query");
        query.addProperty("type", "string");
        args.add(query);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        String query = readString(arguments, "query", "");
        int limit = readInt(arguments, "limit", 5);

        List<String> tags = new ArrayList<>();
        if (arguments != null && arguments.has("tags") && arguments.get("tags").isJsonArray()) {
            for (JsonElement element : arguments.getAsJsonArray("tags")) {
                if (element.isJsonPrimitive()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty()) {
                        tags.add(value);
                    }
                }
            }
        }

        List<AgentMemoryEntry> matches = context.getMemoryStore().search(query, limit, tags);
        JsonArray entries = new JsonArray();
        for (AgentMemoryEntry match : matches) {
            entries.add(match.toJson());
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("count", matches.size());
        payload.add("entries", entries);
        return AgentToolResult.ok(payload);
    }

    private String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
