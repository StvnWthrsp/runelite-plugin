package com.runepal.agent.memory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class AgentMemoryStore {
    private static final String DEFAULT_RELATIVE_PATH = ".runelite/runepal/agent/memory.jsonl";

    private final Path filePath;
    private final Gson gson = new Gson();

    public AgentMemoryStore() {
        this(Paths.get(System.getProperty("user.home"), DEFAULT_RELATIVE_PATH));
    }

    public AgentMemoryStore(Path filePath) {
        this.filePath = filePath;
    }

    public synchronized void append(AgentMemoryEntry entry) {
        AgentMemoryEntry safeEntry = entry == null
                ? new AgentMemoryEntry(Instant.now(), "", Collections.emptyList(), "", new JsonObject())
                : entry;
        ensureParentDirectory();
        try {
            String line = gson.toJson(safeEntry.toJson()) + System.lineSeparator();
            Files.write(filePath, line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to append memory entry", e);
        }
    }

    public synchronized List<AgentMemoryEntry> search(String query, int limit, List<String> tags) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        List<String> normalizedTags = normalizeTags(tags);
        List<AgentMemoryEntry> all = readAll();
        List<AgentMemoryEntry> matched = new ArrayList<>();

        for (AgentMemoryEntry entry : all) {
            if (!normalizedTags.isEmpty() && !entryHasTags(entry, normalizedTags)) {
                continue;
            }
            if (!normalizedQuery.isEmpty() && !entryMatchesQuery(entry, normalizedQuery)) {
                continue;
            }
            matched.add(entry);
        }

        matched.sort(Comparator.comparing(AgentMemoryEntry::getTimestamp).reversed());
        int max = Math.max(1, limit);
        if (matched.size() > max) {
            return new ArrayList<>(matched.subList(0, max));
        }
        return matched;
    }

    public synchronized AgentMemoryEntry getMostRecent() {
        List<AgentMemoryEntry> entries = readAll();
        if (entries.isEmpty()) {
            return null;
        }
        entries.sort(Comparator.comparing(AgentMemoryEntry::getTimestamp).reversed());
        return entries.get(0);
    }

    public Path getFilePath() {
        return filePath;
    }

    private void ensureParentDirectory() {
        Path parent = filePath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create memory directory", e);
        }
    }

    private List<AgentMemoryEntry> readAll() {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        List<AgentMemoryEntry> entries = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
                    entries.add(AgentMemoryEntry.fromJson(json));
                } catch (Exception ignored) {
                    // ignore malformed line
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read memory store", e);
        }
        return entries;
    }

    private boolean entryMatchesQuery(AgentMemoryEntry entry, String query) {
        if (entry.getTitle().toLowerCase(Locale.US).contains(query)) {
            return true;
        }
        if (entry.getContent().toLowerCase(Locale.US).contains(query)) {
            return true;
        }
        for (String tag : entry.getTags()) {
            if (tag.toLowerCase(Locale.US).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean entryHasTags(AgentMemoryEntry entry, List<String> normalizedTags) {
        List<String> tags = new ArrayList<>();
        for (String tag : entry.getTags()) {
            tags.add(tag.toLowerCase(Locale.US));
        }
        for (String required : normalizedTags) {
            if (!tags.contains(required)) {
                return false;
            }
        }
        return true;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String normalized = tag.trim().toLowerCase(Locale.US);
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }
}
