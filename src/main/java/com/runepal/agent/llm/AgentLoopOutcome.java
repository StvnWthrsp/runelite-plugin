package com.runepal.agent.llm;

import com.google.gson.JsonObject;

public final class AgentLoopOutcome {
    private final boolean success;
    private final JsonObject finalPayload;
    private final String error;

    private AgentLoopOutcome(boolean success, JsonObject finalPayload, String error) {
        this.success = success;
        this.finalPayload = finalPayload == null ? new JsonObject() : finalPayload.deepCopy();
        this.error = error;
    }

    public static AgentLoopOutcome success(JsonObject payload) {
        return new AgentLoopOutcome(true, payload, null);
    }

    public static AgentLoopOutcome error(String error, JsonObject payload) {
        return new AgentLoopOutcome(false, payload, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public JsonObject getFinalPayload() {
        return finalPayload.deepCopy();
    }

    public String getError() {
        return error;
    }
}
