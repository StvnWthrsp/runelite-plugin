package com.runepal.harness.action;

import com.google.gson.JsonObject;

public final class HarnessActionStatusEvent {
    private final String actionId;
    private final String actionType;
    private final String status;
    private final String code;
    private final String message;
    private final long tick;

    public HarnessActionStatusEvent(String actionId, String actionType, String status, String code, String message, long tick) {
        this.actionId = actionId;
        this.actionType = actionType;
        this.status = status;
        this.code = code;
        this.message = message;
        this.tick = tick;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("actionId", actionId == null ? "" : actionId);
        json.addProperty("actionType", actionType == null ? "" : actionType);
        json.addProperty("status", status == null ? "" : status);
        json.addProperty("code", code == null ? "" : code);
        json.addProperty("message", message == null ? "" : message);
        json.addProperty("tick", tick);
        return json;
    }
}
