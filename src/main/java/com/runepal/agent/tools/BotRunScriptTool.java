package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class BotRunScriptTool implements AgentTool {
    @Override
    public String getName() {
        return "bot.run_script";
    }

    @Override
    public String getDescription() {
        return "Runs a script by name or JSON spec";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject name = new JsonObject();
        name.addProperty("name", "name");
        name.addProperty("type", "string");
        args.add(name);

        JsonObject script = new JsonObject();
        script.addProperty("name", "script");
        script.addProperty("type", "object");
        args.add(script);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        if (context.getAgentService() == null) {
            return AgentToolResult.error("Agent service unavailable", new JsonObject());
        }
        if (arguments != null && arguments.has("name") && !arguments.get("name").isJsonNull()) {
            String name = arguments.get("name").getAsString();
            context.getAgentService().queueRunScriptByName(name);
            JsonObject payload = new JsonObject();
            payload.addProperty("queued", true);
            payload.addProperty("name", name);
            return AgentToolResult.ok(payload);
        }

        if (arguments != null && arguments.has("script") && arguments.get("script").isJsonObject()) {
            JsonObject script = arguments.getAsJsonObject("script").deepCopy();
            context.getAgentService().queueRunScriptJson(script, true);
            JsonObject payload = new JsonObject();
            payload.addProperty("queued", true);
            payload.addProperty("name", script.has("name") ? script.get("name").getAsString() : "");
            return AgentToolResult.ok(payload);
        }

        return AgentToolResult.error("bot.run_script requires either name or script", new JsonObject());
    }
}
