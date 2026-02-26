package com.runepal.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runepal.BotConfig;
import com.runepal.BotType;
import com.runepal.FishingMode;
import com.runepal.MiningMode;
import com.runepal.RunepalPlugin;
import com.runepal.WoodcuttingMode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

@Slf4j
public class AgentSkillExecutor {
    private static final String CONFIG_GROUP = "runepal";

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ConfigManager configManager;

    private ActiveSkill activeSkill;
    private String lastMessage = "No template skill has run yet";
    private Instant lastUpdatedAt = Instant.EPOCH;

    public AgentSkillExecutor(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.configManager = Objects.requireNonNull(configManager, "configManager cannot be null");
    }

    public synchronized AgentExecutionResult execute(AgentSkillTemplate template, JsonObject params) {
        Objects.requireNonNull(template, "template cannot be null");
        JsonObject safeParams = params == null ? new JsonObject() : params.deepCopy();

        if (!template.startsBot()) {
            return stopActiveSkill();
        }

        try {
            applyTemplateConfiguration(template, safeParams);

            BotType botType = template.getBotType();
            if (botType == null) {
                return AgentExecutionResult.error("Template does not map to a bot type", new JsonObject());
            }

            boolean started = plugin.startBotForType(botType);
            if (!started) {
                JsonObject payload = new JsonObject();
                payload.addProperty("skill", template.getWireName());
                lastMessage = "Failed to start template " + template.getWireName();
                lastUpdatedAt = Instant.now();
                return AgentExecutionResult.error(lastMessage, payload);
            }

            activeSkill = new ActiveSkill(template, Instant.now(), safeParams);
            lastMessage = "Started template " + template.getWireName();
            lastUpdatedAt = Instant.now();

            JsonObject payload = new JsonObject();
            payload.addProperty("skill", template.getWireName());
            payload.addProperty("startedAt", activeSkill.startedAt.toString());
            payload.add("params", safeParams);
            return AgentExecutionResult.success(lastMessage, payload);
        } catch (Exception e) {
            log.error("Failed to execute template {}", template.getWireName(), e);
            JsonObject payload = new JsonObject();
            payload.addProperty("skill", template.getWireName());
            payload.addProperty("error", e.getMessage());
            lastMessage = "Execution failed for template " + template.getWireName();
            lastUpdatedAt = Instant.now();
            return AgentExecutionResult.error(lastMessage, payload);
        }
    }

    public synchronized AgentExecutionResult stopActiveSkill() {
        plugin.stopBot();
        activeSkill = null;
        lastMessage = "Stopped active template skill";
        lastUpdatedAt = Instant.now();

        JsonObject payload = new JsonObject();
        payload.addProperty("running", false);
        return AgentExecutionResult.success(lastMessage, payload);
    }

    public synchronized void onGameTick() {
        if (!config.startBot() && activeSkill != null) {
            activeSkill = null;
            lastMessage = "Template skill ended outside agent command";
            lastUpdatedAt = Instant.now();
        }
    }

    public synchronized void clearActiveSkill(String message) {
        activeSkill = null;
        if (message != null && !message.trim().isEmpty()) {
            lastMessage = message;
            lastUpdatedAt = Instant.now();
        }
    }

    public synchronized JsonObject getStatusSnapshot() {
        JsonObject status = new JsonObject();
        status.addProperty("running", config.startBot());
        status.addProperty("botType", config.botType().name());
        status.addProperty("lastMessage", lastMessage);
        if (!Instant.EPOCH.equals(lastUpdatedAt)) {
            status.addProperty("lastUpdatedAt", lastUpdatedAt.toString());
        }

        if (activeSkill != null) {
            status.addProperty("activeSkill", activeSkill.template.getWireName());
            status.addProperty("activeSkillStartedAt", activeSkill.startedAt.toString());
            status.add("activeSkillParams", activeSkill.params.deepCopy());
        } else {
            status.addProperty("activeSkill", "");
        }

        return status;
    }

    public JsonArray listSkillDefinitions() {
        JsonArray definitions = new JsonArray();
        for (AgentSkillTemplate template : AgentSkillTemplate.values()) {
            JsonObject definition = new JsonObject();
            definition.addProperty("name", template.getWireName());
            definition.addProperty("description", template.getDescription());
            definition.addProperty("startsBot", template.startsBot());
            if (template.getBotType() != null) {
                definition.addProperty("botType", template.getBotType().name());
            }
            definition.add("parameters", buildParameterHints(template));
            definitions.add(definition);
        }
        return definitions;
    }

    private JsonArray buildParameterHints(AgentSkillTemplate template) {
        JsonArray hints = new JsonArray();
        switch (template) {
            case MINE_POWER:
                hints.add("rockTypes: CSV string, e.g. 'Iron,Coal'");
                break;
            case MINE_BANK:
                hints.add("rockTypes: CSV string, e.g. 'Iron,Coal'");
                hints.add("miningBank: enum name, e.g. 'VARROCK_EAST'");
                break;
            case WOODCUT_POWER:
                hints.add("treeTypes: CSV string, e.g. 'Oak,Willow'");
                break;
            case WOODCUT_BANK:
                hints.add("treeTypes: CSV string, e.g. 'Oak,Willow'");
                hints.add("woodcuttingBank: enum name, e.g. 'VARROCK_EAST'");
                break;
            case FISH_POWER:
            case FISH_BANK:
                hints.add("fishingSpot: enum name, e.g. 'NET' or 'LURE'");
                hints.add("fishingArea: enum name, e.g. 'LUMBRIDGE_SWAMP'");
                hints.add("cookFish: boolean");
                break;
            case COMBAT_BASIC:
                hints.add("combatNpcNames: CSV string, e.g. 'Goblin,Cow'");
                hints.add("combatEatAtHealthPercent: integer 1-99");
                break;
            case STOP_ALL:
                break;
            default:
                break;
        }
        return hints;
    }

    private void applyTemplateConfiguration(AgentSkillTemplate template, JsonObject params) {
        switch (template) {
            case MINE_POWER:
                setConfig("miningMode", MiningMode.POWER_MINE.name());
                setConfig("rockTypes", readCsvParam(params, "rockTypes", config.rockTypes()));
                break;

            case MINE_BANK:
                setConfig("miningMode", MiningMode.BANK.name());
                setConfig("rockTypes", readCsvParam(params, "rockTypes", config.rockTypes()));
                setConfig("miningBank", readEnumParam(params, "miningBank", config.miningBank()));
                break;

            case WOODCUT_POWER:
                setConfig("woodcuttingMode", WoodcuttingMode.POWER_CHOP.name());
                setConfig("treeTypes", readCsvParam(params, "treeTypes", config.treeTypes()));
                break;

            case WOODCUT_BANK:
                setConfig("woodcuttingMode", WoodcuttingMode.BANK.name());
                setConfig("treeTypes", readCsvParam(params, "treeTypes", config.treeTypes()));
                setConfig("woodcuttingBank", readEnumParam(params, "woodcuttingBank", config.woodcuttingBank()));
                break;

            case FISH_POWER:
                setConfig("fishingMode", FishingMode.POWER_DROP.name());
                setConfig("fishingSpot", readEnumParam(params, "fishingSpot", config.fishingSpot().name()));
                setConfig("fishingArea", readEnumParam(params, "fishingArea", config.fishingArea().name()));
                setConfig("cookFish", readBooleanParam(params, "cookFish", config.cookFish()));
                break;

            case FISH_BANK:
                setConfig("fishingMode", FishingMode.BANK.name());
                setConfig("fishingSpot", readEnumParam(params, "fishingSpot", config.fishingSpot().name()));
                setConfig("fishingArea", readEnumParam(params, "fishingArea", config.fishingArea().name()));
                setConfig("cookFish", readBooleanParam(params, "cookFish", config.cookFish()));
                break;

            case COMBAT_BASIC:
                setConfig("combatNpcNames", readCsvParam(params, "combatNpcNames", config.combatNpcNames()));
                setConfig("combatEatAtHealthPercent",
                        readIntParam(params, "combatEatAtHealthPercent", config.combatEatAtHealthPercent(), 1, 99));
                break;

            case STOP_ALL:
                break;

            default:
                throw new IllegalArgumentException("Unsupported template: " + template.getWireName());
        }
    }

    private void setConfig(String keyName, Object value) {
        configManager.setConfiguration(CONFIG_GROUP, keyName, value);
    }

    private String readCsvParam(JsonObject params, String key, String fallback) {
        if (params == null || !params.has(key)) {
            return fallback;
        }

        JsonElement element = params.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        String raw = element.getAsString();
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        StringJoiner joiner = new StringJoiner(",");
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                joiner.add(trimmed);
            }
        }
        String normalized = joiner.toString();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String readEnumParam(JsonObject params, String key, String fallback) {
        if (params == null || !params.has(key)) {
            return fallback;
        }

        JsonElement element = params.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        String raw = element.getAsString();
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }

        return raw.trim()
                .toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private boolean readBooleanParam(JsonObject params, String key, boolean fallback) {
        if (params == null || !params.has(key)) {
            return fallback;
        }

        JsonElement element = params.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readIntParam(JsonObject params, String key, int fallback, int minValue, int maxValue) {
        if (params == null || !params.has(key)) {
            return fallback;
        }

        JsonElement element = params.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            int value = element.getAsInt();
            if (value < minValue) {
                return minValue;
            }
            if (value > maxValue) {
                return maxValue;
            }
            return value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class ActiveSkill {
        private final AgentSkillTemplate template;
        private final Instant startedAt;
        private final JsonObject params;

        private ActiveSkill(AgentSkillTemplate template, Instant startedAt, JsonObject params) {
            this.template = template;
            this.startedAt = startedAt;
            this.params = params;
        }
    }
}
