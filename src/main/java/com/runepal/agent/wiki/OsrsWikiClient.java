package com.runepal.agent.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class OsrsWikiClient {
    public interface Transport {
        String get(URI uri) throws IOException, InterruptedException;
    }

    private static final String BASE_API = "https://oldschool.runescape.wiki/api.php";
    private static final String ALLOWED_SCHEME = "https";
    private static final String ALLOWED_HOST = "oldschool.runescape.wiki";
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_MIN_INTERVAL = Duration.ofSeconds(1);
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 2_000_000;
    private static final int CONTENT_PREVIEW_CHARS = 1200;

    private final Transport transport;
    private final Path cacheDirectory;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Duration minInterval;
    private final int maxResponseBytes;

    private Instant nextAllowedRequestAt = Instant.EPOCH;

    public OsrsWikiClient() {
        this(defaultTransport(),
                Paths.get(System.getProperty("user.home"), ".runelite", "runepal", "wiki_cache"),
                Clock.systemUTC(),
                DEFAULT_CACHE_TTL,
                DEFAULT_MIN_INTERVAL,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    public OsrsWikiClient(Transport transport,
                          Path cacheDirectory,
                          Clock clock,
                          Duration cacheTtl,
                          Duration minInterval,
                          int maxResponseBytes) {
        this.transport = Objects.requireNonNull(transport, "transport cannot be null");
        this.cacheDirectory = Objects.requireNonNull(cacheDirectory, "cacheDirectory cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.cacheTtl = cacheTtl == null ? DEFAULT_CACHE_TTL : cacheTtl;
        this.minInterval = minInterval == null ? DEFAULT_MIN_INTERVAL : minInterval;
        this.maxResponseBytes = Math.max(64 * 1024, maxResponseBytes);
    }

    public synchronized WikiSearchResult search(String query, int limit) {
        String safeQuery = query == null ? "" : query.trim();
        int safeLimit = Math.max(1, Math.min(10, limit));
        URI uri = URI.create(BASE_API
                + "?action=query&list=search&format=json&srsearch=" + encode(safeQuery)
                + "&srlimit=" + safeLimit);

        JsonObject json = fetchJson(uri);
        List<WikiSearchResult.Item> items = new ArrayList<>();

        if (json.has("query") && json.get("query").isJsonObject()) {
            JsonObject queryJson = json.getAsJsonObject("query");
            if (queryJson.has("search") && queryJson.get("search").isJsonArray()) {
                JsonArray searchArray = queryJson.getAsJsonArray("search");
                for (JsonElement element : searchArray) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = element.getAsJsonObject();
                    String title = readString(item, "title", "");
                    String snippet = readString(item, "snippet", "");
                    items.add(new WikiSearchResult.Item(title, snippet));
                }
            }
        }

        String canonicalUrl = "https://oldschool.runescape.wiki/w/Special:Search?search=" + encode(safeQuery);
        return new WikiSearchResult(canonicalUrl, Instant.now(clock), items);
    }

    public synchronized WikiFetchResult fetch(String title, Integer section, WikiFormat format) {
        String safeTitle = title == null ? "" : title.trim();
        WikiFormat safeFormat = format == null ? WikiFormat.WIKITEXT : format;

        StringBuilder uriBuilder = new StringBuilder(BASE_API)
                .append("?action=parse&format=json&page=")
                .append(encode(safeTitle));

        if (section != null && section >= 0) {
            uriBuilder.append("&section=").append(section);
        }

        if (safeFormat == WikiFormat.TEXT) {
            uriBuilder.append("&prop=text|sections");
        } else {
            uriBuilder.append("&prop=wikitext|sections");
        }

        URI uri = URI.create(uriBuilder.toString());
        JsonObject json = fetchJson(uri);
        JsonObject parse = json.has("parse") && json.get("parse").isJsonObject()
                ? json.getAsJsonObject("parse") : new JsonObject();

        String resolvedTitle = readString(parse, "title", safeTitle);
        String content = "";
        if (safeFormat == WikiFormat.TEXT && parse.has("text") && parse.get("text").isJsonObject()) {
            content = readString(parse.getAsJsonObject("text"), "*", "");
        }
        if (safeFormat == WikiFormat.WIKITEXT && parse.has("wikitext") && parse.get("wikitext").isJsonObject()) {
            content = readString(parse.getAsJsonObject("wikitext"), "*", "");
        }

        String canonicalUrl = "https://oldschool.runescape.wiki/w/" + encodeTitleForUrl(resolvedTitle);
        return new WikiFetchResult(resolvedTitle, canonicalUrl, Instant.now(clock), content);
    }

    public JsonObject toPreviewJson(WikiFetchResult result) {
        return result.toJson(CONTENT_PREVIEW_CHARS);
    }

    private JsonObject fetchJson(URI uri) {
        verifyAllowlist(uri);
        String raw = fetchWithCache(uri);
        if (raw.length() > maxResponseBytes) {
            throw new IllegalArgumentException("Wiki response exceeded max size cap");
        }

        JsonElement parsed = JsonParser.parseString(raw);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Wiki response was not a JSON object");
        }
        return parsed.getAsJsonObject();
    }

    private String fetchWithCache(URI uri) {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create wiki cache directory", e);
        }

        Path cacheFile = cacheDirectory.resolve(cacheFileName(uri.toString()));
        Instant now = Instant.now(clock);

        if (Files.exists(cacheFile)) {
            try {
                JsonObject cacheJson = JsonParser.parseString(Files.readString(cacheFile, StandardCharsets.UTF_8)).getAsJsonObject();
                String fetchedAtRaw = readString(cacheJson, "fetchedAt", "");
                String body = readString(cacheJson, "body", "");
                if (!fetchedAtRaw.isEmpty()) {
                    Instant fetchedAt = Instant.parse(fetchedAtRaw);
                    if (fetchedAt.plus(cacheTtl).isAfter(now)) {
                        return body;
                    }
                }
            } catch (Exception ignored) {
                // ignore broken cache entry
            }
        }

        enforceRateLimit(now);
        String response;
        try {
            response = transport.get(uri);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Wiki request failed: " + e.getMessage(), e);
        }

        JsonObject cacheJson = new JsonObject();
        cacheJson.addProperty("url", uri.toString());
        cacheJson.addProperty("fetchedAt", now.toString());
        cacheJson.addProperty("body", response);
        try {
            Files.writeString(cacheFile, cacheJson.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ignored) {
            // cache write failures should not fail the request
        }

        return response;
    }

    private void enforceRateLimit(Instant now) {
        if (now.isBefore(nextAllowedRequestAt)) {
            Duration sleepDuration = Duration.between(now, nextAllowedRequestAt);
            try {
                Thread.sleep(Math.max(1L, sleepDuration.toMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        nextAllowedRequestAt = Instant.now(clock).plus(minInterval);
    }

    void verifyAllowlist(URI uri) {
        if (!ALLOWED_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Wiki URI scheme not allowlisted");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (!ALLOWED_HOST.equals(host)) {
            throw new IllegalArgumentException("Wiki host not allowlisted: " + host);
        }
    }

    private String cacheFileName(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash) + ".json";
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash wiki cache key", e);
        }
    }

    private String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }

    private String encodeTitleForUrl(String title) {
        String safe = title == null ? "" : title.trim().replace(' ', '_');
        return encode(safe).replace("%2F", "/");
    }

    private String readString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static Transport defaultTransport() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        };
    }
}
