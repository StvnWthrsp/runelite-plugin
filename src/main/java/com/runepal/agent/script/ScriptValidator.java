package com.runepal.agent.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScriptValidator {
    public ScriptValidationResult validate(ScriptSpec scriptSpec) {
        List<String> errors = new ArrayList<>();
        if (scriptSpec == null) {
            errors.add("Script spec cannot be null");
            return ScriptValidationResult.invalid(errors);
        }

        if (isBlank(scriptSpec.getName())) {
            errors.add("Script name is required");
        }

        if (isBlank(scriptSpec.getEntryState())) {
            errors.add("entryState is required");
        }

        Map<String, List<ScriptStep>> states = scriptSpec.getStates();
        if (states == null || states.isEmpty()) {
            errors.add("Script must define at least one state");
            return ScriptValidationResult.invalid(errors);
        }

        if (!states.containsKey(scriptSpec.getEntryState())) {
            errors.add("entryState '" + scriptSpec.getEntryState() + "' is not defined in states");
        }

        for (Map.Entry<String, List<ScriptStep>> stateEntry : states.entrySet()) {
            String stateName = stateEntry.getKey();
            List<ScriptStep> steps = stateEntry.getValue();
            if (steps == null || steps.isEmpty()) {
                errors.add("State '" + stateName + "' must contain at least one step");
                continue;
            }

            for (int index = 0; index < steps.size(); index++) {
                ScriptStep step = steps.get(index);
                validateStep(step, stateName, index, states, errors);
            }
        }

        return errors.isEmpty() ? ScriptValidationResult.valid() : ScriptValidationResult.invalid(errors);
    }

    private void validateStep(ScriptStep step,
                              String stateName,
                              int index,
                              Map<String, List<ScriptStep>> states,
                              List<String> errors) {
        if (step == null) {
            errors.add("State '" + stateName + "' step " + index + " cannot be null");
            return;
        }

        switch (step.getType()) {
            case ACTION:
                validateActionStep(step, stateName, index, errors);
                break;
            case WAIT_UNTIL:
                if (step.getCondition() == null) {
                    errors.add("State '" + stateName + "' step " + index + " WAIT_UNTIL requires condition");
                } else {
                    validateCondition(step.getCondition(), stateName, index, errors);
                }
                if (step.getTimeoutTicks() <= 0) {
                    errors.add("State '" + stateName + "' step " + index + " WAIT_UNTIL timeoutTicks must be > 0");
                }
                break;
            case GOTO:
                if (isBlank(step.getTargetState())) {
                    errors.add("State '" + stateName + "' step " + index + " GOTO requires targetState");
                } else if (!states.containsKey(step.getTargetState())) {
                    errors.add("State '" + stateName + "' step " + index
                            + " GOTO target '" + step.getTargetState() + "' is undefined");
                }
                break;
            case IF:
                if (step.getCondition() == null) {
                    errors.add("State '" + stateName + "' step " + index + " IF requires condition");
                } else {
                    validateCondition(step.getCondition(), stateName, index, errors);
                }
                if (isBlank(step.getThenState()) || !states.containsKey(step.getThenState())) {
                    errors.add("State '" + stateName + "' step " + index + " IF has invalid thenState");
                }
                if (!isBlank(step.getElseState()) && !states.containsKey(step.getElseState())) {
                    errors.add("State '" + stateName + "' step " + index + " IF has undefined elseState");
                }
                break;
            case STOP:
                break;
            default:
                errors.add("State '" + stateName + "' step " + index + " uses unsupported type " + step.getType());
                break;
        }
    }

    private void validateActionStep(ScriptStep step, String stateName, int index, List<String> errors) {
        String action = normalize(step.getAction());
        if (action.isEmpty()) {
            errors.add("State '" + stateName + "' step " + index + " ACTION requires action name");
            return;
        }

        JsonObject params = step.getParams();
        switch (action) {
            case "WALK_TO":
                if (!hasNumeric(params, "worldX") || !hasNumeric(params, "worldY")) {
                    errors.add("State '" + stateName + "' step " + index
                            + " WALK_TO requires numeric worldX and worldY params");
                }
                break;
            case "INTERACT_OBJECT":
                if (!hasIntArray(params, "ids")) {
                    errors.add("State '" + stateName + "' step " + index
                            + " INTERACT_OBJECT requires ids array param");
                }
                break;
            case "INTERACT_NPC":
                if (!hasStringArray(params, "npcNames") && !hasIntArray(params, "npcIds")) {
                    errors.add("State '" + stateName + "' step " + index
                            + " INTERACT_NPC requires npcNames or npcIds param");
                }
                break;
            case "BANK_DEPOSIT_ALL":
                break;
            case "POWER_DROP":
                if (!hasIntArray(params, "itemIds")) {
                    errors.add("State '" + stateName + "' step " + index
                            + " POWER_DROP requires itemIds array param");
                }
                break;
            case "CAST_SPELL":
                if (isBlank(readString(params, "spellName", null))) {
                    errors.add("State '" + stateName + "' step " + index
                            + " CAST_SPELL requires spellName");
                }
                break;
            case "SET_STATUS":
                if (isBlank(readString(params, "status", null))) {
                    errors.add("State '" + stateName + "' step " + index
                            + " SET_STATUS requires status");
                }
                break;
            default:
                errors.add("State '" + stateName + "' step " + index
                        + " has unsupported action '" + step.getAction() + "'");
                break;
        }
    }

    private void validateCondition(ScriptCondition condition, String stateName, int index, List<String> errors) {
        JsonObject params = condition.getParams();
        switch (condition.getType()) {
            case ALWAYS:
            case INVENTORY_FULL:
            case INVENTORY_EMPTY:
            case PLAYER_IDLE:
            case IS_DROPPING:
            case IS_INTERACTING:
                break;
            case HAS_ITEM:
                if (!hasNumeric(params, "itemId")) {
                    errors.add("State '" + stateName + "' step " + index + " HAS_ITEM requires numeric itemId");
                }
                break;
            case CURRENT_STATE_CONTAINS:
                if (isBlank(readString(params, "value", null))) {
                    errors.add("State '" + stateName + "' step " + index
                            + " CURRENT_STATE_CONTAINS requires value");
                }
                break;
            case RANDOM_CHANCE:
                if (!hasNumeric(params, "percent")) {
                    errors.add("State '" + stateName + "' step " + index
                            + " RANDOM_CHANCE requires percent");
                    break;
                }
                int percent = params.get("percent").getAsInt();
                if (percent < 0 || percent > 100) {
                    errors.add("State '" + stateName + "' step " + index
                            + " RANDOM_CHANCE percent must be between 0 and 100");
                }
                break;
            default:
                errors.add("State '" + stateName + "' step " + index
                        + " has unsupported condition " + condition.getType());
                break;
        }
    }

    private boolean hasNumeric(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber();
    }

    private boolean hasIntArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return false;
        }

        JsonArray array = object.getAsJsonArray(key);
        if (array.size() == 0) {
            return false;
        }

        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasStringArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return false;
        }

        JsonArray array = object.getAsJsonArray(key);
        if (array.size() == 0) {
            return false;
        }

        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return false;
            }
        }
        return true;
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
    }
}
