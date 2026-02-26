package com.runepal.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.BotConfig;
import com.runepal.agent.llm.AgentLoopOutcome;
import com.runepal.agent.llm.AgentToolLoopRunner;
import com.runepal.agent.script.ScriptParser;
import com.runepal.agent.script.ScriptRepository;
import com.runepal.agent.script.ScriptSpec;
import com.runepal.agent.script.ScriptValidationResult;
import com.runepal.agent.script.ScriptValidator;
import com.runepal.agent.trace.AgentTraceService;
import com.runepal.llm.LlmClient;
import com.runepal.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class AgentOrchestrator {
    private static final Pattern KILL_TARGET_PATTERN = Pattern.compile("\\bkill\\b\\s+([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);

    private ScriptSpec pendingScriptSpec;
    private Instant pendingScriptCreatedAt = Instant.EPOCH;
    private final BotConfig config;
    private final LlmClient llmClient;
    private final ScriptParser scriptParser;
    private final ScriptValidator scriptValidator;
    private final ScriptRepository scriptRepository;
    private final AgentGoalStore goalStore;
    private final Consumer<PlannedAction> actionSink;
    private final Consumer<AgentDecisionRecord> decisionSink;
    private final AgentToolLoopRunner toolLoopRunner;
    private final AgentTraceService traceService;
    private final ExecutorService plannerExecutor;
    private final AtomicBoolean planning = new AtomicBoolean(false);

    private volatile AgentDecisionRecord lastDecision = AgentDecisionRecord.builder()
            .decisionType("idle")
            .reason("No decisions yet")
            .build();

    public AgentOrchestrator(BotConfig config,
                             LlmClient llmClient,
                             ScriptParser scriptParser,
                             ScriptValidator scriptValidator,
                             ScriptRepository scriptRepository,
                             AgentGoalStore goalStore,
                             Consumer<PlannedAction> actionSink,
                             Consumer<AgentDecisionRecord> decisionSink,
                             AgentToolLoopRunner toolLoopRunner,
                             AgentTraceService traceService) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient cannot be null");
        this.scriptParser = Objects.requireNonNull(scriptParser, "scriptParser cannot be null");
        this.scriptValidator = Objects.requireNonNull(scriptValidator, "scriptValidator cannot be null");
        this.scriptRepository = Objects.requireNonNull(scriptRepository, "scriptRepository cannot be null");
        this.goalStore = Objects.requireNonNull(goalStore, "goalStore cannot be null");
        this.actionSink = Objects.requireNonNull(actionSink, "actionSink cannot be null");
        this.decisionSink = Objects.requireNonNull(decisionSink, "decisionSink cannot be null");
        this.toolLoopRunner = Objects.requireNonNull(toolLoopRunner, "toolLoopRunner cannot be null");
        this.traceService = Objects.requireNonNull(traceService, "traceService cannot be null");

        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "agent-orchestrator");
            thread.setDaemon(true);
            return thread;
        };
        this.plannerExecutor = Executors.newSingleThreadExecutor(factory);
    }

    public void shutdown() {
        plannerExecutor.shutdownNow();
    }

    public void setGoal(String goal) {
        goalStore.setGoal(goal);

        synchronized (this) {
            pendingScriptSpec = null;
            pendingScriptCreatedAt = Instant.EPOCH;
        }
    }

    public JsonObject getGoalSnapshot() {
        return goalStore.toJson();
    }

    public JsonObject getLastDecisionSnapshot() {
        return lastDecision.toJson();
    }

    public synchronized boolean hasPendingScript() {
        return pendingScriptSpec != null;
    }

    public synchronized JsonObject getPendingScriptSnapshot() {
        JsonObject json = new JsonObject();
        json.addProperty("present", pendingScriptSpec != null);
        if (pendingScriptSpec != null) {
            json.addProperty("name", pendingScriptSpec.getName());
            if (!Instant.EPOCH.equals(pendingScriptCreatedAt)) {
                json.addProperty("createdAt", pendingScriptCreatedAt.toString());
            }
            json.add("script", pendingScriptSpec.toJson());
        }
        return json;
    }

    public synchronized PlannedAction consumePendingScriptAsAction() {
        if (pendingScriptSpec == null) {
            return PlannedAction.none();
        }

        ScriptSpec spec = pendingScriptSpec;
        pendingScriptSpec = null;
        pendingScriptCreatedAt = Instant.EPOCH;
        return PlannedAction.runScript(spec);
    }

    public boolean isPlanning() {
        return planning.get();
    }

    public boolean requestPlan(JsonObject snapshot, JsonArray templateDefinitions) {
        String goal = goalStore.getGoal();
        if (goal == null || goal.trim().isEmpty()) {
            AgentDecisionRecord decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal("")
                    .source("none")
                    .decisionType("idle")
                    .reason("Goal is empty")
                    .queuedExecution(false)
                    .build();
            publishDecision(decision);
            return false;
        }

        if (!planning.compareAndSet(false, true)) {
            return false;
        }

        JsonObject snapshotCopy = snapshot == null ? new JsonObject() : snapshot.deepCopy();
        JsonArray templatesCopy = templateDefinitions == null ? new JsonArray() : templateDefinitions.deepCopy();
        JsonObject tracePayload = new JsonObject();
        tracePayload.addProperty("goal", goal);
        tracePayload.addProperty("templateCount", templatesCopy.size());
        traceService.record("plan_requested", "planning requested", tracePayload);
        plannerExecutor.submit(() -> planInternal(goal, snapshotCopy, templatesCopy, false, "", new JsonObject()));
        return true;
    }

    public boolean requestDebugPlan(String reason,
                                    JsonObject snapshot,
                                    JsonArray templateDefinitions,
                                    JsonObject debugEvidence) {
        String goal = goalStore.getGoal();
        if (goal == null || goal.trim().isEmpty()) {
            goal = "(no goal set)";
        }

        if (!planning.compareAndSet(false, true)) {
            return false;
        }

        JsonObject snapshotCopy = snapshot == null ? new JsonObject() : snapshot.deepCopy();
        JsonArray templatesCopy = templateDefinitions == null ? new JsonArray() : templateDefinitions.deepCopy();
        JsonObject evidenceCopy = debugEvidence == null ? new JsonObject() : debugEvidence.deepCopy();
        String debugReason = reason == null ? "" : reason;

        JsonObject tracePayload = new JsonObject();
        tracePayload.addProperty("goal", goal);
        tracePayload.addProperty("reason", debugReason);
        traceService.record("debug_requested", "debug planning requested", tracePayload);

        String finalGoal = goal;
        plannerExecutor.submit(() -> planInternal(finalGoal, snapshotCopy, templatesCopy, true, debugReason, evidenceCopy));
        return true;
    }

    public void requestPing(Consumer<LlmResult> callback) {
        plannerExecutor.submit(() -> {
            LlmResult result = llmClient.ping();
            callback.accept(result);
        });
    }

    private void planInternal(String goal,
                              JsonObject snapshot,
                              JsonArray templateDefinitions,
                              boolean debugMode,
                              String debugReason,
                              JsonObject debugEvidence) {
        try {
            if (debugMode) {
                planDebugInternal(goal, snapshot, templateDefinitions, debugReason, debugEvidence);
                return;
            }

            PlannedAction action;
            AgentDecisionRecord decision;

            boolean llmEnabled = config.llmEnable();
            boolean hasApiKey = config.llmApiKey() != null && !config.llmApiKey().trim().isEmpty();

            if (!llmEnabled || !hasApiKey) {
                action = heuristicPlan(goal);
                decision = buildDecisionFromAction(goal, "heuristic", action,
                        llmEnabled ? "LLM API key missing, used heuristic fallback" : "LLM disabled, used heuristic fallback");
            } else {
                    AgentLoopOutcome loopOutcome = toolLoopRunner.run(goal, snapshot, Math.max(2, config.agentMaxPlanTurns()));
                if (!loopOutcome.isSuccess()) {
                    action = heuristicPlan(goal);
                    AgentDecisionRecord fallbackDecision = buildDecisionFromAction(goal, "heuristic", action,
                            "LLM tool loop failed, fallback: " + loopOutcome.getError());
                    decision = AgentDecisionRecord.builder()
                            .timestamp(Instant.now())
                            .goal(goal)
                            .source(fallbackDecision.getSource())
                            .decisionType(fallbackDecision.getDecisionType())
                            .reason(fallbackDecision.getReason())
                            .templateName(fallbackDecision.getTemplateName())
                            .scriptName(fallbackDecision.getScriptName())
                            .queuedExecution(fallbackDecision.isQueuedExecution())
                            .error(loopOutcome.getError())
                            .build();
                } else {
                    JsonObject finalPayload = loopOutcome.getFinalPayload();
                    action = parseToolLoopFinalDecision(goal, finalPayload, true);
                    if ("answer".equalsIgnoreCase(readString(finalPayload, "mode", ""))) {
                        String answer = readString(finalPayload, "answer", "");
                        if (finalPayload.has("citations") && finalPayload.get("citations").isJsonArray()) {
                            answer = answer + "\nSources: " + finalPayload.getAsJsonArray("citations").toString();
                        }
                        decision = AgentDecisionRecord.builder()
                                .timestamp(Instant.now())
                                .goal(goal)
                                .source("llm")
                                .decisionType("answer")
                                .reason(answer)
                                .queuedExecution(false)
                                .build();
                    } else {
                        decision = buildDecisionFromAction(goal, "llm", action, "Planned via tool loop response");
                    }
                }
            }

            if (action.getType() == PlannedActionType.RUN_SCRIPT && config.llmRequireScriptApproval()) {
                ScriptSpec pendingSpec = action.getScriptSpec();
                if (pendingSpec != null) {
                    synchronized (this) {
                        pendingScriptSpec = pendingSpec;
                        pendingScriptCreatedAt = Instant.now();
                    }
                    try {
                        scriptRepository.saveScript(pendingSpec);
                    } catch (Exception e) {
                        log.warn("Failed to persist pending script '{}'", pendingSpec.getName(), e);
                    }
                }

                decision = AgentDecisionRecord.builder()
                        .timestamp(Instant.now())
                        .goal(goal)
                        .source(decision.getSource())
                        .decisionType("script")
                        .reason("Script generated but execution paused because require-script-approval is enabled")
                        .scriptName(action.getScriptSpec() == null ? null : action.getScriptSpec().getName())
                        .queuedExecution(false)
                        .build();
                publishDecision(decision);
                return;
            }

            // Any successful non-paused plan clears any pending script.
            if (action.getType() != PlannedActionType.RUN_SCRIPT) {
                synchronized (this) {
                    pendingScriptSpec = null;
                    pendingScriptCreatedAt = Instant.EPOCH;
                }
            }

            if (action.getType() != PlannedActionType.NONE) {
                actionSink.accept(action);
            }

            publishDecision(decision);
        } catch (Exception e) {
            AgentDecisionRecord errorDecision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("error")
                    .decisionType("error")
                    .reason("Planner crashed")
                    .queuedExecution(false)
                    .error(e.getMessage())
                    .build();
            publishDecision(errorDecision);
        } finally {
            planning.set(false);
        }
    }

    private void planDebugInternal(String goal,
                                   JsonObject snapshot,
                                   JsonArray templateDefinitions,
                                   String debugReason,
                                   JsonObject debugEvidence) {
        boolean llmEnabled = config.llmEnable();
        boolean hasApiKey = config.llmApiKey() != null && !config.llmApiKey().trim().isEmpty();

        if (!llmEnabled || !hasApiKey) {
            AgentDecisionRecord decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("debug")
                    .decisionType("debug")
                    .reason(llmEnabled
                            ? "Self-debug skipped: LLM API key missing"
                            : "Self-debug skipped: LLM planning disabled")
                    .queuedExecution(false)
                    .build();
            publishDecision(decision);
            return;
        }

        JsonObject context = debugEvidence == null ? new JsonObject() : debugEvidence.deepCopy();
        context.addProperty("debugReason", debugReason == null ? "" : debugReason);
        context.add("templateDefinitions", templateDefinitions == null ? new JsonArray() : templateDefinitions.deepCopy());

        String debugGoal = "Debug the currently stalled automation for goal: " + goal + ". "
                + "Diagnose likely root cause using available tools and provide a minimal repair. "
                + "Return mode=execute if you can repair now, else mode=answer with diagnosis and next checks.";

        AgentLoopOutcome loopOutcome = toolLoopRunner.runDebug(
                debugGoal,
                snapshot == null ? new JsonObject() : snapshot.deepCopy(),
                context,
                Math.max(2, config.agentMaxPlanTurns()));

        if (!loopOutcome.isSuccess()) {
            AgentDecisionRecord decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("debug")
                    .decisionType("debug")
                    .reason("Self-debug failed: " + loopOutcome.getError())
                    .queuedExecution(false)
                    .error(loopOutcome.getError())
                    .build();
            publishDecision(decision);
            return;
        }

        JsonObject finalPayload = loopOutcome.getFinalPayload();
        String mode = readString(finalPayload, "mode", "");
        if ("answer".equalsIgnoreCase(mode)) {
            AgentDecisionRecord decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("llm_debug")
                    .decisionType("debug")
                    .reason(readString(finalPayload, "answer", "Self-debug returned no answer"))
                    .queuedExecution(false)
                    .build();
            publishDecision(decision);
            return;
        }

        PlannedAction action = parseToolLoopFinalDecision(goal, finalPayload, false);

        if (action.getType() == PlannedActionType.NONE) {
            String diagnosis = readString(finalPayload, "answer",
                    readString(finalPayload, "reason", "Self-debug found no executable repair"));
            AgentDecisionRecord decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("llm_debug")
                    .decisionType("debug")
                    .reason(diagnosis)
                    .queuedExecution(false)
                    .build();
            publishDecision(decision);
            return;
        }

        AgentDecisionRecord decision = buildDecisionFromAction(goal, "llm_debug", action,
                "Self-debug produced a repair plan");

        if (action.getType() == PlannedActionType.RUN_SCRIPT && config.llmRequireScriptApproval()) {
            ScriptSpec pendingSpec = action.getScriptSpec();
            if (pendingSpec != null) {
                synchronized (this) {
                    pendingScriptSpec = pendingSpec;
                    pendingScriptCreatedAt = Instant.now();
                }
                try {
                    scriptRepository.saveScript(pendingSpec);
                } catch (Exception e) {
                    log.warn("Failed to persist pending script '{}'", pendingSpec.getName(), e);
                }
            }

            decision = AgentDecisionRecord.builder()
                    .timestamp(Instant.now())
                    .goal(goal)
                    .source("llm_debug")
                    .decisionType("script")
                    .reason("Self-debug generated a script repair but execution is paused for approval")
                    .scriptName(action.getScriptSpec() == null ? null : action.getScriptSpec().getName())
                    .queuedExecution(false)
                    .build();
            publishDecision(decision);
            return;
        }

        if (action.getType() != PlannedActionType.NONE) {
            actionSink.accept(action);
        }
        publishDecision(decision);
    }

    private PlannedAction parseToolLoopFinalDecision(String goal, JsonObject decision, boolean allowHeuristicFallback) {
        if (decision == null) {
            return PlannedAction.none();
        }

        String mode = readString(decision, "mode", "");
        if ("answer".equalsIgnoreCase(mode)) {
            return PlannedAction.none();
        }

        String decisionType = normalize(readString(decision, "decisionType", ""));
        if ("TEMPLATE".equals(decisionType)) {
            String templateName = readString(decision, "templateName", null);
            Optional<AgentSkillTemplate> template = AgentSkillTemplate.fromWireName(templateName);
            if (!template.isPresent()) {
                return allowHeuristicFallback ? heuristicPlan(goal) : PlannedAction.none();
            }

            JsonObject params = decision.has("templateParams") && decision.get("templateParams").isJsonObject()
                    ? decision.getAsJsonObject("templateParams").deepCopy()
                    : new JsonObject();
            return PlannedAction.runTemplate(template.get(), params);
        }

        if ("SCRIPT".equals(decisionType)) {
            if (!decision.has("script") || !decision.get("script").isJsonObject()) {
                return allowHeuristicFallback ? heuristicPlan(goal) : PlannedAction.none();
            }

            JsonObject scriptJson = decision.getAsJsonObject("script");
            ScriptSpec spec;
            try {
                spec = scriptParser.parse(scriptJson);
            } catch (Exception parseError) {
                return allowHeuristicFallback ? heuristicPlan(goal) : PlannedAction.none();
            }

            ScriptValidationResult validationResult = scriptValidator.validate(spec);
            if (!validationResult.isValid()) {
                return allowHeuristicFallback ? heuristicPlan(goal) : PlannedAction.none();
            }

            try {
                scriptRepository.saveScript(spec);
            } catch (Exception e) {
                log.warn("Generated script could not be saved: {}", spec.getName(), e);
            }
            return PlannedAction.runScript(spec);
        }

        return PlannedAction.none();
    }

    private PlannedAction heuristicPlan(String goal) {
        String normalizedGoal = normalize(goal);

        if (normalizedGoal.contains("MINE") || normalizedGoal.contains("ORE") || normalizedGoal.contains("ROCK")) {
            JsonObject params = new JsonObject();
            if (normalizedGoal.contains("IRON")) {
                params.addProperty("rockTypes", "Iron");
            }
            return PlannedAction.runTemplate(AgentSkillTemplate.MINE_POWER, params);
        }

        if (normalizedGoal.contains("WOOD") || normalizedGoal.contains("TREE") || normalizedGoal.contains("CHOP")) {
            JsonObject params = new JsonObject();
            if (normalizedGoal.contains("OAK")) {
                params.addProperty("treeTypes", "Oak");
            }
            return PlannedAction.runTemplate(AgentSkillTemplate.WOODCUT_POWER, params);
        }

        if (normalizedGoal.contains("FISH") || normalizedGoal.contains("SALMON") || normalizedGoal.contains("TROUT")) {
            return PlannedAction.runTemplate(AgentSkillTemplate.FISH_POWER, new JsonObject());
        }

        if (normalizedGoal.contains("KILL") || normalizedGoal.contains("COMBAT") || normalizedGoal.contains("ATTACK")) {
            String npcTarget = inferNpcTargetFromGoal(goal);
            JsonObject params = new JsonObject();
            params.addProperty("combatNpcNames", npcTarget);
            return PlannedAction.runTemplate(AgentSkillTemplate.COMBAT_BASIC, params);
        }

        return PlannedAction.none();
    }

    private AgentDecisionRecord buildDecisionFromAction(String goal, String source, PlannedAction action, String reason) {
        AgentDecisionRecord.Builder builder = AgentDecisionRecord.builder()
                .timestamp(Instant.now())
                .goal(goal)
                .source(source)
                .reason(reason)
                .queuedExecution(action.getType() != PlannedActionType.NONE);

        switch (action.getType()) {
            case RUN_TEMPLATE:
                builder.decisionType("template");
                if (action.getTemplate() != null) {
                    builder.templateName(action.getTemplate().getWireName());
                }
                break;
            case RUN_SCRIPT:
                builder.decisionType("script");
                if (action.getScriptSpec() != null) {
                    builder.scriptName(action.getScriptSpec().getName());
                }
                break;
            case NONE:
            default:
                builder.decisionType("idle");
                break;
        }

        return builder.build();
    }

    private void publishDecision(AgentDecisionRecord decision) {
        this.lastDecision = decision;
        traceService.record("decision", "decision published", decision.toJson());
        log.info("Agent decision: type={}, source={}, reason={}",
                decision.getDecisionType(),
                decision.getSource(),
                decision.getReason());
        decisionSink.accept(decision);
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

    private String inferNpcTargetFromGoal(String goal) {
        if (goal == null || goal.trim().isEmpty()) {
            return "Goblin";
        }

        String lower = goal.trim().toLowerCase(Locale.US);
        if (lower.contains("cow")) {
            return "Cow";
        }
        if (lower.contains("chicken")) {
            return "Chicken";
        }
        if (lower.contains("goblin")) {
            return "Goblin";
        }
        if (lower.contains("rat")) {
            return "Rat";
        }

        Matcher matcher = KILL_TARGET_PATTERN.matcher(lower);
        if (!matcher.find()) {
            return "Goblin";
        }

        String captured = matcher.group(1);
        if (captured == null) {
            return "Goblin";
        }

        // Stop at common clause boundaries: "until", "for", "with", "at"
        String trimmed = captured.split("\\b(until|for|with|at|in|on)\\b")[0].trim();
        if (trimmed.isEmpty()) {
            return "Goblin";
        }

        // Drop trailing punctuation
        trimmed = trimmed.replaceAll("[\\p{Punct}]+$", "").trim();
        if (trimmed.endsWith("s") && trimmed.length() > 3) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        // Title-case first letter only (CombatTask uses contains-ignorecase)
        char first = Character.toUpperCase(trimmed.charAt(0));
        if (trimmed.length() == 1) {
            return String.valueOf(first);
        }
        return first + trimmed.substring(1);
    }

    public enum PlannedActionType {
        RUN_TEMPLATE,
        RUN_SCRIPT,
        NONE
    }

    public static final class PlannedAction {
        private final PlannedActionType type;
        private final AgentSkillTemplate template;
        private final JsonObject templateParams;
        private final ScriptSpec scriptSpec;

        private PlannedAction(PlannedActionType type, AgentSkillTemplate template, JsonObject templateParams,
                              ScriptSpec scriptSpec) {
            this.type = type;
            this.template = template;
            this.templateParams = templateParams == null ? new JsonObject() : templateParams.deepCopy();
            this.scriptSpec = scriptSpec;
        }

        public static PlannedAction runTemplate(AgentSkillTemplate template, JsonObject params) {
            return new PlannedAction(PlannedActionType.RUN_TEMPLATE, template, params, null);
        }

        public static PlannedAction runScript(ScriptSpec scriptSpec) {
            return new PlannedAction(PlannedActionType.RUN_SCRIPT, null, null, scriptSpec);
        }

        public static PlannedAction none() {
            return new PlannedAction(PlannedActionType.NONE, null, null, null);
        }

        public PlannedActionType getType() {
            return type;
        }

        public AgentSkillTemplate getTemplate() {
            return template;
        }

        public JsonObject getTemplateParams() {
            return templateParams.deepCopy();
        }

        public ScriptSpec getScriptSpec() {
            return scriptSpec;
        }
    }
}
