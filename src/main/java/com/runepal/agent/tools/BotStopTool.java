package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class BotStopTool implements AgentTool {
    @Override
    public String getName() {
        return "bot.stop";
    }

    @Override
    public String getDescription() {
        return "Stops active automation";
    }

    @Override
    public JsonArray getArgumentHints() {
        return new JsonArray();
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        if (context.getAgentService() == null) {
            return AgentToolResult.error("Agent service unavailable", new JsonObject());
        }
        context.getAgentService().stopAllFromUi();
        JsonObject payload = new JsonObject();
        payload.addProperty("stopped", true);
        return AgentToolResult.ok(payload);
    }
}
