package com.runepal.agent.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WikiSearchResult {
    public static final class Item {
        private final String title;
        private final String snippet;

        public Item(String title, String snippet) {
            this.title = title == null ? "" : title;
            this.snippet = snippet == null ? "" : snippet;
        }

        public String getTitle() {
            return title;
        }

        public String getSnippet() {
            return snippet;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("title", title);
            json.addProperty("snippet", snippet);
            return json;
        }
    }

    private final String canonicalUrl;
    private final Instant fetchedAt;
    private final List<Item> items;

    public WikiSearchResult(String canonicalUrl, Instant fetchedAt, List<Item> items) {
        this.canonicalUrl = canonicalUrl;
        this.fetchedAt = fetchedAt;
        this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
    }

    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("canonicalUrl", canonicalUrl);
        json.addProperty("fetchedAt", fetchedAt.toString());
        JsonArray itemArray = new JsonArray();
        for (Item item : items) {
            itemArray.add(item.toJson());
        }
        json.add("items", itemArray);
        return json;
    }
}
