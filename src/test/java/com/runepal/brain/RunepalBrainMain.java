package com.runepal.brain;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class RunepalBrainMain {
    public static void main(String[] args) throws Exception {
        Arguments parsedArgs = parseArgs(args);
        if (parsedArgs.showHelp) {
            printUsage();
            return;
        }

        HttpClient client = HttpClient.newHttpClient();

        Listener listener = new Listener(parsedArgs.printSnapshots);
        WebSocket webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(parsedArgs.wsUrl), listener)
                .join();

        System.out.println("Connected to " + parsedArgs.wsUrl);

        if (parsedArgs.initialCommandJson != null && !parsedArgs.initialCommandJson.trim().isEmpty()) {
            webSocket.sendText(parsedArgs.initialCommandJson, true).join();
        }

        String pingRequest = "{\"type\":\"command\",\"requestId\":\"ping-" + UUID.randomUUID()
                + "\",\"command\":\"ping\",\"payload\":{}}";
        webSocket.sendText(pingRequest, true).join();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if ("quit".equalsIgnoreCase(trimmed) || "exit".equalsIgnoreCase(trimmed)) {
                    break;
                }
                webSocket.sendText(trimmed, true).join();
            }
        }

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
    }

    private static Arguments parseArgs(String[] args) {
        Arguments parsed = new Arguments();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--ws".equals(arg) && i + 1 < args.length) {
                parsed.wsUrl = args[++i];
                continue;
            }
            if ("--printSnapshots".equals(arg) && i + 1 < args.length) {
                parsed.printSnapshots = Boolean.parseBoolean(args[++i]);
                continue;
            }
            if ("--send".equals(arg) && i + 1 < args.length) {
                parsed.initialCommandJson = args[++i];
                continue;
            }
            if ("--help".equals(arg) || "-h".equals(arg)) {
                parsed.showHelp = true;
                return parsed;
            }
            System.out.println("Ignoring unknown argument: " + arg);
        }
        return parsed;
    }

    private static void printUsage() {
        System.out.println("RunepalBrainMain usage:");
        System.out.println("  --ws <ws-url>                 (default: ws://127.0.0.1:8766)");
        System.out.println("  --printSnapshots <true|false> (default: true)");
        System.out.println("  --send <json-command>         (optional initial JSON command)");
        System.out.println("  --help");
    }

    private static final class Arguments {
        private String wsUrl = "ws://127.0.0.1:8766";
        private boolean printSnapshots = true;
        private String initialCommandJson;
        private boolean showHelp;
    }

    private static final class Listener implements WebSocket.Listener {
        private final boolean printSnapshots;

        private Listener(boolean printSnapshots) {
            this.printSnapshots = printSnapshots;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = data == null ? "" : data.toString();
            if (message.contains("\"type\":\"hello\"")) {
                System.out.println("[hello] " + message);
            } else if (message.contains("\"type\":\"snapshot\"")) {
                if (printSnapshots) {
                    System.out.println("[snapshot] " + message);
                }
            } else {
                System.out.println("[message] " + message);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + (error == null ? "unknown" : error.getMessage()));
        }
    }
}
