package com.runepal.llm;

import com.google.gson.JsonObject;

public final class LlmResult {
    private final boolean success;
    private final String content;
    private final String errorMessage;
    private final int statusCode;
    private final JsonObject rawResponse;

    private LlmResult(boolean success, String content, String errorMessage, int statusCode, JsonObject rawResponse) {
        this.success = success;
        this.content = content;
        this.errorMessage = errorMessage;
        this.statusCode = statusCode;
        this.rawResponse = rawResponse;
    }

    public static LlmResult success(String content, int statusCode, JsonObject rawResponse) {
        return new LlmResult(true, content, null, statusCode, rawResponse);
    }

    public static LlmResult error(String errorMessage, int statusCode, JsonObject rawResponse) {
        return new LlmResult(false, null, errorMessage, statusCode, rawResponse);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getContent() {
        return content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public JsonObject getRawResponse() {
        return rawResponse;
    }
}
