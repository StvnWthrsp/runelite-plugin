package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.agent.AgentSkillTemplate;

import java.util.Optional;

public final class BotRunTemplateTool implements AgentTool {
    @Override
    public String getName() {
        return "bot.run_template";
    }

    @Override
    public String getDescription() {
        return "Runs one of the built-in automation templates";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject template = new JsonObject();
        template.addProperty("name", "template");
        template.addProperty("type", "string");
        template.addProperty("required", true);
        args.add(template);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        if (context.getAgentService() == null) {
            return AgentToolResult.error("Agent service unavailable", new JsonObject());
        }
        String templateName = readString(arguments, "template", "");
        Optional<AgentSkillTemplate> template = AgentSkillTemplate.fromWireName(templateName);
        if (!template.isPresent()) {
            return AgentToolResult.error("Unknown template: " + templateName, new JsonObject());
        }

        JsonObject params = arguments != null && arguments.has("params") && arguments.get("params").isJsonObject()
                ? arguments.getAsJsonObject("params").deepCopy()
                : new JsonObject();
        context.getAgentService().queueRunSkill(template.get(), params);

        JsonObject payload = new JsonObject();
        payload.addProperty("queued", true);
        payload.addProperty("template", template.get().getWireName());
        return AgentToolResult.ok(payload);
    }

    private String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
