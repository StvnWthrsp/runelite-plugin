package com.runepal.agent;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Objects;

public class AgentWebSocketServer extends WebSocketServer {
    private final AgentService agentService;

    public AgentWebSocketServer(InetSocketAddress bindAddress, AgentService agentService) {
        super(bindAddress);
        this.agentService = Objects.requireNonNull(agentService, "agentService cannot be null");
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        agentService.onSocketOpen(connection);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        agentService.onSocketClose(connection, code, reason, remote);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        agentService.onSocketMessage(connection, message);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        agentService.onSocketError(connection, exception);
    }

    @Override
    public void onStart() {
        agentService.onSocketServerStarted();
    }
}
