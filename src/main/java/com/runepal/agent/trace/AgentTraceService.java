package com.runepal.agent.trace;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AgentTraceService {
    private static final int DEFAULT_CAPACITY = 200;

    private final int capacity;
    private final ArrayDeque<TraceEvent> events;

    public AgentTraceService() {
        this(DEFAULT_CAPACITY);
    }

    public AgentTraceService(int capacity) {
        this.capacity = Math.max(10, capacity);
        this.events = new ArrayDeque<>(this.capacity);
    }

    public synchronized TraceEvent record(String category, String message, JsonObject payload) {
        TraceEvent event = new TraceEvent(Instant.now(), category, message, payload);
        if (events.size() >= capacity) {
            events.removeFirst();
        }
        events.addLast(event);
        return event;
    }

    public TraceEvent record(String category, String message) {
        return record(category, message, new JsonObject());
    }

    public synchronized List<TraceEvent> getRecent(int limit) {
        int max = Math.max(1, limit);
        List<TraceEvent> copy = new ArrayList<>(events);
        int start = Math.max(0, copy.size() - max);
        return new ArrayList<>(copy.subList(start, copy.size()));
    }

    public synchronized JsonArray getRecentJson(int limit) {
        JsonArray array = new JsonArray();
        for (TraceEvent event : getRecent(limit)) {
            array.add(event.toJson());
        }
        return array;
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized JsonObject toSnapshot(int limit) {
        JsonObject json = new JsonObject();
        json.addProperty("size", events.size());
        json.add("events", getRecentJson(limit));
        return json;
    }
}
