package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runepal.agent.memory.AgentMemoryEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MemoryAddTool implements AgentTool {
    @Override
    public String getName() {
        return "memory.add";
    }

    @Override
    public String getDescription() {
        return "Appends a local memory entry";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject title = new JsonObject();
        title.addProperty("name", "title");
        title.addProperty("type", "string");
        title.addProperty("required", true);
        args.add(title);

        JsonObject content = new JsonObject();
        content.addProperty("name", "content");
        content.addProperty("type", "string");
        content.addProperty("required", true);
        args.add(content);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        String title = readString(arguments, "title", "");
        String content = readString(arguments, "content", "");
        JsonObject evidence = arguments != null && arguments.has("evidence") && arguments.get("evidence").isJsonObject()
                ? arguments.getAsJsonObject("evidence").deepCopy()
                : new JsonObject();

        List<String> tags = new ArrayList<>();
        if (arguments != null && arguments.has("tags") && arguments.get("tags").isJsonArray()) {
            for (JsonElement element : arguments.getAsJsonArray("tags")) {
                if (element.isJsonPrimitive()) {
                    String tag = element.getAsString().trim();
                    if (!tag.isEmpty()) {
                        tags.add(tag);
                    }
                }
            }
        }

        AgentMemoryEntry entry = new AgentMemoryEntry(Instant.now(), title, tags, content, evidence);
        context.getMemoryStore().append(entry);

        JsonObject payload = new JsonObject();
        payload.addProperty("saved", true);
        payload.add("entry", entry.toJson());
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
}
