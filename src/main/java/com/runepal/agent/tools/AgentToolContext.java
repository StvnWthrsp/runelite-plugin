package com.runepal.agent.tools;

import com.google.gson.JsonObject;
import com.runepal.agent.AgentService;
import com.runepal.agent.AgentSnapshotBuilder;
import com.runepal.agent.memory.AgentMemoryStore;
import com.runepal.agent.trace.AgentTraceService;
import com.runepal.agent.wiki.OsrsWikiClient;

import java.util.Objects;
import java.util.function.Supplier;

public final class AgentToolContext {
    private final OsrsWikiClient wikiClient;
    private final AgentMemoryStore memoryStore;
    private final AgentTraceService traceService;
    private final AgentSnapshotBuilder snapshotBuilder;
    private final Supplier<JsonObject> latestSnapshotSupplier;
    private final AgentService agentService;

    public AgentToolContext(OsrsWikiClient wikiClient,
                            AgentMemoryStore memoryStore,
                            AgentTraceService traceService,
                            AgentSnapshotBuilder snapshotBuilder,
                            Supplier<JsonObject> latestSnapshotSupplier,
                            AgentService agentService) {
        this.wikiClient = Objects.requireNonNull(wikiClient, "wikiClient cannot be null");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore cannot be null");
        this.traceService = Objects.requireNonNull(traceService, "traceService cannot be null");
        this.snapshotBuilder = snapshotBuilder;
        this.latestSnapshotSupplier = Objects.requireNonNull(latestSnapshotSupplier, "latestSnapshotSupplier cannot be null");
        this.agentService = agentService;
    }

    public OsrsWikiClient getWikiClient() {
        return wikiClient;
    }

    public AgentMemoryStore getMemoryStore() {
        return memoryStore;
    }

    public AgentTraceService getTraceService() {
        return traceService;
    }

    public AgentSnapshotBuilder getSnapshotBuilder() {
        return snapshotBuilder;
    }

    public JsonObject getLatestSnapshot() {
        JsonObject snapshot = latestSnapshotSupplier.get();
        return snapshot == null ? new JsonObject() : snapshot;
    }

    public AgentService getAgentService() {
        return agentService;
    }
}
