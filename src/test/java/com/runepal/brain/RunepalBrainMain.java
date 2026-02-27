package com.runepal.brain;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
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
        WebSocket webSocket = connectWithRetry(
                client,
                URI.create(parsedArgs.wsUrl),
                listener,
                parsedArgs.retryAttempts,
                Duration.ofMillis(parsedArgs.retryDelayMs));

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

    private static WebSocket connectWithRetry(
            HttpClient client,
            URI uri,
            Listener listener,
            int retryAttempts,
            Duration retryDelay) {
        int safeAttempts = Math.max(1, retryAttempts);
        long safeDelayMs = Math.max(100, retryDelay.toMillis());

        CompletionException lastConnectException = null;
        for (int attempt = 1; attempt <= safeAttempts; attempt++) {
            try {
                return client.newWebSocketBuilder().buildAsync(uri, listener).join();
            } catch (CompletionException e) {
                lastConnectException = e;
                if (!isConnectionRefused(e) || attempt == safeAttempts) {
                    break;
                }

                System.out.println("Harness not reachable yet at " + uri + " (attempt " + attempt + "/"
                        + safeAttempts + "). Retrying in " + safeDelayMs + "ms...");

                try {
                    Thread.sleep(safeDelayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        System.err.println("Unable to connect to harness at " + uri + ".");
        System.err.println("Check these before retrying:");
        System.err.println("1) RuneLite is running with the Runepal plugin enabled.");
        System.err.println("2) External Brain Harness is enabled in plugin config.");
        System.err.println("3) Port matches (harnessPort), default is 8766.");
        System.err.println("4) Harness log line appears: 'Harness server listening on ws://127.0.0.1:<port>'.");

        if (lastConnectException != null) {
            throw lastConnectException;
        }
        throw new IllegalStateException("Unable to connect to harness");
    }

    private static boolean isConnectionRefused(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
            if ("--retryAttempts".equals(arg) && i + 1 < args.length) {
                parsed.retryAttempts = parseInt(args[++i], parsed.retryAttempts);
                continue;
            }
            if ("--retryDelayMs".equals(arg) && i + 1 < args.length) {
                parsed.retryDelayMs = parseInt(args[++i], parsed.retryDelayMs);
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
        System.out.println("  --retryAttempts <number>      (default: 30)");
        System.out.println("  --retryDelayMs <number>       (default: 1000)");
        System.out.println("  --help");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class Arguments {
        private String wsUrl = "ws://127.0.0.1:8766";
        private boolean printSnapshots = true;
        private String initialCommandJson;
        private int retryAttempts = 30;
        private int retryDelayMs = 1000;
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
