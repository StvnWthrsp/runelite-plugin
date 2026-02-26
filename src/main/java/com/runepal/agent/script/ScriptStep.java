package com.runepal.agent.script;

import com.google.gson.JsonObject;

import java.util.Objects;

public final class ScriptStep {
    private final ScriptStepType type;
    private final String action;
    private final JsonObject params;
    private final ScriptCondition condition;
    private final String targetState;
    private final String thenState;
    private final String elseState;
    private final int timeoutTicks;

    private ScriptStep(ScriptStepType type,
                       String action,
                       JsonObject params,
                       ScriptCondition condition,
                       String targetState,
                       String thenState,
                       String elseState,
                       int timeoutTicks) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.action = action;
        this.params = params == null ? new JsonObject() : params.deepCopy();
        this.condition = condition;
        this.targetState = targetState;
        this.thenState = thenState;
        this.elseState = elseState;
        this.timeoutTicks = timeoutTicks;
    }

    public static ScriptStep action(String action, JsonObject params) {
        return new ScriptStep(ScriptStepType.ACTION, action, params, null, null, null, null, 0);
    }

    public static ScriptStep waitUntil(ScriptCondition condition, int timeoutTicks) {
        return new ScriptStep(ScriptStepType.WAIT_UNTIL, null, null, condition, null, null, null, timeoutTicks);
    }

    public static ScriptStep goTo(String targetState) {
        return new ScriptStep(ScriptStepType.GOTO, null, null, null, targetState, null, null, 0);
    }

    public static ScriptStep ifState(ScriptCondition condition, String thenState, String elseState) {
        return new ScriptStep(ScriptStepType.IF, null, null, condition, null, thenState, elseState, 0);
    }

    public static ScriptStep stop() {
        return new ScriptStep(ScriptStepType.STOP, null, null, null, null, null, null, 0);
    }

    public ScriptStepType getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public JsonObject getParams() {
        return params.deepCopy();
    }

    public ScriptCondition getCondition() {
        return condition;
    }

    public String getTargetState() {
        return targetState;
    }

    public String getThenState() {
        return thenState;
    }

    public String getElseState() {
        return elseState;
    }

    public int getTimeoutTicks() {
        return timeoutTicks;
    }
}
