package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.agent.wiki.WikiFetchResult;
import com.runepal.agent.wiki.WikiFormat;

public final class WikiFetchTool implements AgentTool {
    @Override
    public String getName() {
        return "wiki.fetch";
    }

    @Override
    public String getDescription() {
        return "Fetches OSRS Wiki page content";
    }

    @Override
    public JsonArray getArgumentHints() {
        JsonArray args = new JsonArray();
        JsonObject title = new JsonObject();
        title.addProperty("name", "title");
        title.addProperty("type", "string");
        title.addProperty("required", true);
        args.add(title);

        JsonObject section = new JsonObject();
        section.addProperty("name", "section");
        section.addProperty("type", "number");
        args.add(section);

        JsonObject format = new JsonObject();
        format.addProperty("name", "format");
        format.addProperty("type", "string");
        format.addProperty("enum", "wikitext|text");
        args.add(format);
        return args;
    }

    @Override
    public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
        String title = readString(arguments, "title", "");
        Integer section = readOptionalInt(arguments, "section");
        String formatRaw = readString(arguments, "format", "wikitext");
        WikiFormat format = "text".equalsIgnoreCase(formatRaw) ? WikiFormat.TEXT : WikiFormat.WIKITEXT;
        WikiFetchResult result = context.getWikiClient().fetch(title, section, format);
        return AgentToolResult.ok(context.getWikiClient().toPreviewJson(result));
    }

    private Integer readOptionalInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
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
