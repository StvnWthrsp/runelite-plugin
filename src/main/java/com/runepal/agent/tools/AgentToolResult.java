package com.runepal.agent.tools;

import com.google.gson.JsonObject;

public final class AgentToolResult {
    private final boolean ok;
    private final String message;
    private final JsonObject payload;

    private AgentToolResult(boolean ok, String message, JsonObject payload) {
        this.ok = ok;
        this.message = message == null ? "" : message;
        this.payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    public static AgentToolResult ok(JsonObject payload) {
        return new AgentToolResult(true, "ok", payload);
    }

    public static AgentToolResult error(String message, JsonObject payload) {
        return new AgentToolResult(false, message, payload);
    }

    public boolean isOk() {
        return ok;
    }

    public String getMessage() {
        return message;
    }

    public JsonObject getPayload() {
        return payload.deepCopy();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", ok);
        json.addProperty("message", message);
        json.add("payload", payload.deepCopy());
        return json;
    }
}
