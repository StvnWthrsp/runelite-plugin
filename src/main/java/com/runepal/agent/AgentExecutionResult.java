package com.runepal.agent;

import com.google.gson.JsonObject;

public final class AgentExecutionResult {
    private final boolean success;
    private final String message;
    private final JsonObject payload;

    private AgentExecutionResult(boolean success, String message, JsonObject payload) {
        this.success = success;
        this.message = message;
        this.payload = payload;
    }

    public static AgentExecutionResult success(String message, JsonObject payload) {
        return new AgentExecutionResult(true, message, payload);
    }

    public static AgentExecutionResult error(String message, JsonObject payload) {
        return new AgentExecutionResult(false, message, payload);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public JsonObject getPayload() {
        return payload;
    }
}
