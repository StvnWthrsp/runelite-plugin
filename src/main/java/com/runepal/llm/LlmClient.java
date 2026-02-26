package com.runepal.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runepal.BotConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class LlmClient {
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1";
    private static final String NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1";

    private final BotConfig config;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public LlmClient(BotConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public LlmResult ping() {
        return chatCompletion(
                java.util.Collections.singletonList(LlmMessage.user("Reply with the exact text PONG.")),
                LlmRequestOptions.builder().maxTokens(8).build());
    }

    public LlmResult chatCompletion(List<LlmMessage> messages, LlmRequestOptions options) {
        if (messages == null || messages.isEmpty()) {
            return LlmResult.error("No messages were provided", 0, null);
        }

        String apiKey = trimToEmpty(config.llmApiKey());
        if (apiKey.isEmpty()) {
            return LlmResult.error("LLM API key is empty", 0, null);
        }

        String endpoint = resolveEndpoint();
        if (endpoint.isEmpty()) {
            return LlmResult.error("LLM base URL is empty", 0, null);
        }

        JsonObject payload = buildPayload(messages, options == null ? LlmRequestOptions.defaults() : options);
        LlmResult result = doChatCompletionRequest(endpoint, apiKey, payload);

        if (result.isSuccess()) {
            return result;
        }

        // Compatibility fallback: some models/providers require max_completion_tokens instead of max_tokens.
        // If we detect this error, retry once with the alternate parameter name.
        String error = result.getErrorMessage() == null ? "" : result.getErrorMessage().toLowerCase(Locale.US);
        if (payload.has("max_tokens")
                && result.getStatusCode() == 400
                && error.contains("max_completion_tokens")
                && error.contains("max_tokens")) {
            JsonObject retryPayload = payload.deepCopy();
            JsonElement maxTokensElement = retryPayload.get("max_tokens");
            retryPayload.remove("max_tokens");
            if (maxTokensElement != null && maxTokensElement.isJsonPrimitive() && maxTokensElement.getAsJsonPrimitive().isNumber()) {
                retryPayload.add("max_completion_tokens", maxTokensElement);
            }
            return doChatCompletionRequest(endpoint, apiKey, retryPayload);
        }

        return result;
    }

    private LlmResult doChatCompletionRequest(String endpoint, String apiKey, JsonObject payload) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(Math.max(1000, config.llmRequestTimeoutMs())))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)));

        if (config.llmProvider() == LlmProviderType.OPENROUTER) {
            builder.header("HTTP-Referer", "https://runepal.local")
                    .header("X-Title", "Runepal");
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("LLM request failed: {}", e.getMessage());
            return LlmResult.error("LLM request failed: " + e.getMessage(), 0, null);
        }

        JsonObject parsedBody = parseJsonObject(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorMessage = extractErrorMessage(parsedBody, response.body());
            return LlmResult.error(errorMessage, response.statusCode(), parsedBody);
        }

        String content = extractAssistantContent(parsedBody);
        if (content == null || content.trim().isEmpty()) {
            return LlmResult.error("LLM response did not contain assistant content", response.statusCode(), parsedBody);
        }

        return LlmResult.success(content, response.statusCode(), parsedBody);
    }

    private JsonObject buildPayload(List<LlmMessage> messages, LlmRequestOptions options) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", trimToEmpty(config.llmModel()));

        JsonArray messagesJson = new JsonArray();
        for (LlmMessage message : messages) {
            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("role", message.getRole());
            messageJson.addProperty("content", message.getContent());
            messagesJson.add(messageJson);
        }
        payload.add("messages", messagesJson);

        Integer maxTokens = options.getMaxTokens();
        if (maxTokens == null && config.llmMaxTokens() > 0) {
            maxTokens = config.llmMaxTokens();
        }
        if (maxTokens != null && maxTokens > 0) {
            payload.addProperty("max_tokens", maxTokens);
        }

        if (options.isRequireJsonResponse()) {
            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            payload.add("response_format", responseFormat);
        }

        return payload;
    }

    private String resolveEndpoint() {
        String baseUrl = trimToEmpty(config.llmBaseUrl());
        if (baseUrl.isEmpty()) {
            switch (config.llmProvider()) {
                case OPENROUTER:
                    baseUrl = OPENROUTER_BASE_URL;
                    break;
                case NVIDIA:
                    baseUrl = NVIDIA_BASE_URL;
                    break;
                case OPENAI:
                    baseUrl = OPENAI_BASE_URL;
                    break;
                case CUSTOM:
                default:
                    baseUrl = OPENAI_BASE_URL;
                    break;
            }
        }

        String normalized = stripTrailingSlashes(baseUrl);
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String extractErrorMessage(JsonObject body, String rawBody) {
        if (body != null && body.has("error") && body.get("error").isJsonObject()) {
            JsonObject error = body.getAsJsonObject("error");
            if (error.has("message")) {
                return error.get("message").getAsString();
            }
        }
        return "LLM request failed: " + (rawBody == null ? "unknown error" : rawBody);
    }

    private String extractAssistantContent(JsonObject body) {
        if (body == null || !body.has("choices") || !body.get("choices").isJsonArray()) {
            return null;
        }

        JsonArray choices = body.getAsJsonArray("choices");
        if (choices.size() == 0) {
            return null;
        }

        JsonObject firstChoice = choices.get(0).isJsonObject() ? choices.get(0).getAsJsonObject() : null;
        if (firstChoice == null || !firstChoice.has("message")) {
            return null;
        }

        JsonObject message = firstChoice.get("message").isJsonObject()
                ? firstChoice.getAsJsonObject("message")
                : null;
        if (message == null || !message.has("content")) {
            return null;
        }

        JsonElement contentElement = message.get("content");
        if (contentElement.isJsonPrimitive()) {
            return contentElement.getAsString();
        }

        if (contentElement.isJsonArray()) {
            StringBuilder builder = new StringBuilder();
            JsonArray parts = contentElement.getAsJsonArray();
            for (JsonElement part : parts) {
                if (!part.isJsonObject()) {
                    continue;
                }
                JsonObject partObject = part.getAsJsonObject();
                if (partObject.has("text")) {
                    builder.append(partObject.get("text").getAsString());
                }
            }
            return builder.toString();
        }

        return null;
    }

    private JsonObject parseJsonObject(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }

            JsonObject wrapper = new JsonObject();
            wrapper.add("body", parsed);
            return wrapper;
        } catch (Exception e) {
            JsonObject fallback = new JsonObject();
            fallback.addProperty("body", body);
            return fallback;
        }
    }

    private String stripTrailingSlashes(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
