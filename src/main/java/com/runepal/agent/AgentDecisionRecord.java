package com.runepal.agent;

import com.google.gson.JsonObject;

import java.time.Instant;

public final class AgentDecisionRecord {
    private final Instant timestamp;
    private final String goal;
    private final String source;
    private final String decisionType;
    private final String reason;
    private final String templateName;
    private final String scriptName;
    private final boolean queuedExecution;
    private final String error;

    private AgentDecisionRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.goal = builder.goal;
        this.source = builder.source;
        this.decisionType = builder.decisionType;
        this.reason = builder.reason;
        this.templateName = builder.templateName;
        this.scriptName = builder.scriptName;
        this.queuedExecution = builder.queuedExecution;
        this.error = builder.error;
    }

    public static Builder builder() {
        return new Builder();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("goal", goal);
        json.addProperty("source", source);
        json.addProperty("decisionType", decisionType);
        json.addProperty("reason", reason);
        json.addProperty("queuedExecution", queuedExecution);
        if (templateName != null) {
            json.addProperty("templateName", templateName);
        }
        if (scriptName != null) {
            json.addProperty("scriptName", scriptName);
        }
        if (error != null) {
            json.addProperty("error", error);
        }
        return json;
    }

    public String getSource() {
        return source;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public String getReason() {
        return reason;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getScriptName() {
        return scriptName;
    }

    public boolean isQueuedExecution() {
        return queuedExecution;
    }

    public static final class Builder {
        private Instant timestamp = Instant.now();
        private String goal = "";
        private String source = "heuristic";
        private String decisionType = "idle";
        private String reason = "";
        private String templateName;
        private String scriptName;
        private boolean queuedExecution;
        private String error;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder goal(String goal) {
            this.goal = goal == null ? "" : goal;
            return this;
        }

        public Builder source(String source) {
            this.source = source == null ? "heuristic" : source;
            return this;
        }

        public Builder decisionType(String decisionType) {
            this.decisionType = decisionType == null ? "idle" : decisionType;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason == null ? "" : reason;
            return this;
        }

        public Builder templateName(String templateName) {
            this.templateName = templateName;
            return this;
        }

        public Builder scriptName(String scriptName) {
            this.scriptName = scriptName;
            return this;
        }

        public Builder queuedExecution(boolean queuedExecution) {
            this.queuedExecution = queuedExecution;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public AgentDecisionRecord build() {
            return new AgentDecisionRecord(this);
        }
    }
}
