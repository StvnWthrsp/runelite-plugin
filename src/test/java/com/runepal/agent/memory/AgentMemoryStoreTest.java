package com.runepal.agent.memory;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class AgentMemoryStoreTest {
    @Test
    public void searchesByQueryAndTag() throws Exception {
        Path tempDir = Files.createTempDirectory("memory-store-test");
        Path memoryFile = tempDir.resolve("memory.jsonl");
        AgentMemoryStore store = new AgentMemoryStore(memoryFile);

        store.append(new AgentMemoryEntry(Instant.parse("2026-01-01T00:00:00Z"), "Cow combat",
                Arrays.asList("combat", "cow"), "Worked when cows were on-screen", new JsonObject()));
        store.append(new AgentMemoryEntry(Instant.parse("2026-01-02T00:00:00Z"), "Quest research",
                Arrays.asList("wiki"), "Legends quest requirements", new JsonObject()));

        List<AgentMemoryEntry> queryMatches = store.search("legends", 5, null);
        Assert.assertEquals(1, queryMatches.size());
        Assert.assertEquals("Quest research", queryMatches.get(0).getTitle());

        List<AgentMemoryEntry> tagMatches = store.search("", 5, Arrays.asList("combat"));
        Assert.assertEquals(1, tagMatches.size());
        Assert.assertEquals("Cow combat", tagMatches.get(0).getTitle());
    }
}
