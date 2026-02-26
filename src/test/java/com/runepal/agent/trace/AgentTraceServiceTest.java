package com.runepal.agent.trace;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AgentTraceServiceTest {
    @Test
    public void keepsBoundedRingBuffer() {
        AgentTraceService service = new AgentTraceService(3);
        service.record("a", "one", new JsonObject());
        service.record("b", "two", new JsonObject());
        service.record("c", "three", new JsonObject());
        service.record("d", "four", new JsonObject());

        List<TraceEvent> events = service.getRecent(10);
        Assert.assertEquals(3, events.size());
        Assert.assertEquals("b", events.get(0).getCategory());
        Assert.assertEquals("d", events.get(2).getCategory());
    }
}
