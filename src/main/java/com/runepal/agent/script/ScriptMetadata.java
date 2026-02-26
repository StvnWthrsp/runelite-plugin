package com.runepal.agent.script;

import java.time.Instant;

public final class ScriptMetadata {
    private final String name;
    private final String fileName;
    private final Instant updatedAt;

    public ScriptMetadata(String name, String fileName, Instant updatedAt) {
        this.name = name;
        this.fileName = fileName;
        this.updatedAt = updatedAt;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
