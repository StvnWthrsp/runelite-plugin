package com.runepal.agent.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runepal.ActionService;
import com.runepal.BankTask;
import com.runepal.BotTask;
import com.runepal.EventService;
import com.runepal.GameService;
import com.runepal.HumanizerService;
import com.runepal.RunepalPlugin;
import com.runepal.TaskManager;
import com.runepal.WalkTask;
import com.runepal.agent.trace.AgentTraceService;
import com.runepal.shortestpath.pathfinder.PathfinderConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Slf4j
public class ScriptTask implements BotTask {
    private final ScriptSpec scriptSpec;
    private final RunepalPlugin plugin;
    private final TaskManager taskManager;
    private final PathfinderConfig pathfinderConfig;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final HumanizerService humanizerService;
    private final AgentTraceService traceService;
    private final Random random;

    private boolean started;
    private boolean finished;
    private String currentState;
    private int currentStepIndex;
    private int delayTicks;
    private int waitRemainingTicks;
    private String waitingState;
    private int waitingStepIndex = -1;
    private String failureReason;

    public ScriptTask(ScriptSpec scriptSpec,
                      RunepalPlugin plugin,
                      TaskManager taskManager,
                      PathfinderConfig pathfinderConfig,
                      ActionService actionService,
                      GameService gameService,
                      EventService eventService,
                      HumanizerService humanizerService) {
        this(scriptSpec, plugin, taskManager, pathfinderConfig, actionService, gameService, eventService, humanizerService, null);
    }

    public ScriptTask(ScriptSpec scriptSpec,
                      RunepalPlugin plugin,
                      TaskManager taskManager,
                      PathfinderConfig pathfinderConfig,
                      ActionService actionService,
                      GameService gameService,
                      EventService eventService,
                      HumanizerService humanizerService,
                      AgentTraceService traceService) {
        this.scriptSpec = Objects.requireNonNull(scriptSpec, "scriptSpec cannot be null");
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager cannot be null");
        this.pathfinderConfig = Objects.requireNonNull(pathfinderConfig, "pathfinderConfig cannot be null");
        this.actionService = Objects.requireNonNull(actionService, "actionService cannot be null");
        this.gameService = Objects.requireNonNull(gameService, "gameService cannot be null");
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
        this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
        this.traceService = traceService;
        this.random = plugin.getRandom();
    }

    @Override
    public void onStart() {
        this.started = true;
        this.finished = false;
        this.currentState = scriptSpec.getEntryState();
        this.currentStepIndex = 0;
        this.waitingStepIndex = -1;
        this.waitingState = null;
        this.failureReason = null;
        this.delayTicks = 0;
        plugin.setCurrentState("SCRIPT:" + scriptSpec.getName() + ":" + currentState);
        log.info("Starting script task '{}' at state '{}'.", scriptSpec.getName(), currentState);
        trace("script_start", "script started", "state", currentState);
    }

    @Override
    public void onLoop() {
        if (finished) {
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        List<ScriptStep> steps = getCurrentStateSteps();
        if (steps == null || steps.isEmpty()) {
            fail("Current state has no steps: " + currentState);
            return;
        }

        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            fail("Step index out of bounds in state " + currentState + ": " + currentStepIndex);
            return;
        }

        ScriptStep step = steps.get(currentStepIndex);
        plugin.setCurrentState("SCRIPT:" + scriptSpec.getName() + ":" + currentState + "#" + currentStepIndex);
        trace("script_step", "executing step", "stepType", step.getType().name());

        switch (step.getType()) {
            case ACTION:
                if (executeAction(step)) {
                    advanceStep();
                }
                break;
            case WAIT_UNTIL:
                handleWaitUntil(step);
                break;
            case GOTO:
                transitionToState(step.getTargetState());
                break;
            case IF:
                boolean conditionResult = evaluateCondition(step.getCondition());
                if (conditionResult) {
                    transitionToState(step.getThenState());
                } else if (step.getElseState() != null && !step.getElseState().trim().isEmpty()) {
                    transitionToState(step.getElseState());
                } else {
                    advanceStep();
                }
                break;
            case STOP:
                this.finished = true;
                log.info("Script '{}' reached STOP step.", scriptSpec.getName());
                trace("script_stop", "script reached stop", "state", currentState);
                break;
            default:
                fail("Unsupported step type: " + step.getType());
                break;
        }
    }

    @Override
    public void onStop() {
        if (failureReason != null) {
            log.warn("Stopped script '{}' due to failure: {}", scriptSpec.getName(), failureReason);
        } else {
            log.info("Stopped script '{}'", scriptSpec.getName());
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public String getTaskName() {
        return "Script:" + scriptSpec.getName();
    }

    private List<ScriptStep> getCurrentStateSteps() {
        Map<String, List<ScriptStep>> states = scriptSpec.getStates();
        if (states == null) {
            return null;
        }
        return states.get(currentState);
    }

    private boolean executeAction(ScriptStep step) {
        String action = normalize(step.getAction());
        JsonObject params = step.getParams();

        switch (action) {
            case "WALK_TO":
                return executeWalkTo(params);
            case "INTERACT_OBJECT":
                return executeInteractObject(params);
            case "INTERACT_NPC":
                return executeInteractNpc(params);
            case "BANK_DEPOSIT_ALL":
                taskManager.pushTask(new BankTask(plugin, actionService, gameService, eventService));
                delayTicks = humanizerService.getShortDelay();
                return true;
            case "POWER_DROP":
                int[] itemIds = readIntArray(params, "itemIds");
                if (itemIds.length == 0) {
                    fail("POWER_DROP action requires non-empty itemIds array");
                    return false;
                }
                if (!actionService.isDropping()) {
                    actionService.powerDrop(itemIds);
                    delayTicks = 1;
                }
                return true;
            case "CAST_SPELL":
                String spellName = readString(params, "spellName", null);
                if (spellName == null || spellName.trim().isEmpty()) {
                    fail("CAST_SPELL action requires spellName");
                    return false;
                }
                actionService.castSpell(spellName);
                delayTicks = 1;
                return true;
            case "SET_STATUS":
                String status = readString(params, "status", null);
                if (status == null || status.trim().isEmpty()) {
                    fail("SET_STATUS action requires status");
                    return false;
                }
                plugin.setCurrentState(status);
                return true;
            default:
                fail("Unsupported action: " + step.getAction());
                return false;
        }
    }

    private boolean executeWalkTo(JsonObject params) {
        if (!params.has("worldX") || !params.has("worldY")) {
            fail("WALK_TO requires worldX and worldY");
            return false;
        }

        int worldX = params.get("worldX").getAsInt();
        int worldY = params.get("worldY").getAsInt();
        int plane = params.has("plane") ? params.get("plane").getAsInt() : 0;
        WorldPoint destination = new WorldPoint(worldX, worldY, plane);

        taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, destination, actionService, gameService, humanizerService));
        delayTicks = humanizerService.getShortDelay();
        return true;
    }

    private boolean executeInteractObject(JsonObject params) {
        int[] ids = readIntArray(params, "ids");
        if (ids.length == 0) {
            fail("INTERACT_OBJECT requires ids array");
            return false;
        }

        String option = readString(params, "option", readString(params, "action", "Interact"));
        GameObject target = gameService.findNearestGameObject(ids);
        if (target == null) {
            delayTicks = humanizerService.getRandomDelay(1, 3);
            return false;
        }

        if (!actionService.isInteracting()) {
            actionService.interactWithGameObject(target, option);
            delayTicks = 1;
            return true;
        }
        return false;
    }

    private boolean executeInteractNpc(JsonObject params) {
        String option = readString(params, "option", readString(params, "action", "Attack"));
        NPC targetNpc = null;

        int[] npcIds = readIntArray(params, "npcIds");
        if (npcIds.length > 0) {
            for (int npcId : npcIds) {
                targetNpc = gameService.findNearestNpc(npcId);
                if (targetNpc != null) {
                    break;
                }
            }
        }

        if (targetNpc == null) {
            String[] npcNames = readStringArray(params, "npcNames");
            if (npcNames.length > 0) {
                targetNpc = gameService.findNearestNpc(npcNames);
            }
        }

        if (targetNpc == null) {
            delayTicks = humanizerService.getRandomDelay(1, 3);
            return false;
        }

        if (!actionService.isInteracting()) {
            actionService.interactWithNpc(targetNpc, option);
            delayTicks = 1;
            return true;
        }

        return false;
    }

    private void handleWaitUntil(ScriptStep step) {
        if (waitingStepIndex != currentStepIndex || !currentState.equals(waitingState)) {
            waitingStepIndex = currentStepIndex;
            waitingState = currentState;
            waitRemainingTicks = Math.max(1, step.getTimeoutTicks());
        }

        if (evaluateCondition(step.getCondition())) {
            waitingStepIndex = -1;
            waitingState = null;
            waitRemainingTicks = 0;
            advanceStep();
            return;
        }

        waitRemainingTicks--;
        if (waitRemainingTicks <= 0) {
            fail("WAIT_UNTIL timed out in state " + currentState + " at step " + currentStepIndex);
        }
    }

    private boolean evaluateCondition(ScriptCondition condition) {
        if (condition == null) {
            return false;
        }

        JsonObject params = condition.getParams();
        switch (condition.getType()) {
            case ALWAYS:
                return true;
            case INVENTORY_FULL:
                return gameService.isInventoryFull();
            case INVENTORY_EMPTY:
                return gameService.isInventoryEmpty();
            case PLAYER_IDLE:
                return gameService.isPlayerIdle();
            case HAS_ITEM:
                if (!params.has("itemId")) {
                    return false;
                }
                return gameService.hasItem(params.get("itemId").getAsInt());
            case IS_DROPPING:
                return actionService.isDropping();
            case IS_INTERACTING:
                return actionService.isInteracting();
            case CURRENT_STATE_CONTAINS:
                String expected = readString(params, "value", "");
                return plugin.getCurrentState() != null && plugin.getCurrentState().contains(expected);
            case RANDOM_CHANCE:
                int percent = params.has("percent") ? params.get("percent").getAsInt() : 0;
                percent = Math.max(0, Math.min(100, percent));
                return random.nextInt(100) < percent;
            default:
                return false;
        }
    }

    private void transitionToState(String nextState) {
        if (nextState == null || nextState.trim().isEmpty()) {
            fail("Transition target state was empty");
            return;
        }

        if (!scriptSpec.getStates().containsKey(nextState)) {
            fail("Transition target state does not exist: " + nextState);
            return;
        }

        this.currentState = nextState;
        this.currentStepIndex = 0;
        this.waitingStepIndex = -1;
        this.waitingState = null;
        trace("script_transition", "state transition", "state", nextState);
    }

    private void advanceStep() {
        currentStepIndex++;
        waitingStepIndex = -1;
        waitingState = null;
    }

    private void fail(String reason) {
        this.finished = true;
        this.failureReason = reason;
        plugin.setCurrentState("SCRIPT_FAILED:" + scriptSpec.getName());
        log.warn("Script '{}' failed: {}", scriptSpec.getName(), reason);
        trace("script_failure", reason, "state", currentState);
    }

    private void trace(String category, String message, String key, String value) {
        if (traceService == null) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("script", scriptSpec.getName());
        payload.addProperty("currentState", currentState == null ? "" : currentState);
        payload.addProperty("stepIndex", currentStepIndex);
        if (key != null) {
            payload.addProperty(key, value == null ? "" : value);
        }
        traceService.record(category, message, payload);
    }

    private int[] readIntArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return new int[0];
        }

        JsonArray array = object.getAsJsonArray(key);
        List<Integer> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                values.add(element.getAsInt());
            }
        }

        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private String[] readStringArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return new String[0];
        }

        JsonArray array = object.getAsJsonArray(key);
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }

        return values.toArray(new String[0]);
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.US).replace('-', '_').replace(' ', '_');
    }
}
