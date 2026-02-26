package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class TraceGetRecentTool implements AgentTool {
    @Override
    public String getName() {
        return "trace.get_recent";
    }

    @Override
    public String getDescription() {
        return "Returns recent structured trace events";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject limit = new JsonObject();
        limit.addProperty("name", "limit");
        limit.addProperty("type", "number");
        args.add(limit);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        int limit = 20;
        if (arguments != null && arguments.has("limit") && !arguments.get("limit").isJsonNull()) {
            try {
                limit = Math.max(1, Math.min(100, arguments.get("limit").getAsInt()));
            } catch (Exception ignored) {
                limit = 20;
            }
        }
        JsonObject payload = new JsonObject();
        payload.add("events", context.getTraceService().getRecentJson(limit));
        return AgentToolResult.ok(payload);
    }
}
