package com.runepal.agent.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AgentMemoryEntry {
    private final Instant timestamp;
    private final String title;
    private final List<String> tags;
    private final String content;
    private final JsonObject evidence;

    public AgentMemoryEntry(Instant timestamp, String title, List<String> tags, String content, JsonObject evidence) {
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.title = title == null ? "" : title;
        this.tags = tags == null ? Collections.emptyList() : new ArrayList<>(tags);
        this.content = content == null ? "" : content;
        this.evidence = evidence == null ? new JsonObject() : evidence.deepCopy();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getTags() {
        return new ArrayList<>(tags);
    }

    public String getContent() {
        return content;
    }

    public JsonObject getEvidence() {
        return evidence.deepCopy();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("title", title);
        JsonArray tagsArray = new JsonArray();
        for (String tag : tags) {
            tagsArray.add(tag);
        }
        json.add("tags", tagsArray);
        json.addProperty("content", content);
        json.add("evidence", evidence.deepCopy());
        return json;
    }

    public static AgentMemoryEntry fromJson(JsonObject json) {
        if (json == null) {
            return new AgentMemoryEntry(Instant.now(), "", Collections.emptyList(), "", new JsonObject());
        }

        Instant timestamp;
        try {
            timestamp = json.has("timestamp") ? Instant.parse(json.get("timestamp").getAsString()) : Instant.now();
        } catch (Exception ignored) {
            timestamp = Instant.now();
        }

        String title = json.has("title") ? json.get("title").getAsString() : "";
        String content = json.has("content") ? json.get("content").getAsString() : "";

        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (JsonElement tag : json.getAsJsonArray("tags")) {
                if (tag.isJsonPrimitive()) {
                    String value = tag.getAsString().trim();
                    if (!value.isEmpty()) {
                        tags.add(value);
                    }
                }
            }
        }

        JsonObject evidence = json.has("evidence") && json.get("evidence").isJsonObject()
                ? json.getAsJsonObject("evidence").deepCopy()
                : new JsonObject();
        return new AgentMemoryEntry(timestamp, title, tags, content, evidence);
    }
}
