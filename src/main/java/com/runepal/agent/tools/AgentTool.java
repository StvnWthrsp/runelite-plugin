package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public interface AgentTool {
    String getName();

    String getDescription();

    JsonArray getArgumentHints();

    AgentToolResult execute(JsonObject arguments, AgentToolContext context);
}
