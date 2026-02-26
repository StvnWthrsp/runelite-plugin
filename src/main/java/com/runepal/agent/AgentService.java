package com.runepal.agent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import org.java_websocket.WebSocket;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class AgentService {
    private static final int DEFAULT_PORT = 8765;

    private final BotConfig config;
    private final AgentSkillExecutor skillExecutor;
    private final AgentSnapshotBuilder snapshotBuilder;
    private final Queue<QueuedCommand> commandQueue = new ConcurrentLinkedQueue<>();
    private final Gson gson = new Gson();

    private AgentWebSocketServer webSocketServer;
    private int boundPort = -1;
    private long tickCounter = 0;
    private volatile String latestSnapshotMessage;

    public AgentService(RunepalPlugin plugin, BotConfig config, ConfigManager configManager) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(configManager, "configManager cannot be null");
        this.skillExecutor = new AgentSkillExecutor(plugin, config, configManager);
        this.snapshotBuilder = new AgentSnapshotBuilder(plugin, config, skillExecutor);
    }

    public void onGameTick() {
        synchronizeServerLifecycle();

        AgentWebSocketServer server = webSocketServer;
        if (server == null) {
            return;
        }

        skillExecutor.onGameTick();
        processQueuedCommands();

        tickCounter++;
        int streamEveryTicks = Math.max(1, config.agentStreamEveryTicks());
        if (tickCounter % streamEveryTicks != 0) {
            return;
        }

        JsonObject snapshotPayload = snapshotBuilder.buildSnapshot(tickCounter);
        String snapshotMessage = envelopeToJson("snapshot", snapshotPayload);
        latestSnapshotMessage = snapshotMessage;
        safeBroadcast(snapshotMessage);
    }

    public synchronized void shutdown() {
        stopServerInternal();
        commandQueue.clear();
        latestSnapshotMessage = null;
    }

    public void queueRunSkill(AgentSkillTemplate template, JsonObject params) {
        if (template == null) {
            return;
        }
        JsonObject safeParams = params == null ? new JsonObject() : params.deepCopy();
        commandQueue.offer(QueuedCommand.run(null, null, template, safeParams));
    }

    public void queueStopSkill() {
        commandQueue.offer(QueuedCommand.stop(null, null));
    }

    void onSocketOpen(WebSocket connection) {
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", "runepal-agent-v1");
        payload.addProperty("localOnly", true);
        payload.addProperty("port", boundPort);
        payload.add("skills", skillExecutor.listSkillDefinitions());

        sendMessage(connection, envelopeToJson("hello", payload));

        String snapshot = latestSnapshotMessage;
        if (snapshot != null) {
            sendMessage(connection, snapshot);
        }

        sendMessage(connection, envelopeToJson("skill_status", skillExecutor.getStatusSnapshot()));
    }

    void onSocketClose(WebSocket connection, int code, String reason, boolean remote) {
        log.debug("Agent socket closed (code={}, remote={}): {}", code, remote, reason);
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
                JsonObject payload = new JsonObject();
                payload.add("skills", skillExecutor.listSkillDefinitions());
                sendResponse(connection, requestType, requestId, true, "Available template skills", payload);
                break;

            case "run_skill":
                enqueueRunSkill(connection, requestId, request);
                break;

            case "stop_skill":
                commandQueue.offer(QueuedCommand.stop(connection, requestId));
                JsonObject stopPayload = new JsonObject();
                stopPayload.addProperty("queued", true);
                sendResponse(connection, requestType, requestId, true, "Stop command queued", stopPayload);
                break;

            default:
                sendError(connection, requestId, "unsupported_type",
                        "Unknown command type. Supported: ping,state_request,list_skills,run_skill,stop_skill");
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
            AgentExecutionResult result;
            if (command.type == QueuedCommandType.STOP) {
                result = skillExecutor.stopActiveSkill();
                sendResponse(command.connection, "stop_skill", command.requestId, result.isSuccess(),
                        result.getMessage(), result.getPayload());
                safeBroadcast(envelopeToJson("skill_status", skillExecutor.getStatusSnapshot()));
                continue;
            }

            result = skillExecutor.execute(command.template, command.params);
            sendResponse(command.connection, "run_skill", command.requestId, result.isSuccess(),
                    result.getMessage(), result.getPayload());
            safeBroadcast(envelopeToJson("skill_status", skillExecutor.getStatusSnapshot()));
        }
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

        commandQueue.offer(QueuedCommand.run(connection, requestId, template, params));

        JsonObject payload = new JsonObject();
        payload.addProperty("queued", true);
        payload.addProperty("skill", template.getWireName());
        sendResponse(connection, "run_skill", requestId, true, "Run command queued", payload);
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

    private void sendResponse(WebSocket connection, String requestType, String requestId, boolean ok, String message,
                              JsonObject payload) {
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

    private enum QueuedCommandType {
        RUN,
        STOP
    }

    private static final class QueuedCommand {
        private final QueuedCommandType type;
        private final WebSocket connection;
        private final String requestId;
        private final AgentSkillTemplate template;
        private final JsonObject params;

        private QueuedCommand(QueuedCommandType type, WebSocket connection, String requestId,
                              AgentSkillTemplate template, JsonObject params) {
            this.type = type;
            this.connection = connection;
            this.requestId = requestId;
            this.template = template;
            this.params = params;
        }

        private static QueuedCommand run(WebSocket connection, String requestId,
                                         AgentSkillTemplate template, JsonObject params) {
            return new QueuedCommand(QueuedCommandType.RUN, connection, requestId, template, params);
        }

        private static QueuedCommand stop(WebSocket connection, String requestId) {
            return new QueuedCommand(QueuedCommandType.STOP, connection, requestId, null, new JsonObject());
        }
    }
}
