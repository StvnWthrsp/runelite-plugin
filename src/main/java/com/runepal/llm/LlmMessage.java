package com.runepal.llm;

import java.util.Objects;

public final class LlmMessage {
    private final String role;
    private final String content;

    private LlmMessage(String role, String content) {
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
    }

    public static LlmMessage system(String content) {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
