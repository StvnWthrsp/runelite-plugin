package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.agent.wiki.WikiSearchResult;

public final class WikiSearchTool implements AgentTool {
    @Override
    public String getName() {
        return "wiki.search";
    }

    @Override
    public String getDescription() {
        return "Searches OSRS Wiki pages by query";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject query = new JsonObject();
        query.addProperty("name", "query");
        query.addProperty("type", "string");
        query.addProperty("required", true);
        args.add(query);

        JsonObject limit = new JsonObject();
        limit.addProperty("name", "limit");
        limit.addProperty("type", "number");
        args.add(limit);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        String query = readString(arguments, "query", "");
        int limit = readInt(arguments, "limit", 3);
        WikiSearchResult result = context.getWikiClient().search(query, limit);
        return AgentToolResult.ok(result.toJson());
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
