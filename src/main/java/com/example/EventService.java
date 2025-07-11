package com.example;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple event bus service for publish/subscribe messaging.
 * Allows decoupling of event producers from event consumers.
 */
@Singleton
@Slf4j
public class EventService {
    
    // Map of event types to lists of subscribers
    private final Map<Class<?>, List<Consumer<Object>>> subscribers = new ConcurrentHashMap<>();
    
    /**
     * Subscribe to events of a specific type.
     * 
     * @param eventType the class type of events to subscribe to
     * @param handler the handler function to call when events are published
     * @param <T> the type of event
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            throw new IllegalArgumentException("Event type and handler cannot be null");
        }
        
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                  .add((Consumer<Object>) handler);
        
        log.debug("Subscribed to event type: {}", eventType.getSimpleName());
    }
    
    /**
     * Publish an event to all subscribers of its type.
     * 
     * @param event the event to publish
     * @param <T> the type of event
     */
    public <T> void publish(T event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return;
        }
        
        Class<?> eventType = event.getClass();
        List<Consumer<Object>> eventHandlers = subscribers.get(eventType);
        
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            log.debug("No subscribers for event type: {}", eventType.getSimpleName());
            return;
        }
        
        log.trace("Publishing event {} to {} subscribers", eventType.getSimpleName(), eventHandlers.size());
        
        // Notify all subscribers
        for (Consumer<Object> handler : eventHandlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("Error in event handler for {}: {}", eventType.getSimpleName(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Unsubscribe a specific handler from an event type.
     * 
     * @param eventType the class type of events to unsubscribe from
     * @param handler the handler function to remove
     * @param <T> the type of event
     * @return true if the handler was found and removed, false otherwise
     */
    public <T> boolean unsubscribe(Class<T> eventType, Consumer<T> handler) {
        if (eventType == null || handler == null) {
            return false;
        }
        
        List<Consumer<Object>> eventHandlers = subscribers.get(eventType);
        if (eventHandlers == null) {
            return false;
        }
        
        boolean removed = eventHandlers.remove(handler);
        
        // Clean up empty lists
        if (eventHandlers.isEmpty()) {
            subscribers.remove(eventType);
        }
        
        if (removed) {
            log.debug("Unsubscribed from event type: {}", eventType.getSimpleName());
        }
        
        return removed;
    }
    
    /**
     * Remove all subscribers for a specific event type.
     * 
     * @param eventType the class type to clear subscribers for
     * @param <T> the type of event
     * @return the number of subscribers that were removed
     */
    public <T> int clearSubscribers(Class<T> eventType) {
        if (eventType == null) {
            return 0;
        }
        
        List<Consumer<Object>> eventHandlers = subscribers.remove(eventType);
        int count = eventHandlers != null ? eventHandlers.size() : 0;
        
        if (count > 0) {
            log.debug("Cleared {} subscribers for event type: {}", count, eventType.getSimpleName());
        }
        
        return count;
    }
    
    /**
     * Remove all subscribers from the event service.
     */
    public void clearAllSubscribers() {
        int totalSubscribers = subscribers.values().stream()
                                         .mapToInt(List::size)
                                         .sum();
        
        subscribers.clear();
        
        if (totalSubscribers > 0) {
            log.debug("Cleared all {} subscribers from event service", totalSubscribers);
        }
    }
    
    /**
     * Get the number of subscribers for a specific event type.
     * 
     * @param eventType the class type to check
     * @param <T> the type of event
     * @return the number of subscribers
     */
    public <T> int getSubscriberCount(Class<T> eventType) {
        List<Consumer<Object>> eventHandlers = subscribers.get(eventType);
        return eventHandlers != null ? eventHandlers.size() : 0;
    }
    
    /**
     * Get the total number of subscribers across all event types.
     * 
     * @return the total number of subscribers
     */
    public int getTotalSubscriberCount() {
        return subscribers.values().stream()
                         .mapToInt(List::size)
                         .sum();
    }
    
    /**
     * Get all event types that have subscribers.
     * 
     * @return a set of event types
     */
    public Set<Class<?>> getSubscribedEventTypes() {
        return new HashSet<>(subscribers.keySet());
    }
} 