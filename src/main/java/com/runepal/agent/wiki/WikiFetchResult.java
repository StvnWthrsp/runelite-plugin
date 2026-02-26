package com.runepal.agent.wiki;

import com.google.gson.JsonObject;

import java.time.Instant;

public final class WikiFetchResult {
    private final String title;
    private final String canonicalUrl;
    private final Instant fetchedAt;
    private final String content;
    private final int totalLength;

    public WikiFetchResult(String title, String canonicalUrl, Instant fetchedAt, String content) {
        this.title = title == null ? "" : title;
        this.canonicalUrl = canonicalUrl;
        this.fetchedAt = fetchedAt;
        this.content = content == null ? "" : content;
        this.totalLength = this.content.length();
    }

    public String getTitle() {
        return title;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getContent() {
        return content;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public JsonObject toJson(int previewChars) {
        int max = Math.max(128, previewChars);
        JsonObject json = new JsonObject();
        json.addProperty("title", title);
        json.addProperty("canonicalUrl", canonicalUrl);
        json.addProperty("fetchedAt", fetchedAt.toString());
        json.addProperty("totalLength", totalLength);
        String preview = content.length() > max ? content.substring(0, max) : content;
        json.addProperty("preview", preview);
        return json;
    }
}
