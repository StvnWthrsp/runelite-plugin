package com.runepal.agent.tools;

import com.google.gson.JsonObject;
import com.runepal.agent.trace.AgentTraceService;

import java.util.Objects;

public final class AgentToolDispatcher {
    private final AgentToolRegistry registry;
    private final AgentToolContext context;
    private final AgentTraceService traceService;

    public AgentToolDispatcher(AgentToolRegistry registry, AgentToolContext context, AgentTraceService traceService) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.traceService = Objects.requireNonNull(traceService, "traceService cannot be null");
    }

    public AgentToolResult dispatch(String name, JsonObject arguments, String callId) {
        JsonObject safeArguments = arguments == null ? new JsonObject() : arguments.deepCopy();

        JsonObject callPayload = new JsonObject();
        callPayload.addProperty("tool", name);
        callPayload.addProperty("callId", callId == null ? "" : callId);
        callPayload.add("arguments", safeArguments);
        traceService.record("tool_call", "tool called", callPayload);

        AgentTool tool = registry.get(name);
        if (tool == null) {
            JsonObject errorPayload = new JsonObject();
            errorPayload.addProperty("tool", name);
            AgentToolResult missing = AgentToolResult.error("Unknown tool: " + name, errorPayload);
            traceService.record("tool_result", "tool failed", missing.toJson());
            return missing;
        }

        try {
            AgentToolResult result = tool.execute(safeArguments, context);
            JsonObject resultPayload = result.toJson();
            resultPayload.addProperty("tool", name);
            resultPayload.addProperty("callId", callId == null ? "" : callId);
            traceService.record("tool_result", result.isOk() ? "tool ok" : "tool error", resultPayload);
            return result;
        } catch (Exception e) {
            JsonObject errorPayload = new JsonObject();
            errorPayload.addProperty("tool", name);
            errorPayload.addProperty("error", e.getMessage());
            AgentToolResult result = AgentToolResult.error("Tool execution failed: " + e.getMessage(), errorPayload);
            traceService.record("tool_result", "tool exception", result.toJson());
            return result;
        }
    }
}
