package com.runepal.runtime;

import com.runepal.EventService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SubscriptionBag {
    private static class Subscription<T> {
        private final Class<T> eventType;
        private final Consumer<T> handler;

        private Subscription(Class<T> eventType, Consumer<T> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }
    }

    private final EventService eventService;
    private final List<Subscription<?>> subscriptions = new ArrayList<>();

    public SubscriptionBag(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
    }

    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        eventService.subscribe(eventType, handler);
        subscriptions.add(new Subscription<>(eventType, handler));
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        for (Subscription<?> subscription : subscriptions) {
            Subscription<Object> typed = (Subscription<Object>) subscription;
            eventService.unsubscribe(typed.eventType, typed.handler);
        }
        subscriptions.clear();
    }
}
