package com.runepal.harness;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runepal.BotConfig;
import com.runepal.RunepalPlugin;
import com.runepal.harness.action.HarnessActionRuntime;
import com.runepal.harness.action.HarnessActionStatusEvent;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import org.java_websocket.WebSocket;

@Slf4j
public class HarnessService {
    private static final int DEFAULT_PORT = 8766;

    private final RunepalPlugin plugin;
    private final BotConfig config;
    private final Gson gson = new Gson();
    private final Queue<QueuedCommand> commandQueue = new ConcurrentLinkedQueue<>();

    private final HarnessWorldIndex worldIndex;
    private final HarnessSnapshotBuilder snapshotBuilder;
    private final HarnessActionRuntime actionRuntime;

    private HarnessWebSocketServer webSocketServer;
    private int boundPort = -1;
    private long tickCounter = 0;
    private String latestSnapshotMessage;

    public HarnessService(RunepalPlugin plugin, BotConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.worldIndex = new HarnessWorldIndex();
        this.snapshotBuilder = new HarnessSnapshotBuilder(plugin, config, worldIndex);
        this.actionRuntime = new HarnessActionRuntime(
                plugin,
                plugin.getRemoteInputService(),
                plugin.getWindmouseService(),
                this::onActionStatusEvent);
    }

    public void onGameTick() {
        refreshLifecycle();
        if (!config.harnessEnable()) {
            return;
        }

        tickCounter++;
        processQueuedCommands();
        actionRuntime.onGameTick(tickCounter);

        int streamEveryTicks = Math.max(1, config.harnessStreamEveryTicks());
        if (tickCounter % streamEveryTicks != 0) {
            return;
        }

        JsonObject snapshotPayload = snapshotBuilder.buildSnapshot(tickCounter);
        latestSnapshotMessage = envelopeToJson("snapshot", snapshotPayload);
        safeBroadcast(latestSnapshotMessage);
    }

    public void refreshLifecycle() {
        synchronizeServerLifecycle();
    }

    public synchronized void shutdown() {
        stopServerInternal();
        commandQueue.clear();
        latestSnapshotMessage = null;
    }

    public void onNpcSpawned(NPC npc) {
        worldIndex.onNpcSpawned(npc, tickCounter, plugin.getClient().getWorld());
    }

    public void onNpcDespawned(NPC npc) {
        worldIndex.onNpcDespawned(npc);
    }

    public void onObjectSpawned(GameObject gameObject) {
        ObjectComposition composition = null;
        try {
            composition = plugin.getClient().getObjectDefinition(gameObject.getId());
        } catch (Exception ignored) {
            // Keep index resilient to bad object definitions.
        }
        worldIndex.onObjectSpawned(gameObject, composition, tickCounter, plugin.getClient().getWorld());
    }

    public void onObjectDespawned(GameObject gameObject) {
        worldIndex.onObjectDespawned(gameObject);
    }

    void onSocketOpen(WebSocket connection) {
        JsonObject payload = new JsonObject();
        payload.addProperty("protocol", "runepal-harness-v1");
        payload.addProperty("localOnly", true);
        payload.addProperty("port", boundPort);
        payload.addProperty("snapshotVersion", 2);
        JsonObject server = new JsonObject();
        server.addProperty("bind", "127.0.0.1");
        server.addProperty("port", boundPort);
        payload.add("server", server);
        JsonObject capabilities = new JsonObject();
        capabilities.addProperty("snapshots", true);
        capabilities.addProperty("actions", true);
        capabilities.addProperty("scripts", false);
        capabilities.addProperty("screenshot", true);
        payload.add("capabilities", capabilities);
        sendMessage(connection, envelopeToJson("hello", payload));

        if (latestSnapshotMessage != null) {
            sendMessage(connection, latestSnapshotMessage);
        }
    }

    void onSocketClose(WebSocket connection, int code, String reason, boolean remote) {
        log.info("Harness client disconnected (code={}, remote={}): {}", code, remote, reason);
    }

    void onSocketServerStarted() {
        log.info("Harness server listening on ws://127.0.0.1:{}", boundPort);
    }

    void onSocketError(WebSocket connection, Exception exception) {
        if (exception == null) {
            return;
        }
        if (connection != null) {
            log.warn("Harness socket error for {}", connection.getRemoteSocketAddress(), exception);
            return;
        }
        log.warn("Harness server error", exception);
    }

    void onSocketMessage(WebSocket connection, String message) {
        if (message == null || message.trim().isEmpty()) {
            sendResponse(connection, null, "", false, "empty_message", "Command payload was empty", new JsonObject());
            return;
        }

        JsonObject request;
        try {
            JsonElement root = JsonParser.parseString(message);
            if (!root.isJsonObject()) {
                sendResponse(connection, null, "", false, "invalid_message", "Payload must be a JSON object", new JsonObject());
                return;
            }
            request = root.getAsJsonObject();
        } catch (Exception e) {
            sendResponse(connection, null, "", false, "invalid_json", "Unable to parse command JSON", new JsonObject());
            return;
        }

        String type = readString(request, "type", "");
        if (!"command".equals(type)) {
            sendResponse(connection, readString(request, "requestId", null), "", false, "invalid_type",
                    "Expected type='command'", new JsonObject());
            return;
        }

        String requestId = readString(request, "requestId", null);
        String command = readString(request, "command", "");
        JsonObject payload = request.has("payload") && request.get("payload").isJsonObject()
                ? request.getAsJsonObject("payload").deepCopy()
                : new JsonObject();

        if (command.trim().isEmpty()) {
            sendResponse(connection, requestId, "", false, "missing_command", "Command is required", new JsonObject());
            return;
        }

        commandQueue.offer(new QueuedCommand(connection, requestId, command, payload));
    }

    private synchronized void synchronizeServerLifecycle() {
        boolean enabled = config.harnessEnable();
        int desiredPort = sanitizePort(config.harnessPort());

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
            JsonObject payload = command.payload == null ? new JsonObject() : command.payload;
            switch (command.command) {
                case "ping": {
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.addProperty("value", "pong");
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                case "get_state": {
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.add("snapshot", snapshotBuilder.buildSnapshot(tickCounter));
                    responsePayload.add("actions", actionRuntime.getStatusSnapshot());
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                case "capture_screenshot": {
                    int maxWidth = readInt(payload, "maxWidth", 640);
                    JsonObject capture = snapshotBuilder.captureScreenshot(maxWidth);
                    if (capture == null) {
                        sendResponse(command.connection, command.requestId, command.command, false,
                                "capture_failed", "Unable to capture screenshot", new JsonObject());
                        break;
                    }
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", capture);
                    break;
                }
                case "actions.enqueue": {
                    int added = actionRuntime.enqueue(payload, tickCounter);
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.addProperty("enqueued", added);
                    responsePayload.add("actions", actionRuntime.getStatusSnapshot());
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                case "actions.clear": {
                    int cleared = actionRuntime.clearQueued();
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.addProperty("cleared", cleared);
                    responsePayload.add("actions", actionRuntime.getStatusSnapshot());
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                case "actions.status": {
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.add("actions", actionRuntime.getStatusSnapshot());
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                case "actions.cancel_active": {
                    boolean cancelled = actionRuntime.cancelActive("Cancelled by command", tickCounter);
                    JsonObject responsePayload = new JsonObject();
                    responsePayload.addProperty("cancelled", cancelled);
                    responsePayload.add("actions", actionRuntime.getStatusSnapshot());
                    sendResponse(command.connection, command.requestId, command.command, true, "", "ok", responsePayload);
                    break;
                }
                default:
                    sendResponse(command.connection, command.requestId, command.command, false,
                            "unsupported_command",
                            "Supported commands: ping,get_state,capture_screenshot,actions.enqueue,actions.clear,actions.status,actions.cancel_active",
                            new JsonObject());
                    break;
            }
        }
    }

    private void onActionStatusEvent(HarnessActionStatusEvent event) {
        if (event == null) {
            return;
        }
        safeBroadcast(envelopeToJson("action_status", event.toJson()));
    }

    private synchronized void startServerInternal(int port) {
        try {
            HarnessWebSocketServer server = new HarnessWebSocketServer(new InetSocketAddress("127.0.0.1", port), this);
            server.setConnectionLostTimeout(30);
            server.start();
            webSocketServer = server;
            boundPort = port;
        } catch (Exception e) {
            log.error("Failed to start harness server on port {}", port, e);
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
            log.warn("Error while stopping harness server", e);
        } finally {
            webSocketServer = null;
            boundPort = -1;
            commandQueue.clear();
        }
    }

    private void sendResponse(
            WebSocket connection,
            String requestId,
            String command,
            boolean ok,
            String code,
            String message,
            JsonObject payload) {
        if (connection == null) {
            return;
        }
        JsonObject response = new JsonObject();
        response.addProperty("type", "response");
        if (requestId != null && !requestId.trim().isEmpty()) {
            response.addProperty("requestId", requestId);
        }
        response.addProperty("command", command == null ? "" : command);
        response.addProperty("ok", ok);
        response.addProperty("code", code == null ? "" : code);
        response.addProperty("message", message == null ? "" : message);
        response.add("payload", payload == null ? new JsonObject() : payload);
        sendMessage(connection, gson.toJson(response));
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
            log.debug("Failed to send harness message", e);
        }
    }

    private void safeBroadcast(String message) {
        HarnessWebSocketServer server = webSocketServer;
        if (server == null || message == null) {
            return;
        }
        try {
            server.broadcast(message);
        } catch (Exception e) {
            log.debug("Failed to broadcast harness message", e);
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
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static final class QueuedCommand {
        private final WebSocket connection;
        private final String requestId;
        private final String command;
        private final JsonObject payload;

        private QueuedCommand(WebSocket connection, String requestId, String command, JsonObject payload) {
            this.connection = connection;
            this.requestId = requestId;
            this.command = command;
            this.payload = payload == null ? new JsonObject() : payload.deepCopy();
        }
    }
}
