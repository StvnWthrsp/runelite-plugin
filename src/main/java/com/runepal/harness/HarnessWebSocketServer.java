package com.runepal.harness;

import java.net.InetSocketAddress;
import java.util.Objects;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class HarnessWebSocketServer extends WebSocketServer {
    private final HarnessService harnessService;

    public HarnessWebSocketServer(InetSocketAddress bindAddress, HarnessService harnessService) {
        super(bindAddress);
        this.harnessService = Objects.requireNonNull(harnessService, "harnessService cannot be null");
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        harnessService.onSocketOpen(connection);
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        harnessService.onSocketClose(connection, code, reason, remote);
    }

    @Override
    public void onMessage(WebSocket connection, String message) {
        harnessService.onSocketMessage(connection, message);
    }

    @Override
    public void onError(WebSocket connection, Exception exception) {
        harnessService.onSocketError(connection, exception);
    }

    @Override
    public void onStart() {
        harnessService.onSocketServerStarted();
    }
}
