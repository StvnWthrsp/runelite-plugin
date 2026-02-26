package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class BotStatusTool implements AgentTool {
    @Override
    public String getName() {
        return "bot.status";
    }

    @Override
    public String getDescription() {
        return "Returns goal, decision, and runtime bot status";
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
        return AgentToolResult.ok(context.getAgentService().getBotStatusSnapshot());
    }
}
