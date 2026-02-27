package com.runepal.harness.action;

import com.google.gson.JsonObject;

public final class HarnessAction {
    private final String actionId;
    private final String type;
    private final JsonObject payload;

    public HarnessAction(String actionId, String type, JsonObject payload) {
        this.actionId = actionId;
        this.type = type;
        this.payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    public String getActionId() {
        return actionId;
    }

    public String getType() {
        return type;
    }

    public JsonObject getPayload() {
        return payload.deepCopy();
    }
}
