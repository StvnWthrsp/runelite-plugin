package com.runepal.agent.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ScriptRepository {
    private static final String SCRIPTS_DIR = ".runelite/runepal/scripts";

    private final Path scriptsPath;
    private final ScriptParser parser;
    private final Gson gson;

    public ScriptRepository(ScriptParser parser) {
        this(parser, resolveDefaultScriptsPath());
    }

    public ScriptRepository(ScriptParser parser, Path scriptsPath) {
        this.parser = parser;
        this.scriptsPath = scriptsPath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<ScriptMetadata> listScripts() {
        ensureScriptsDirectory();
        List<ScriptMetadata> scripts = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(scriptsPath)) {
            stream
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(path -> scripts.add(new ScriptMetadata(
                            fileNameToScriptName(path.getFileName().toString()),
                            path.getFileName().toString(),
                            readLastModified(path))));
        } catch (IOException e) {
            log.warn("Failed to list scripts from {}", scriptsPath, e);
        }
        return scripts;
    }

    public Optional<ScriptSpec> loadScript(String scriptName) {
        ensureScriptsDirectory();
        String safeName = sanitizeScriptName(scriptName);
        if (safeName.isEmpty()) {
            return Optional.empty();
        }

        Path file = scriptsPath.resolve(safeName + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            return Optional.of(parser.parse(content));
        } catch (Exception e) {
            log.warn("Failed to load script {}", file, e);
            return Optional.empty();
        }
    }

    public void saveScript(ScriptSpec spec) {
        ensureScriptsDirectory();
        String safeName = sanitizeScriptName(spec.getName());
        if (safeName.isEmpty()) {
            throw new IllegalArgumentException("Script name cannot be empty");
        }

        Path file = scriptsPath.resolve(safeName + ".json");
        JsonObject json = spec.toJson();
        String serialized = gson.toJson(json);

        try {
            Files.write(file,
                    serialized.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save script " + safeName, e);
        }
    }

    public boolean deleteScript(String scriptName) {
        ensureScriptsDirectory();
        String safeName = sanitizeScriptName(scriptName);
        if (safeName.isEmpty()) {
            return false;
        }

        Path file = scriptsPath.resolve(safeName + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Failed to delete script {}", file, e);
            return false;
        }
    }

    private void ensureScriptsDirectory() {
        try {
            Files.createDirectories(scriptsPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create scripts directory " + scriptsPath, e);
        }
    }

    private String sanitizeScriptName(String scriptName) {
        if (scriptName == null) {
            return "";
        }

        String safe = scriptName.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        while (safe.contains("__")) {
            safe = safe.replace("__", "_");
        }
        if (safe.startsWith(".")) {
            safe = "script" + safe;
        }
        return safe;
    }

    private static String fileNameToScriptName(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.toLowerCase().endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }

    private Instant readLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static Path resolveDefaultScriptsPath() {
        String home = System.getProperty("user.home", ".");
        return Paths.get(home).resolve(SCRIPTS_DIR);
    }
}
