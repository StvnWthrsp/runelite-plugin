package com.runepal.agent.trace;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.Objects;

public final class TraceEvent {
    private final Instant timestamp;
    private final String category;
    private final String message;
    private final JsonObject payload;

    public TraceEvent(Instant timestamp, String category, String message, JsonObject payload) {
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        this.category = category == null ? "general" : category;
        this.message = message == null ? "" : message;
        this.payload = payload == null ? new JsonObject() : payload.deepCopy();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public JsonObject getPayload() {
        return payload.deepCopy();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("category", category);
        json.addProperty("message", message);
        json.add("payload", payload.deepCopy());
        return json;
    }
}
