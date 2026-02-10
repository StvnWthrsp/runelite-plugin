package com.runepal.runtime;

import com.runepal.EventService;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionBagTest {
    private static class TestEvent {
        private final int value;

        private TestEvent(int value) {
            this.value = value;
        }
    }

    @Test
    public void clearUnsubscribesAllHandlers() {
        EventService eventService = new EventService();
        SubscriptionBag bag = new SubscriptionBag(eventService);
        AtomicInteger observed = new AtomicInteger(0);

        bag.subscribe(TestEvent.class, event -> observed.addAndGet(event.value));
        bag.subscribe(TestEvent.class, event -> observed.addAndGet(event.value * 2));

        eventService.publish(new TestEvent(2));
        Assert.assertEquals(6, observed.get());

        bag.clear();
        eventService.publish(new TestEvent(3));
        Assert.assertEquals(6, observed.get());
        Assert.assertEquals(0, eventService.getSubscriberCount(TestEvent.class));
    }
}
