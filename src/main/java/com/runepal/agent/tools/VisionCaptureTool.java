package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public final class VisionCaptureTool implements AgentTool {
    @Override
    public String getName() {
        return "vision.capture";
    }

    @Override
    public String getDescription() {
        return "Captures an on-demand downscaled screenshot";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject maxWidth = new JsonObject();
        maxWidth.addProperty("name", "maxWidth");
        maxWidth.addProperty("type", "number");
        args.add(maxWidth);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        if (context.getSnapshotBuilder() == null) {
            return AgentToolResult.error("Snapshot builder unavailable", new JsonObject());
        }
        int maxWidth = 640;
        if (arguments != null && arguments.has("maxWidth") && !arguments.get("maxWidth").isJsonNull()) {
            try {
                maxWidth = Math.max(320, Math.min(1280, arguments.get("maxWidth").getAsInt()));
            } catch (Exception ignored) {
                maxWidth = 640;
            }
        }

        JsonObject capture = context.getSnapshotBuilder().captureScreenshot(maxWidth);
        if (capture == null || !capture.has("jpegBase64")) {
            return AgentToolResult.error("Unable to capture screenshot", new JsonObject());
        }
        return AgentToolResult.ok(capture);
    }
}
