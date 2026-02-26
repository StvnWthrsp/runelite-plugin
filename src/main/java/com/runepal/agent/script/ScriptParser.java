package com.runepal.agent.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScriptParser {
    public ScriptSpec parse(String jsonText) {
        JsonElement root = JsonParser.parseString(jsonText);
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("Script JSON root must be an object");
        }
        return parse(root.getAsJsonObject());
    }

    public ScriptSpec parse(JsonObject scriptJson) {
        if (scriptJson == null) {
            throw new IllegalArgumentException("Script JSON cannot be null");
        }

        String name = readString(scriptJson, "name", "generated_script");
        String entryState = readString(scriptJson, "entryState", null);
        if (entryState == null || entryState.trim().isEmpty()) {
            throw new IllegalArgumentException("Script requires 'entryState'");
        }

        JsonObject statesObject = readObject(scriptJson, "states");
        if (statesObject == null) {
            throw new IllegalArgumentException("Script requires 'states' object");
        }

        Map<String, List<ScriptStep>> states = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> stateEntry : statesObject.entrySet()) {
            if (!stateEntry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("State '" + stateEntry.getKey() + "' must be an array of steps");
            }

            JsonArray stepsArray = stateEntry.getValue().getAsJsonArray();
            List<ScriptStep> steps = new ArrayList<>();
            for (JsonElement stepElement : stepsArray) {
                if (!stepElement.isJsonObject()) {
                    throw new IllegalArgumentException("Each step must be a JSON object");
                }
                steps.add(parseStep(stepElement.getAsJsonObject()));
            }
            states.put(stateEntry.getKey(), steps);
        }

        return new ScriptSpec(name, entryState, states, scriptJson.deepCopy());
    }

    private ScriptStep parseStep(JsonObject stepJson) {
        ScriptStepType stepType = inferStepType(stepJson);

        switch (stepType) {
            case ACTION:
                String action = readString(stepJson, "action", null);
                JsonObject params = readObject(stepJson, "params");
                return ScriptStep.action(action == null ? "" : action, params);

            case WAIT_UNTIL:
                JsonObject waitConditionObject = readObject(stepJson, "condition");
                if (waitConditionObject == null) {
                    waitConditionObject = readObject(stepJson, "wait_for");
                }
                ScriptCondition waitCondition = parseCondition(waitConditionObject);
                int timeoutTicks = readInt(stepJson, "timeoutTicks", 30);
                return ScriptStep.waitUntil(waitCondition, timeoutTicks);

            case GOTO:
                String targetState = readString(stepJson, "targetState", readString(stepJson, "goto", null));
                return ScriptStep.goTo(targetState);

            case IF:
                JsonElement conditionElement = stepJson.get("condition");
                if (conditionElement == null) {
                    conditionElement = stepJson.get("if");
                }
                ScriptCondition condition = parseCondition(conditionElement);
                String thenState = readString(stepJson, "thenState", readString(stepJson, "then", null));
                String elseState = readString(stepJson, "elseState", readString(stepJson, "else", null));
                return ScriptStep.ifState(condition, thenState, elseState);

            case STOP:
                return ScriptStep.stop();

            default:
                throw new IllegalArgumentException("Unsupported step type: " + stepType);
        }
    }

    private ScriptStepType inferStepType(JsonObject stepJson) {
        String typeValue = readString(stepJson, "type", null);
        if (typeValue != null && !typeValue.trim().isEmpty()) {
            return parseStepType(typeValue);
        }

        if (stepJson.has("action")) {
            return ScriptStepType.ACTION;
        }
        if (stepJson.has("wait_for")) {
            return ScriptStepType.WAIT_UNTIL;
        }
        if (stepJson.has("goto")) {
            return ScriptStepType.GOTO;
        }
        if (stepJson.has("if")) {
            return ScriptStepType.IF;
        }
        if (stepJson.has("stop")) {
            return ScriptStepType.STOP;
        }

        throw new IllegalArgumentException("Could not infer step type");
    }

    private ScriptStepType parseStepType(String value) {
        String normalized = normalize(value);
        switch (normalized) {
            case "ACTION":
                return ScriptStepType.ACTION;
            case "WAIT_UNTIL":
            case "WAIT":
                return ScriptStepType.WAIT_UNTIL;
            case "GOTO":
                return ScriptStepType.GOTO;
            case "IF":
                return ScriptStepType.IF;
            case "STOP":
                return ScriptStepType.STOP;
            default:
                throw new IllegalArgumentException("Unsupported script step type: " + value);
        }
    }

    private ScriptCondition parseCondition(JsonElement conditionElement) {
        if (conditionElement == null || conditionElement.isJsonNull()) {
            throw new IllegalArgumentException("Condition is required");
        }

        if (conditionElement.isJsonPrimitive()) {
            ScriptConditionType type = parseConditionType(conditionElement.getAsString());
            return new ScriptCondition(type, new JsonObject());
        }

        if (!conditionElement.isJsonObject()) {
            throw new IllegalArgumentException("Condition must be string or object");
        }

        JsonObject conditionObject = conditionElement.getAsJsonObject();
        String typeValue = readString(conditionObject, "type", null);
        if (typeValue == null || typeValue.trim().isEmpty()) {
            throw new IllegalArgumentException("Condition object must include 'type'");
        }

        ScriptConditionType type = parseConditionType(typeValue);
        JsonObject params = conditionObject.deepCopy();
        params.remove("type");
        return new ScriptCondition(type, params);
    }

    private ScriptConditionType parseConditionType(String value) {
        String normalized = normalize(value);
        switch (normalized) {
            case "ALWAYS":
                return ScriptConditionType.ALWAYS;
            case "INVENTORY_FULL":
                return ScriptConditionType.INVENTORY_FULL;
            case "INVENTORY_EMPTY":
                return ScriptConditionType.INVENTORY_EMPTY;
            case "PLAYER_IDLE":
                return ScriptConditionType.PLAYER_IDLE;
            case "HAS_ITEM":
                return ScriptConditionType.HAS_ITEM;
            case "IS_DROPPING":
                return ScriptConditionType.IS_DROPPING;
            case "IS_INTERACTING":
                return ScriptConditionType.IS_INTERACTING;
            case "CURRENT_STATE_CONTAINS":
                return ScriptConditionType.CURRENT_STATE_CONTAINS;
            case "RANDOM_CHANCE":
                return ScriptConditionType.RANDOM_CHANCE;
            default:
                throw new IllegalArgumentException("Unsupported condition type: " + value);
        }
    }

    private JsonObject readObject(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
    }
}
