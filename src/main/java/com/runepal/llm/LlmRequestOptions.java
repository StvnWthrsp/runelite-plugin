package com.runepal.llm;

public final class LlmRequestOptions {
    private final Integer maxTokens;
    private final Double temperature;
    private final boolean requireJsonResponse;

    private LlmRequestOptions(Integer maxTokens, Double temperature, boolean requireJsonResponse) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.requireJsonResponse = requireJsonResponse;
    }

    public static LlmRequestOptions defaults() {
        return new LlmRequestOptions(null, null, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public boolean isRequireJsonResponse() {
        return requireJsonResponse;
    }

    public static final class Builder {
        private Integer maxTokens;
        private Double temperature;
        private boolean requireJsonResponse;

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder requireJsonResponse(boolean requireJsonResponse) {
            this.requireJsonResponse = requireJsonResponse;
            return this;
        }

        public LlmRequestOptions build() {
            return new LlmRequestOptions(maxTokens, temperature, requireJsonResponse);
        }
    }
}
