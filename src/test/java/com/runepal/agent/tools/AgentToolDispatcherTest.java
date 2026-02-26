package com.runepal.agent.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runepal.agent.memory.AgentMemoryStore;
import com.runepal.agent.trace.AgentTraceService;
import com.runepal.agent.wiki.OsrsWikiClient;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

public class AgentToolDispatcherTest {
    @Test
    public void dispatchesToolAndTracesResult() throws Exception {
        AgentToolRegistry registry = new AgentToolRegistry();
        registry.register(new AgentTool() {
            @Override
            public String getName() {
                return "test.echo";
            }

            @Override
            public String getDescription() {
                return "Echo tool";
            }

            @Override
            public JsonArray getArgumentHints() {
                return new JsonArray();
            }

            @Override
            public AgentToolResult execute(JsonObject arguments, AgentToolContext context) {
                JsonObject payload = new JsonObject();
                payload.add("echo", arguments);
                return AgentToolResult.ok(payload);
            }
        });

        AgentTraceService traceService = new AgentTraceService(20);
        OsrsWikiClient wikiClient = new OsrsWikiClient(
                uri -> "{}",
                Files.createTempDirectory("tool-dispatch-test"),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1),
                Duration.ZERO,
                1000000);
        AgentToolContext context = new AgentToolContext(
                wikiClient,
                new AgentMemoryStore(Files.createTempFile("memory", ".jsonl")),
                traceService,
                null,
                JsonObject::new,
                null);

        AgentToolDispatcher dispatcher = new AgentToolDispatcher(registry, context, traceService);
        JsonObject args = new JsonObject();
        args.addProperty("x", 1);

        AgentToolResult result = dispatcher.dispatch("test.echo", args, "c1");
        Assert.assertTrue(result.isOk());
        Assert.assertTrue(result.getPayload().has("echo"));
        Assert.assertEquals(2, traceService.size());
    }
}
