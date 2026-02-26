package com.runepal.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runepal.BotConfig;
import com.runepal.agent.script.ScriptParser;
import com.runepal.agent.script.ScriptRepository;
import com.runepal.agent.script.ScriptSpec;
import com.runepal.agent.script.ScriptValidationResult;
import com.runepal.agent.script.ScriptValidator;
import com.runepal.llm.LlmClient;
import com.runepal.llm.LlmMessage;
import com.runepal.llm.LlmRequestOptions;
import com.runepal.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final BotConfig config;
    private final LlmClient llmClient;
    private final ScriptParser scriptParser;
    private final ScriptValidator scriptValidator;
    private final ScriptRepository scriptRepository;
    private final AgentGoalStore goalStore;
    private final Consumer<PlannedAction> actionSink;
    private final Consumer<AgentDecisionRecord> decisionSink;
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
                             Consumer<AgentDecisionRecord> decisionSink) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient cannot be null");
        this.scriptParser = Objects.requireNonNull(scriptParser, "scriptParser cannot be null");
        this.scriptValidator = Objects.requireNonNull(scriptValidator, "scriptValidator cannot be null");
        this.scriptRepository = Objects.requireNonNull(scriptRepository, "scriptRepository cannot be null");
        this.goalStore = Objects.requireNonNull(goalStore, "goalStore cannot be null");
        this.actionSink = Objects.requireNonNull(actionSink, "actionSink cannot be null");
        this.decisionSink = Objects.requireNonNull(decisionSink, "decisionSink cannot be null");

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
    }

    public JsonObject getGoalSnapshot() {
        return goalStore.toJson();
    }

    public JsonObject getLastDecisionSnapshot() {
        return lastDecision.toJson();
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
        plannerExecutor.submit(() -> planInternal(goal, snapshotCopy, templatesCopy));
        return true;
    }

    public void requestPing(Consumer<LlmResult> callback) {
        plannerExecutor.submit(() -> {
            LlmResult result = llmClient.ping();
            callback.accept(result);
        });
    }

    private void planInternal(String goal, JsonObject snapshot, JsonArray templateDefinitions) {
        try {
            PlannedAction action;
            AgentDecisionRecord decision;

            boolean llmEnabled = config.llmEnable();
            boolean hasApiKey = config.llmApiKey() != null && !config.llmApiKey().trim().isEmpty();

            if (!llmEnabled || !hasApiKey) {
                action = heuristicPlan(goal);
                decision = buildDecisionFromAction(goal, "heuristic", action,
                        llmEnabled ? "LLM API key missing, used heuristic fallback" : "LLM disabled, used heuristic fallback");
            } else {
                LlmResult llmResult = callPlannerModel(goal, snapshot, templateDefinitions);
                if (!llmResult.isSuccess()) {
                    action = heuristicPlan(goal);
                    AgentDecisionRecord fallbackDecision = buildDecisionFromAction(goal, "heuristic", action,
                            "LLM request failed, fallback: " + llmResult.getErrorMessage());
                    decision = AgentDecisionRecord.builder()
                            .timestamp(Instant.now())
                            .goal(goal)
                            .source(fallbackDecision.getSource())
                            .decisionType(fallbackDecision.getDecisionType())
                            .reason(fallbackDecision.getReason())
                            .templateName(fallbackDecision.getTemplateName())
                            .scriptName(fallbackDecision.getScriptName())
                            .queuedExecution(fallbackDecision.isQueuedExecution())
                            .error(llmResult.getErrorMessage())
                            .build();
                } else {
                    action = parseModelDecision(goal, llmResult.getContent());
                    decision = buildDecisionFromAction(goal, "llm", action, "Planned via model response");
                }
            }

            if (action.getType() == PlannedActionType.RUN_SCRIPT && config.llmRequireScriptApproval()) {
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

    private LlmResult callPlannerModel(String goal, JsonObject snapshot, JsonArray templateDefinitions) {
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(
                "You are an OSRS automation planner. Return strict JSON only. "
                        + "Prefer decisionType='template' whenever a suitable template exists in the provided templates list. "
                        + "Only choose decisionType='script' when templates cannot accomplish the goal. "
                        + "Output object fields: decisionType ('template'|'script'|'idle'), reason, "
                        + "templateName, templateParams, script. "
                        + "If script is chosen, script must include name, entryState, states. "
                        + "Use templateParams keys exactly as suggested by the template parameter hints."));

        JsonObject context = new JsonObject();
        context.addProperty("goal", goal);
        context.add("snapshot", snapshot);
        context.add("templates", templateDefinitions);

        messages.add(LlmMessage.user(context.toString()));
        return llmClient.chatCompletion(
                messages,
                LlmRequestOptions.builder()
                        .maxTokens(Math.max(128, config.llmMaxTokens()))
                        .temperature(config.llmTemperature())
                        .requireJsonResponse(true)
                        .build());
    }

    private PlannedAction parseModelDecision(String goal, String content) {
        JsonObject decision = extractJsonObject(content);
        if (decision == null) {
            return heuristicPlan(goal);
        }

        String decisionType = normalize(readString(decision, "decisionType", ""));
        if ("TEMPLATE".equals(decisionType)) {
            String templateName = readString(decision, "templateName", null);
            Optional<AgentSkillTemplate> template = AgentSkillTemplate.fromWireName(templateName);
            if (!template.isPresent()) {
                return heuristicPlan(goal);
            }

            JsonObject params = decision.has("templateParams") && decision.get("templateParams").isJsonObject()
                    ? decision.getAsJsonObject("templateParams").deepCopy()
                    : new JsonObject();
            return PlannedAction.runTemplate(template.get(), params);
        }

        if ("SCRIPT".equals(decisionType)) {
            if (!decision.has("script") || !decision.get("script").isJsonObject()) {
                return heuristicPlan(goal);
            }

            JsonObject scriptJson = decision.getAsJsonObject("script");
            ScriptSpec spec;
            try {
                spec = scriptParser.parse(scriptJson);
            } catch (Exception parseError) {
                return heuristicPlan(goal);
            }

            ScriptValidationResult validationResult = scriptValidator.validate(spec);
            if (!validationResult.isValid()) {
                return heuristicPlan(goal);
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

        JsonObject fallbackScript = new JsonObject();
        fallbackScript.addProperty("name", "idle_guard_script");
        fallbackScript.addProperty("entryState", "main");
        JsonObject states = new JsonObject();
        JsonArray main = new JsonArray();

        JsonObject step = new JsonObject();
        step.addProperty("type", "wait_until");
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "always");
        step.add("condition", condition);
        step.addProperty("timeoutTicks", 10);
        main.add(step);

        JsonObject gotoStep = new JsonObject();
        gotoStep.addProperty("type", "goto");
        gotoStep.addProperty("targetState", "main");
        main.add(gotoStep);

        states.add("main", main);
        fallbackScript.add("states", states);

        ScriptSpec spec = scriptParser.parse(fallbackScript);
        return PlannedAction.runScript(spec);
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
        log.info("Agent decision: type={}, source={}, reason={}",
                decision.getDecisionType(),
                decision.getSource(),
                decision.getReason());
        decisionSink.accept(decision);
    }

    private JsonObject extractJsonObject(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement direct = JsonParser.parseString(content);
            if (direct.isJsonObject()) {
                return direct.getAsJsonObject();
            }
        } catch (Exception ignored) {
            // fall through to bracket extraction
        }

        int firstBrace = content.indexOf('{');
        int lastBrace = content.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }

        String candidate = content.substring(firstBrace, lastBrace + 1);
        try {
            JsonElement parsed = JsonParser.parseString(candidate);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
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
