package com.runepal.agent.wiki;

import org.junit.Assert;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

public class OsrsWikiClientTest {
    @Test
    public void enforcesAllowlist() throws Exception {
        Path cacheDir = Files.createTempDirectory("wiki-allowlist-test");
        OsrsWikiClient client = new OsrsWikiClient(
                uri -> "{}",
                cacheDir,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1),
                Duration.ZERO,
                1000000);

        client.verifyAllowlist(URI.create("https://oldschool.runescape.wiki/api.php"));

        boolean threw = false;
        try {
            client.verifyAllowlist(URI.create("https://example.com/api.php"));
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        Assert.assertTrue(threw);
    }

    @Test
    public void usesDiskCacheWithinTtl() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        String response = "{\"query\":{\"search\":[{\"title\":\"Legends' Quest\",\"snippet\":\"quest\"}]}}";

        Path cacheDir = Files.createTempDirectory("wiki-cache-test");
        OsrsWikiClient client = new OsrsWikiClient(
                uri -> {
                    calls.incrementAndGet();
                    return response;
                },
                cacheDir,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1),
                Duration.ZERO,
                1000000);

        client.search("Legends Quest", 1);
        client.search("Legends Quest", 1);

        Assert.assertEquals(1, calls.get());
    }
}
