package com.runepal.agent;

import com.google.gson.JsonObject;

import java.time.Instant;

public class AgentGoalStore {
    private String goal = "";
    private Instant updatedAt = Instant.EPOCH;

    public synchronized void setGoal(String goal) {
        this.goal = goal == null ? "" : goal.trim();
        this.updatedAt = Instant.now();
    }

    public synchronized String getGoal() {
        return goal;
    }

    public synchronized JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("goal", goal);
        if (!Instant.EPOCH.equals(updatedAt)) {
            json.addProperty("updatedAt", updatedAt.toString());
        }
        return json;
    }
}
