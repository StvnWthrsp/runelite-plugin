package com.runepal.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import com.runepal.agent.script.ScriptMetadata;
import com.runepal.agent.script.ScriptParser;
import com.runepal.agent.script.ScriptRepository;
import com.runepal.agent.script.ScriptSpec;
import com.runepal.agent.script.ScriptValidationResult;
import com.runepal.agent.script.ScriptValidator;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class AgentScriptExecutor {
    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final ScriptParser parser;
    private final ScriptValidator validator;
    private final ScriptRepository repository;

    private String activeScriptName;
    private Instant activeScriptStartedAt;
    private String lastMessage = "No scripts have been run";
    private Instant lastUpdatedAt = Instant.EPOCH;

    public AgentScriptExecutor(RunepalPlugin plugin,
                               BotConfig config,
                               ScriptParser parser,
                               ScriptValidator validator,
                               ScriptRepository repository) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.parser = Objects.requireNonNull(parser, "parser cannot be null");
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    public synchronized AgentExecutionResult runScriptSpec(ScriptSpec scriptSpec, boolean saveFirst) {
        ScriptValidationResult validationResult = validator.validate(scriptSpec);
        if (!validationResult.isValid()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("script", scriptSpec.getName());
            payload.add("errors", toJsonArray(validationResult.getErrors()));
            updateLastMessage("Script validation failed for " + scriptSpec.getName());
            return AgentExecutionResult.error(lastMessage, payload);
        }

        if (saveFirst) {
            try {
                repository.saveScript(scriptSpec);
            } catch (Exception e) {
                log.warn("Failed to save script '{}' before run", scriptSpec.getName(), e);
            }
        }

        boolean started = plugin.startScriptSpec(scriptSpec);
        if (!started) {
            JsonObject payload = new JsonObject();
            payload.addProperty("script", scriptSpec.getName());
            updateLastMessage("Failed to start script " + scriptSpec.getName());
            return AgentExecutionResult.error(lastMessage, payload);
        }

        this.activeScriptName = scriptSpec.getName();
        this.activeScriptStartedAt = Instant.now();
        updateLastMessage("Started script " + scriptSpec.getName());

        JsonObject payload = new JsonObject();
        payload.addProperty("script", scriptSpec.getName());
        payload.addProperty("startedAt", activeScriptStartedAt.toString());
        return AgentExecutionResult.success(lastMessage, payload);
    }

    public synchronized AgentExecutionResult runScriptByName(String scriptName) {
        Optional<ScriptSpec> loaded = repository.loadScript(scriptName);
        if (!loaded.isPresent()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("script", scriptName);
            updateLastMessage("Script not found: " + scriptName);
            return AgentExecutionResult.error(lastMessage, payload);
        }

        return runScriptSpec(loaded.get(), false);
    }

    public synchronized AgentExecutionResult saveScript(JsonObject scriptJson) {
        ScriptSpec scriptSpec;
        try {
            scriptSpec = parser.parse(scriptJson);
        } catch (Exception e) {
            JsonObject payload = new JsonObject();
            payload.addProperty("error", e.getMessage());
            updateLastMessage("Invalid script JSON");
            return AgentExecutionResult.error(lastMessage, payload);
        }

        ScriptValidationResult validationResult = validator.validate(scriptSpec);
        if (!validationResult.isValid()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("script", scriptSpec.getName());
            payload.add("errors", toJsonArray(validationResult.getErrors()));
            updateLastMessage("Script validation failed for " + scriptSpec.getName());
            return AgentExecutionResult.error(lastMessage, payload);
        }

        try {
            repository.saveScript(scriptSpec);
        } catch (Exception e) {
            JsonObject payload = new JsonObject();
            payload.addProperty("script", scriptSpec.getName());
            payload.addProperty("error", e.getMessage());
            updateLastMessage("Failed to save script " + scriptSpec.getName());
            return AgentExecutionResult.error(lastMessage, payload);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("script", scriptSpec.getName());
        updateLastMessage("Saved script " + scriptSpec.getName());
        return AgentExecutionResult.success(lastMessage, payload);
    }

    public synchronized AgentExecutionResult parseAndRunScript(JsonObject scriptJson, boolean saveFirst) {
        ScriptSpec scriptSpec;
        try {
            scriptSpec = parser.parse(scriptJson);
        } catch (Exception e) {
            JsonObject payload = new JsonObject();
            payload.addProperty("error", e.getMessage());
            updateLastMessage("Invalid script JSON");
            return AgentExecutionResult.error(lastMessage, payload);
        }

        return runScriptSpec(scriptSpec, saveFirst);
    }

    public synchronized JsonArray listScripts() {
        JsonArray array = new JsonArray();
        List<ScriptMetadata> scripts = repository.listScripts();
        for (ScriptMetadata metadata : scripts) {
            JsonObject script = new JsonObject();
            script.addProperty("name", metadata.getName());
            script.addProperty("fileName", metadata.getFileName());
            script.addProperty("updatedAt", metadata.getUpdatedAt().toString());
            array.add(script);
        }
        return array;
    }

    public synchronized JsonObject getStatusSnapshot() {
        JsonObject status = new JsonObject();
        status.addProperty("running", config.startBot() && activeScriptName != null);
        status.addProperty("activeScript", activeScriptName == null ? "" : activeScriptName);
        if (activeScriptStartedAt != null) {
            status.addProperty("activeScriptStartedAt", activeScriptStartedAt.toString());
        }
        status.addProperty("lastMessage", lastMessage);
        if (!Instant.EPOCH.equals(lastUpdatedAt)) {
            status.addProperty("lastUpdatedAt", lastUpdatedAt.toString());
        }
        return status;
    }

    public synchronized void onGameTick() {
        if (!config.startBot() && activeScriptName != null) {
            activeScriptName = null;
            activeScriptStartedAt = null;
            updateLastMessage("Script execution ended");
        }
    }

    public synchronized void onBotStopped() {
        activeScriptName = null;
        activeScriptStartedAt = null;
        updateLastMessage("Script stopped");
    }

    private JsonArray toJsonArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private void updateLastMessage(String message) {
        this.lastMessage = message;
        this.lastUpdatedAt = Instant.now();
    }
}
