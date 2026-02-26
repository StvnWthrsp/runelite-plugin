package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class GameSnapshotTool implements AgentTool {
    @Override
    public String getName() {
        return "game.snapshot";
    }

    @Override
    public String getDescription() {
        return "Returns a compact game state snapshot";
    }

    @Override
    public JsonArray getArgumentHints() {
        return new JsonArray();
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        JsonObject snapshot = context.getLatestSnapshot();
        JsonObject compact = new JsonObject();
        if (snapshot.has("timestamp")) {
            compact.add("timestamp", snapshot.get("timestamp"));
        }
        if (snapshot.has("gameState")) {
            compact.add("gameState", snapshot.get("gameState"));
        }
        if (snapshot.has("bot")) {
            compact.add("bot", snapshot.get("bot").deepCopy());
        }
        if (snapshot.has("player")) {
            compact.add("player", snapshot.get("player").deepCopy());
        }
        if (snapshot.has("inventory")) {
            compact.add("inventory", snapshot.get("inventory").deepCopy());
        }
        if (snapshot.has("nearbyNpcs")) {
            compact.add("nearbyNpcs", snapshot.get("nearbyNpcs").deepCopy());
        }
        return AgentToolResult.ok(compact);
    }
}
