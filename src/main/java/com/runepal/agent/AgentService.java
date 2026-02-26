package com.runepal.agent;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import com.runepal.agent.llm.AgentToolLoopRunner;
import com.runepal.agent.memory.AgentMemoryEntry;
import com.runepal.agent.memory.AgentMemoryStore;
import com.runepal.agent.script.ScriptParser;
import com.runepal.agent.script.ScriptRepository;
import com.runepal.agent.script.ScriptValidator;
import com.runepal.agent.tools.AgentToolContext;
import com.runepal.agent.tools.AgentToolDispatcher;
import com.runepal.agent.tools.AgentToolRegistry;
import com.runepal.agent.tools.AgentToolResult;
import com.runepal.agent.tools.BotRunScriptTool;
import com.runepal.agent.tools.BotRunTemplateTool;
import com.runepal.agent.tools.BotStopTool;
import com.runepal.agent.tools.GameSnapshotTool;
import com.runepal.agent.tools.MemoryAddTool;
import com.runepal.agent.tools.MemorySearchTool;
import com.runepal.agent.tools.TraceGetRecentTool;
import com.runepal.agent.tools.VisionCaptureTool;
import com.runepal.agent.tools.WikiFetchTool;
import com.runepal.agent.tools.WikiSearchTool;
import com.runepal.agent.trace.AgentTraceService;
import com.runepal.agent.wiki.OsrsWikiClient;
import com.runepal.llm.LlmClient;
import com.runepal.llm.LlmResult;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import org.java_websocket.WebSocket;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class AgentService {
    private static final int DEFAULT_PORT = 8765;

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final AgentSkillExecutor skillExecutor;
    private final AgentScriptExecutor scriptExecutor;
    private final AgentSnapshotBuilder snapshotBuilder;
    private final AgentGoalStore goalStore;
    private final AgentOrchestrator orchestrator;
    private final AgentTraceService traceService;
    private final AgentMemoryStore memoryStore;
    private final AgentToolRegistry toolRegistry;
    private final AgentToolDispatcher toolDispatcher;

    private final Queue<QueuedCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final Gson gson = new Gson();

    private AgentWebSocketServer webSocketServer;
    private int boundPort = -1;
    private long tickCounter = 0;
    private volatile String latestSnapshotMessage;
    private volatile JsonObject latestSnapshotPayload;
    private volatile String latestAnswer = "";
    private volatile JsonObject latestToolResult = new JsonObject();

    private long lastProgressTick = 0;
    private long lastDebugAttemptTick = Long.MIN_VALUE / 4;
    private int lastProgressFingerprint = 0;
    private boolean hasProgressFingerprint = false;
    private boolean debugInFlight = false;
    private String lastDebugReason = "";

    public AgentService(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(configManager, "configManager cannot be null");

        this.skillExecutor = new AgentSkillExecutor(plugin, config, configManager);

        ScriptParser scriptParser = new ScriptParser();
        ScriptValidator scriptValidator = new ScriptValidator();
        ScriptRepository scriptRepository = new ScriptRepository(scriptParser);

        this.scriptExecutor = new AgentScriptExecutor(plugin, config, scriptParser, scriptValidator, scriptRepository);
        this.goalStore = new AgentGoalStore();
        this.traceService = new AgentTraceService(300);
        this.memoryStore = new AgentMemoryStore();

        OsrsWikiClient wikiClient = new OsrsWikiClient();

        LlmClient llmClient = new LlmClient(config);
        this.snapshotBuilder = new AgentSnapshotBuilder(plugin, config, skillExecutor);

        this.toolRegistry = new AgentToolRegistry();
        AgentToolContext toolContext = new AgentToolContext(
                wikiClient,
                memoryStore,
                traceService,
                snapshotBuilder,
                () -> latestSnapshotPayload == null ? snapshotBuilder.buildSnapshot(tickCounter) : latestSnapshotPayload.deepCopy(),
                this);
        this.toolDispatcher = new AgentToolDispatcher(toolRegistry, toolContext, traceService);
        registerTools();

        AgentToolLoopRunner toolLoopRunner = new AgentToolLoopRunner(llmClient, toolDispatcher, toolRegistry, traceService);
        this.orchestrator = new AgentOrchestrator(
                config,
                llmClient,
                scriptParser,
                scriptValidator,
                scriptRepository,
                goalStore,
                this::queuePlannedAction,
                this::publishDecision,
                toolLoopRunner,
                traceService);
    }

    public void onGameTick() {
        synchronizeServerLifecycle();

        if (!config.agentEnable()) {
            return;
        }

        skillExecutor.onGameTick();
        scriptExecutor.onGameTick();
        processQueuedCommands();

        tickCounter++;

        monitorStallAndTriggerDebug();

        if (shouldAutoPlan()) {
            requestPlanInternal();
        }

        int streamEveryTicks = Math.max(1, config.agentStreamEveryTicks());
        if (tickCounter % streamEveryTicks != 0) {
            return;
        }

        JsonObject snapshotPayload = snapshotBuilder.buildSnapshot(tickCounter);
        snapshotPayload.add("goal", goalStore.toJson());
        snapshotPayload.add("decision", orchestrator.getLastDecisionSnapshot());
        snapshotPayload.add("script", scriptExecutor.getStatusSnapshot());
        snapshotPayload.add("pendingScript", orchestrator.getPendingScriptSnapshot());
        snapshotPayload.add("trace", traceService.toSnapshot(20));
        snapshotPayload.addProperty("answer", latestAnswer == null ? "" : latestAnswer);
        snapshotPayload.addProperty("latestMemoryTitle", getLatestMemoryTitle());
        snapshotPayload.addProperty("debugInFlight", debugInFlight);
        snapshotPayload.addProperty("lastDebugReason", lastDebugReason == null ? "" : lastDebugReason);
        snapshotPayload.addProperty("planning", orchestrator.isPlanning());

        latestSnapshotPayload = snapshotPayload.deepCopy();
        String snapshotMessage = envelopeToJson("snapshot", snapshotPayload);
        latestSnapshotMessage = snapshotMessage;
        safeBroadcast(snapshotMessage);
    }

    public synchronized void shutdown() {
        orchestrator.shutdown();
        stopServerInternal();
        commandQueue.clear();
        latestSnapshotMessage = null;
        latestSnapshotPayload = null;
        debugInFlight = false;
        hasProgressFingerprint = false;
        lastDebugReason = "";
    }

    // UI-friendly API (no WebSocket client required)
    public void setGoalFromUi(String goal) {
        orchestrator.setGoal(goal);
        if (config.agentAutoPlanOnGoal()) {
            requestPlanInternal();
        }
    }

    public boolean planNowFromUi() {
        return requestPlanInternal();
    }

    public boolean triggerDebugFromUi() {
        if (!config.startBot()) {
            return false;
        }

        JsonObject snapshot = latestSnapshotPayload == null
                ? snapshotBuilder.buildSnapshot(tickCounter)
                : latestSnapshotPayload.deepCopy();
        String reason = "Manual self-debug request from Agent UI";
        JsonObject debugEvidence = buildDebugEvidence(reason, Math.max(0, tickCounter - lastProgressTick), snapshot);

        boolean started = orchestrator.requestDebugPlan(
                reason,
                snapshot,
                skillExecutor.listSkillDefinitions(),
                debugEvidence);
        if (!started) {
            return false;
        }

        lastDebugAttemptTick = tickCounter;
        debugInFlight = true;
        lastDebugReason = reason;
        traceService.record("debug_requested", "manual self-debug triggered", debugEvidence);
        return true;
    }

    public void stopAllFromUi() {
        commandQueue.offer(QueuedCommand.stop(null, null, "stop_all_ui"));
    }

    public JsonObject getGoalSnapshot() {
        return goalStore.toJson();
    }

    public JsonObject getDecisionSnapshot() {
        return orchestrator.getLastDecisionSnapshot();
    }

    public JsonObject getPendingScriptSnapshot() {
        return orchestrator.getPendingScriptSnapshot();
    }

    public boolean approvePendingScriptFromUi() {
        AgentOrchestrator.PlannedAction action = orchestrator.consumePendingScriptAsAction();
        if (action.getType() != AgentOrchestrator.PlannedActionType.RUN_SCRIPT) {
            return false;
        }
        queuePlannedAction(action);
        return true;
    }

    public JsonObject getSkillStatusSnapshot() {
        return skillExecutor.getStatusSnapshot();
    }

    public JsonObject getScriptStatusSnapshot() {
        return scriptExecutor.getStatusSnapshot();
    }

    public JsonObject getTraceSnapshot(int limit) {
        return traceService.toSnapshot(limit);
    }

    public AgentTraceService getTraceService() {
        return traceService;
    }

    public String getLatestAnswer() {
        return latestAnswer == null ? "" : latestAnswer;
    }

    public JsonObject getLatestToolResult() {
        return latestToolResult == null ? new JsonObject() : latestToolResult.deepCopy();
    }

    public JsonObject runToolFromUi(String toolName, JsonObject arguments) {
        AgentToolResult result = toolDispatcher.dispatch(toolName, arguments == null ? new JsonObject() : arguments, "ui");
        latestToolResult = result.toJson();
        JsonObject response = new JsonObject();
        response.addProperty("tool", toolName);
        response.add("result", result.toJson());
        return response;
    }

    public void queueRunScriptByName(String scriptName) {
        if (scriptName == null || scriptName.trim().isEmpty()) {
            return;
        }
        commandQueue.offer(QueuedCommand.runScriptName(null, null, "run_script_tool", scriptName.trim()));
    }

    public void queueRunScriptJson(JsonObject scriptJson, boolean saveBeforeRun) {
        if (scriptJson == null || scriptJson.size() == 0) {
            return;
        }
        commandQueue.offer(QueuedCommand.runScriptJson(null, null, "run_script_tool", scriptJson, saveBeforeRun));
    }

    public String getLatestMemoryTitle() {
        AgentMemoryEntry entry = memoryStore.getMostRecent();
        return entry == null ? "" : entry.getTitle();
    }

    public String getLastDebugReason() {
        return lastDebugReason == null ? "" : lastDebugReason;
    }

    public boolean isPlanning() {
        return orchestrator.isPlanning();
    }

    public void queueRunSkill(AgentSkillTemplate template, JsonObject params) {
        if (template == null) {
            return;
        }
        JsonObject safeParams = params == null ? new JsonObject() : params.deepCopy();
        commandQueue.offer(QueuedCommand.runTemplate(null, null, "run_skill", template, safeParams));
    }

    public void queueStopSkill() {
        commandQueue.offer(QueuedCommand.stop(null, null, "stop_skill"));
    }

    void onSocketOpen(WebSocket connection) {
        log.info("Agent client connected: {}", connection.getRemoteSocketAddress());

        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", "runepal-agent-v2");
        payload.addProperty("localOnly", true);
        payload.addProperty("port", boundPort);
        payload.add("skills", skillExecutor.listSkillDefinitions());
        payload.add("goal", goalStore.toJson());

        sendMessage(connection, envelopeToJson("hello", payload));

        String snapshot = latestSnapshotMessage;
        if (snapshot != null) {
            sendMessage(connection, snapshot);
        }

        sendMessage(connection, envelopeToJson("skill_status", skillExecutor.getStatusSnapshot()));
        sendMessage(connection, envelopeToJson("script_status", scriptExecutor.getStatusSnapshot()));
        sendMessage(connection, envelopeToJson("decision", orchestrator.getLastDecisionSnapshot()));
    }

    void onSocketClose(WebSocket connection, int code, String reason, boolean remote) {
        log.info("Agent client disconnected (code={}, remote={}): {}", code, remote, reason);
    }

    void onSocketServerStarted() {
        log.info("Agent WebSocket server listening on ws://127.0.0.1:{}", boundPort);
    }

    void onSocketError(WebSocket connection, Exception exception) {
        if (exception == null) {
            return;
        }
        if (connection != null) {
            log.warn("Agent socket error for client {}", connection.getRemoteSocketAddress(), exception);
            return;
        }
        log.warn("Agent WebSocket server error", exception);
    }

    void onSocketMessage(WebSocket connection, String message) {
        if (message == null || message.trim().isEmpty()) {
            sendError(connection, null, "empty_message", "Command payload was empty");
            return;
        }

        JsonObject request;
        try {
            JsonElement root = JsonParser.parseString(message);
            if (!root.isJsonObject()) {
                sendError(connection, null, "invalid_message", "Command payload must be a JSON object");
                return;
            }
            request = root.getAsJsonObject();
        } catch (Exception e) {
            sendError(connection, null, "invalid_json", "Unable to parse JSON command");
            return;
        }

        String requestType = readString(request, "type", "");
        String requestId = readString(request, "requestId", null);

        switch (requestType) {
            case "ping":
                sendResponse(connection, requestType, requestId, true, "pong", new JsonObject());
                break;

            case "state_request":
                sendLatestSnapshot(connection, requestId);
                break;

            case "list_skills":
                JsonObject skillsPayload = new JsonObject();
                skillsPayload.add("skills", skillExecutor.listSkillDefinitions());
                sendResponse(connection, requestType, requestId, true, "Available template skills", skillsPayload);
                break;

            case "run_skill":
                enqueueRunSkill(connection, requestId, request);
                break;

            case "stop_skill":
                commandQueue.offer(QueuedCommand.stop(connection, requestId, requestType));
                sendQueuedAck(connection, requestType, requestId);
                break;

            case "set_goal":
                handleSetGoal(connection, requestType, requestId, request);
                break;

            case "get_goal":
                JsonObject goalPayload = new JsonObject();
                goalPayload.add("goal", goalStore.toJson());
                sendResponse(connection, requestType, requestId, true, "Goal snapshot", goalPayload);
                break;

            case "plan_now":
                handlePlanNow(connection, requestType, requestId);
                break;

            case "list_scripts":
                JsonObject scriptsPayload = new JsonObject();
                scriptsPayload.add("scripts", scriptExecutor.listScripts());
                sendResponse(connection, requestType, requestId, true, "Available scripts", scriptsPayload);
                break;

            case "save_script":
                handleSaveScript(connection, requestType, requestId, request);
                break;

            case "run_script":
                enqueueRunScript(connection, requestType, requestId, request);
                break;

            case "stop_script":
                commandQueue.offer(QueuedCommand.stop(connection, requestId, requestType));
                sendQueuedAck(connection, requestType, requestId);
                break;

            case "llm_ping":
                orchestrator.requestPing(result -> handleLlmPingResult(connection, requestType, requestId, result));
                sendQueuedAck(connection, requestType, requestId);
                break;

            default:
                sendError(connection, requestId, "unsupported_type",
                        "Unknown command type. Supported: ping,state_request,list_skills,run_skill,stop_skill,set_goal,get_goal,plan_now,list_scripts,save_script,run_script,stop_script,llm_ping");
                break;
        }
    }

    private synchronized void synchronizeServerLifecycle() {
        boolean enabled = config.agentEnable();
        int desiredPort = sanitizePort(config.agentPort());

        if (!enabled) {
            if (webSocketServer != null) {
                stopServerInternal();
            }
            return;
        }

        if (webSocketServer == null) {
            startServerInternal(desiredPort);
            return;
        }

        if (boundPort != desiredPort) {
            stopServerInternal();
            startServerInternal(desiredPort);
        }
    }

    private void processQueuedCommands() {
        QueuedCommand command;
        while ((command = commandQueue.poll()) != null) {
            log.info("Processing queued agent command: {}", command.requestType);

            boolean loggedIn = plugin.getClient().getGameState() == GameState.LOGGED_IN;
            if (!loggedIn && (command.type == QueuedCommandType.RUN_TEMPLATE
                    || command.type == QueuedCommandType.RUN_SCRIPT_NAME
                    || command.type == QueuedCommandType.RUN_SCRIPT_JSON)) {
                sendResponse(command.connection, command.requestType, command.requestId,
                        false, "Cannot execute automation while not logged in", new JsonObject());
                continue;
            }

            switch (command.type) {
                case STOP_ALL:
                    plugin.stopBot();
                    skillExecutor.clearActiveSkill("Stopped active template skill");
                    scriptExecutor.onBotStopped();
                    sendResponse(command.connection, command.requestType, command.requestId, true,
                            "Stopped running automation", buildRunningPayload(false));
                    break;

                case RUN_TEMPLATE:
                    AgentExecutionResult templateResult = skillExecutor.execute(command.template, command.params);
                    if (templateResult.isSuccess()) {
                        scriptExecutor.onBotStopped();
                    }
                    sendResponse(command.connection, command.requestType, command.requestId,
                            templateResult.isSuccess(), templateResult.getMessage(), templateResult.getPayload());
                    break;

                case RUN_SCRIPT_NAME:
                    AgentExecutionResult runByNameResult = scriptExecutor.runScriptByName(command.scriptName);
                    if (runByNameResult.isSuccess()) {
                        skillExecutor.clearActiveSkill("Script run replaced active template skill");
                    }
                    sendResponse(command.connection, command.requestType, command.requestId,
                            runByNameResult.isSuccess(), runByNameResult.getMessage(), runByNameResult.getPayload());
                    break;

                case RUN_SCRIPT_JSON:
                    AgentExecutionResult runByJsonResult = scriptExecutor.parseAndRunScript(command.scriptJson,
                            command.saveScriptFirst);
                    if (runByJsonResult.isSuccess()) {
                        skillExecutor.clearActiveSkill("Script run replaced active template skill");
                    }
                    sendResponse(command.connection, command.requestType, command.requestId,
                            runByJsonResult.isSuccess(), runByJsonResult.getMessage(), runByJsonResult.getPayload());
                    break;
                default:
                    break;
            }

            safeBroadcast(envelopeToJson("skill_status", skillExecutor.getStatusSnapshot()));
            safeBroadcast(envelopeToJson("script_status", scriptExecutor.getStatusSnapshot()));
        }
    }

    private void handleSetGoal(WebSocket connection, String requestType, String requestId, JsonObject request) {
        String goal = readString(request, "goal", "");
        orchestrator.setGoal(goal);
        log.info("Agent goal updated: {}", goal);

        JsonObject payload = new JsonObject();
        payload.add("goal", goalStore.toJson());

        boolean planningStarted = false;
        if (config.agentAutoPlanOnGoal()) {
            planningStarted = requestPlanInternal();
        }
        payload.addProperty("planningStarted", planningStarted);

        sendResponse(connection, requestType, requestId, true, "Goal updated", payload);
    }

    private void handlePlanNow(WebSocket connection, String requestType, String requestId) {
        log.info("Agent plan requested via plan_now command");
        boolean started = requestPlanInternal();
        JsonObject payload = new JsonObject();
        payload.addProperty("planningStarted", started);
        if (!started && orchestrator.isPlanning()) {
            payload.addProperty("reason", "Planner is already running");
        }
        sendResponse(connection, requestType, requestId, true, "Plan request processed", payload);
    }

    private boolean requestPlanInternal() {
        JsonObject snapshot = latestSnapshotPayload == null
                ? snapshotBuilder.buildSnapshot(tickCounter)
                : latestSnapshotPayload.deepCopy();
        JsonArray templates = skillExecutor.listSkillDefinitions();
        JsonObject tracePayload = new JsonObject();
        tracePayload.addProperty("goal", goalStore.getGoal());
        tracePayload.addProperty("source", "request_plan");
        traceService.record("plan_requested", "plan requested", tracePayload);
        boolean started = orchestrator.requestPlan(snapshot, templates);
        if (started) {
            log.info("Agent planning started");
        }
        return started;
    }

    private void handleSaveScript(WebSocket connection, String requestType, String requestId, JsonObject request) {
        if (!request.has("script") || !request.get("script").isJsonObject()) {
            sendError(connection, requestId, "missing_script", "save_script requires script object");
            return;
        }

        AgentExecutionResult saveResult = scriptExecutor.saveScript(request.getAsJsonObject("script").deepCopy());
        sendResponse(connection, requestType, requestId, saveResult.isSuccess(),
                saveResult.getMessage(), saveResult.getPayload());
        safeBroadcast(envelopeToJson("script_status", scriptExecutor.getStatusSnapshot()));
    }

    private void enqueueRunScript(WebSocket connection, String requestType, String requestId, JsonObject request) {
        if (request.has("name")) {
            String scriptName = readString(request, "name", "");
            if (scriptName.trim().isEmpty()) {
                sendError(connection, requestId, "invalid_name", "run_script name must be non-empty");
                return;
            }
            commandQueue.offer(QueuedCommand.runScriptName(connection, requestId, requestType, scriptName));
            sendQueuedAck(connection, requestType, requestId);
            return;
        }

        if (request.has("script") && request.get("script").isJsonObject()) {
            JsonObject scriptJson = request.getAsJsonObject("script").deepCopy();
            boolean saveBeforeRun = !request.has("saveBeforeRun") || readBoolean(request, "saveBeforeRun", true);
            commandQueue.offer(QueuedCommand.runScriptJson(connection, requestId, requestType, scriptJson, saveBeforeRun));
            sendQueuedAck(connection, requestType, requestId);
            return;
        }

        sendError(connection, requestId, "missing_script", "run_script requires either name or script object");
    }

    private void enqueueRunSkill(WebSocket connection, String requestId, JsonObject request) {
        String skillName = readString(request, "skill", null);
        if (skillName == null || skillName.trim().isEmpty()) {
            sendError(connection, requestId, "missing_skill", "run_skill requires a 'skill' field");
            return;
        }

        AgentSkillTemplate template = AgentSkillTemplate.fromWireName(skillName).orElse(null);
        if (template == null) {
            sendError(connection, requestId, "unknown_skill", "Unknown template skill: " + skillName);
            return;
        }

        JsonObject params = new JsonObject();
        if (request.has("params") && request.get("params").isJsonObject()) {
            params = request.getAsJsonObject("params").deepCopy();
        }

        commandQueue.offer(QueuedCommand.runTemplate(connection, requestId, "run_skill", template, params));
        sendQueuedAck(connection, "run_skill", requestId);
    }

    private synchronized void startServerInternal(int port) {
        try {
            AgentWebSocketServer server = new AgentWebSocketServer(new InetSocketAddress("127.0.0.1", port), this);
            server.setConnectionLostTimeout(30);
            server.start();
            webSocketServer = server;
            boundPort = port;
        } catch (Exception e) {
            log.error("Failed to start agent WebSocket server on port {}", port, e);
            webSocketServer = null;
            boundPort = -1;
        }
    }

    private synchronized void stopServerInternal() {
        if (webSocketServer == null) {
            return;
        }

        try {
            webSocketServer.stop(1000);
        } catch (Exception e) {
            log.warn("Error while stopping agent WebSocket server", e);
        } finally {
            webSocketServer = null;
            boundPort = -1;
            commandQueue.clear();
        }
    }

    private void sendLatestSnapshot(WebSocket connection, String requestId) {
        String snapshotMessage = latestSnapshotMessage;
        if (snapshotMessage == null) {
            sendError(connection, requestId, "snapshot_unavailable", "No snapshot is available yet");
            return;
        }

        sendMessage(connection, snapshotMessage);
    }

    private void handleLlmPingResult(WebSocket connection, String requestType, String requestId, LlmResult result) {
        JsonObject payload = new JsonObject();
        payload.addProperty("statusCode", result.getStatusCode());
        if (result.isSuccess()) {
            payload.addProperty("content", result.getContent());
            sendResponse(connection, requestType, requestId, true, "LLM ping succeeded", payload);
            return;
        }

        payload.addProperty("error", result.getErrorMessage());
        sendResponse(connection, requestType, requestId, false, "LLM ping failed", payload);
    }

    private void queuePlannedAction(AgentOrchestrator.PlannedAction action) {
        if (action == null) {
            return;
        }

        switch (action.getType()) {
            case RUN_TEMPLATE:
                log.info("Queued planned template execution: {}", action.getTemplate() == null ? "unknown" : action.getTemplate().getWireName());
                commandQueue.offer(QueuedCommand.runTemplate(
                        null,
                        null,
                        "plan_execute",
                        action.getTemplate(),
                        action.getTemplateParams()));
                break;
            case RUN_SCRIPT:
                if (action.getScriptSpec() == null) {
                    return;
                }
                log.info("Queued planned script execution: {}", action.getScriptSpec().getName());
                commandQueue.offer(QueuedCommand.runScriptJson(
                        null,
                        null,
                        "plan_execute",
                        action.getScriptSpec().toJson(),
                        true));
                break;
            case NONE:
            default:
                break;
        }
    }

    private void publishDecision(AgentDecisionRecord decision) {
        if (decision == null) {
            return;
        }

        if ("answer".equalsIgnoreCase(decision.getDecisionType())
                || "debug".equalsIgnoreCase(decision.getDecisionType())
                || "debug_answer".equalsIgnoreCase(decision.getDecisionType())) {
            latestAnswer = decision.getReason();
        } else {
            latestAnswer = "";
        }

        if (decision.getSource() != null && decision.getSource().toLowerCase().contains("debug")) {
            lastDebugReason = decision.getReason();
        }

        if (debugInFlight) {
            debugInFlight = false;
            JsonObject evidence = new JsonObject();
            evidence.addProperty("source", decision.getSource());
            evidence.addProperty("decisionType", decision.getDecisionType());
            evidence.addProperty("queuedExecution", decision.isQueuedExecution());
            if (decision.getTemplateName() != null) {
                evidence.addProperty("template", decision.getTemplateName());
            }
            if (decision.getScriptName() != null) {
                evidence.addProperty("script", decision.getScriptName());
            }
            appendMemoryEntry(
                    "Self-debug result",
                    buildTags("debug", "result"),
                    decision.getReason(),
                    evidence);
        }

        safeBroadcast(envelopeToJson("decision", decision.toJson()));
    }

    private void registerTools() {
        toolRegistry.register(new WikiSearchTool());
        toolRegistry.register(new WikiFetchTool());
        toolRegistry.register(new GameSnapshotTool());
        toolRegistry.register(new VisionCaptureTool());
        toolRegistry.register(new TraceGetRecentTool());
        toolRegistry.register(new MemorySearchTool());
        toolRegistry.register(new MemoryAddTool());
        toolRegistry.register(new BotRunTemplateTool());
        toolRegistry.register(new BotRunScriptTool());
        toolRegistry.register(new BotStopTool());
    }

    public JsonObject runWikiSmokeTest() {
        JsonObject searchArgs = new JsonObject();
        searchArgs.addProperty("query", "Legends' Quest");
        searchArgs.addProperty("limit", 1);
        AgentToolResult searchResult = toolDispatcher.dispatch("wiki.search", searchArgs, "ui-wiki-search");

        JsonObject result = new JsonObject();
        result.add("search", searchResult.toJson());
        if (!searchResult.isOk()) {
            return result;
        }

        String title = "Legends' Quest";
        try {
            JsonArray items = searchResult.getPayload().getAsJsonArray("items");
            if (items.size() > 0 && items.get(0).isJsonObject() && items.get(0).getAsJsonObject().has("title")) {
                title = items.get(0).getAsJsonObject().get("title").getAsString();
            }
        } catch (Exception ignored) {
            // fallback title remains
        }

        JsonObject fetchArgs = new JsonObject();
        fetchArgs.addProperty("title", title);
        fetchArgs.addProperty("format", "wikitext");
        AgentToolResult fetchResult = toolDispatcher.dispatch("wiki.fetch", fetchArgs, "ui-wiki-fetch");
        result.add("fetch", fetchResult.toJson());
        latestToolResult = result.deepCopy();
        return result;
    }

    private boolean shouldAutoPlan() {
        int interval = config.agentPlanEveryTicks();
        if (interval <= 0) {
            return false;
        }
        if (tickCounter % interval != 0) {
            return false;
        }
        if (config.startBot()) {
            return false;
        }
        if (orchestrator.isPlanning()) {
            return false;
        }

        if (debugInFlight) {
            return false;
        }

        if (orchestrator.hasPendingScript()) {
            return false;
        }

        if (plugin.getClient().getGameState() != GameState.LOGGED_IN) {
            return false;
        }
        String goal = goalStore.getGoal();
        return goal != null && !goal.trim().isEmpty();
    }

    private void monitorStallAndTriggerDebug() {
        if (!config.agentEnableSelfDebug()) {
            return;
        }

        if (!config.startBot()) {
            hasProgressFingerprint = false;
            return;
        }

        if (plugin.getClient().getGameState() != GameState.LOGGED_IN) {
            return;
        }

        if (orchestrator.isPlanning() || debugInFlight || orchestrator.hasPendingScript()) {
            return;
        }

        if (tickCounter % 2 != 0) {
            return;
        }

        int fingerprint = computeProgressFingerprint();
        if (!hasProgressFingerprint) {
            hasProgressFingerprint = true;
            lastProgressFingerprint = fingerprint;
            lastProgressTick = tickCounter;
            return;
        }

        if (fingerprint != lastProgressFingerprint) {
            lastProgressFingerprint = fingerprint;
            lastProgressTick = tickCounter;
            return;
        }

        int stallThresholdTicks = Math.max(20, config.agentStallTicks());
        long stallTicks = tickCounter - lastProgressTick;
        if (stallTicks < stallThresholdTicks) {
            return;
        }

        int cooldownTicks = Math.max(stallThresholdTicks, config.agentDebugCooldownTicks());
        if ((tickCounter - lastDebugAttemptTick) < cooldownTicks) {
            return;
        }

        JsonObject snapshot = latestSnapshotPayload == null
                ? snapshotBuilder.buildSnapshot(tickCounter)
                : latestSnapshotPayload.deepCopy();
        String reason = "No measurable progress for " + stallTicks + " ticks while automation is running";
        JsonObject debugEvidence = buildDebugEvidence(reason, stallTicks, snapshot);

        boolean started = orchestrator.requestDebugPlan(
                reason,
                snapshot,
                skillExecutor.listSkillDefinitions(),
                debugEvidence);

        if (!started) {
            traceService.record("debug_skipped", "self-debug request skipped because planner is busy");
            return;
        }

        lastDebugAttemptTick = tickCounter;
        debugInFlight = true;
        lastDebugReason = reason;
        traceService.record("debug_requested", "self-debug triggered", debugEvidence);

        JsonObject memoryEvidence = new JsonObject();
        memoryEvidence.addProperty("reason", reason);
        memoryEvidence.addProperty("stallTicks", stallTicks);
        memoryEvidence.addProperty("goal", goalStore.getGoal());
        appendMemoryEntry(
                "Stall detected",
                buildTags("debug", "stall", "auto"),
                reason,
                memoryEvidence);
    }

    private int computeProgressFingerprint() {
        StringBuilder builder = new StringBuilder(256);
        builder.append(config.startBot()).append('|');
        builder.append(plugin.getCurrentState() == null ? "" : plugin.getCurrentState()).append('|');

        Player localPlayer = plugin.getClient().getLocalPlayer();
        if (localPlayer != null) {
            WorldPoint worldPoint = localPlayer.getWorldLocation();
            if (worldPoint != null) {
                builder.append(worldPoint.getX()).append(',')
                        .append(worldPoint.getY()).append(',')
                        .append(worldPoint.getPlane());
            }
            builder.append('|').append(localPlayer.getAnimation());
            if (localPlayer.getInteracting() != null) {
                builder.append('|').append(localPlayer.getInteracting().getClass().getSimpleName())
                        .append(':').append(localPlayer.getInteracting().hashCode());
            }
        }

        builder.append('|').append(computeTotalXp());
        builder.append('|').append(computeInventoryHash());
        return builder.toString().hashCode();
    }

    private long computeTotalXp() {
        long totalXp = 0L;
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) {
                continue;
            }
            totalXp += plugin.getClient().getSkillExperience(skill);
        }
        return totalXp;
    }

    private int computeInventoryHash() {
        ItemContainer inventory = plugin.getClient().getItemContainer(InventoryID.INV);
        if (inventory == null) {
            return 0;
        }

        int hash = 1;
        Item[] items = inventory.getItems();
        if (items == null) {
            return hash;
        }

        for (Item item : items) {
            int itemId = item == null ? -1 : item.getId();
            int quantity = item == null ? 0 : item.getQuantity();
            hash = 31 * hash + itemId;
            hash = 31 * hash + quantity;
        }
        return hash;
    }

    private JsonObject buildDebugEvidence(String reason, long stallTicks, JsonObject snapshot) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("reason", reason);
        evidence.addProperty("stallTicks", stallTicks);
        evidence.addProperty("goal", goalStore.getGoal());
        evidence.add("snapshot", snapshot == null ? new JsonObject() : snapshot.deepCopy());
        evidence.add("recentTrace", traceService.getRecentJson(60));

        if (config.agentDebugIncludeScreenshot()) {
            JsonObject capture = snapshotBuilder.captureScreenshot(640);
            if (capture != null) {
                evidence.add("screenshot", capture);
            }
        }

        return evidence;
    }

    private void appendMemoryEntry(String title, List<String> tags, String content, JsonObject evidence) {
        try {
            memoryStore.append(new AgentMemoryEntry(
                    Instant.now(),
                    title,
                    tags == null ? new ArrayList<>() : tags,
                    content,
                    evidence));
        } catch (Exception e) {
            log.warn("Failed to append memory entry '{}'", title, e);
        }
    }

    private List<String> buildTags(String... tags) {
        List<String> values = new ArrayList<>();
        if (tags == null) {
            return values;
        }
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private JsonObject buildRunningPayload(boolean running) {
        JsonObject payload = new JsonObject();
        payload.addProperty("running", running);
        return payload;
    }

    private void sendQueuedAck(WebSocket connection, String requestType, String requestId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("queued", true);
        sendResponse(connection, requestType, requestId, true, "Command queued", payload);
    }

    private void sendResponse(WebSocket connection, String requestType, String requestId, boolean ok, String message,
                              JsonObject payload) {
        if (connection == null) {
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("type", "response");
        response.addProperty("requestType", requestType);
        if (requestId != null && !requestId.trim().isEmpty()) {
            response.addProperty("requestId", requestId);
        }
        response.addProperty("ok", ok);
        response.addProperty("message", message);
        response.add("payload", payload == null ? new JsonObject() : payload);
        sendMessage(connection, gson.toJson(response));
    }

    private void sendError(WebSocket connection, String requestId, String code, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", code);
        sendResponse(connection, "error", requestId, false, message, payload);
    }

    private void sendMessage(WebSocket connection, String message) {
        if (connection == null || message == null) {
            return;
        }

        try {
            if (connection.isOpen()) {
                connection.send(message);
            }
        } catch (Exception e) {
            log.debug("Failed to send WebSocket message", e);
        }
    }

    private void safeBroadcast(String message) {
        AgentWebSocketServer server = webSocketServer;
        if (server == null || message == null) {
            return;
        }

        try {
            server.broadcast(message);
        } catch (Exception e) {
            log.debug("Failed to broadcast WebSocket message", e);
        }
    }

    private String envelopeToJson(String type, JsonObject payload) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", type);
        envelope.add("payload", payload == null ? new JsonObject() : payload);
        return gson.toJson(envelope);
    }

    private int sanitizePort(int configuredPort) {
        if (configuredPort < 1 || configuredPort > 65535) {
            return DEFAULT_PORT;
        }
        return configuredPort;
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

    private boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key)) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        try {
            return element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private enum QueuedCommandType {
        RUN_TEMPLATE,
        RUN_SCRIPT_NAME,
        RUN_SCRIPT_JSON,
        STOP_ALL
    }

    private static final class QueuedCommand {
        private final QueuedCommandType type;
        private final WebSocket connection;
        private final String requestId;
        private final String requestType;

        private final AgentSkillTemplate template;
        private final JsonObject params;

        private final String scriptName;
        private final JsonObject scriptJson;
        private final boolean saveScriptFirst;

        private QueuedCommand(QueuedCommandType type,
                              WebSocket connection,
                              String requestId,
                              String requestType,
                              AgentSkillTemplate template,
                              JsonObject params,
                              String scriptName,
                              JsonObject scriptJson,
                              boolean saveScriptFirst) {
            this.type = type;
            this.connection = connection;
            this.requestId = requestId;
            this.requestType = requestType;
            this.template = template;
            this.params = params == null ? new JsonObject() : params.deepCopy();
            this.scriptName = scriptName;
            this.scriptJson = scriptJson == null ? null : scriptJson.deepCopy();
            this.saveScriptFirst = saveScriptFirst;
        }

        private static QueuedCommand runTemplate(WebSocket connection,
                                                 String requestId,
                                                 String requestType,
                                                 AgentSkillTemplate template,
                                                 JsonObject params) {
            return new QueuedCommand(QueuedCommandType.RUN_TEMPLATE,
                    connection,
                    requestId,
                    requestType,
                    template,
                    params,
                    null,
                    null,
                    false);
        }

        private static QueuedCommand runScriptName(WebSocket connection,
                                                   String requestId,
                                                   String requestType,
                                                   String scriptName) {
            return new QueuedCommand(QueuedCommandType.RUN_SCRIPT_NAME,
                    connection,
                    requestId,
                    requestType,
                    null,
                    null,
                    scriptName,
                    null,
                    false);
        }

        private static QueuedCommand runScriptJson(WebSocket connection,
                                                   String requestId,
                                                   String requestType,
                                                   JsonObject scriptJson,
                                                   boolean saveScriptFirst) {
            return new QueuedCommand(QueuedCommandType.RUN_SCRIPT_JSON,
                    connection,
                    requestId,
                    requestType,
                    null,
                    null,
                    null,
                    scriptJson,
                    saveScriptFirst);
        }

        private static QueuedCommand stop(WebSocket connection, String requestId, String requestType) {
            return new QueuedCommand(QueuedCommandType.STOP_ALL,
                    connection,
                    requestId,
                    requestType,
                    null,
                    null,
                    null,
                    null,
                    false);
        }
    }
}
