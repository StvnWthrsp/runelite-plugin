package com.example.core;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.StatChanged;

import com.example.EventService;

import java.util.Objects;

/**
 * Manages event handling and publishing for the plugin.
 * Centralizes event distribution to avoid direct coupling between plugin and tasks.
 */
@Singleton
@Slf4j
public class EventManager {
    private final EventService eventService;

    public EventManager(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
    }

    /**
     * Handle and publish an animation changed event.
     * 
     * @param animationChanged the animation changed event
     */
    public void onAnimationChanged(AnimationChanged animationChanged) {
        if (eventService != null) {
            eventService.publish(animationChanged);
        }
    }

    /**
     * Handle and publish a stat changed event.
     * 
     * @param statChanged the stat changed event
     */
    public void onStatChanged(StatChanged statChanged) {
        if (eventService != null) {
            eventService.publish(statChanged);
        }
    }

    /**
     * Handle and publish an interacting changed event.
     * 
     * @param interactingChanged the interacting changed event
     */
    public void onInteractingChanged(InteractingChanged interactingChanged) {
        if (eventService != null) {
            eventService.publish(interactingChanged);
        }
    }

    /**
     * Handle and publish a game state changed event.
     * 
     * @param gameStateChanged the game state changed event
     */
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (eventService != null) {
            eventService.publish(gameStateChanged);
        }
    }

    /**
     * Handle and publish a game tick event.
     * 
     * @param gameTick the game tick event
     */
    public void onGameTick(GameTick gameTick) {
        if (eventService != null) {
            eventService.publish(gameTick);
        }
    }

    /**
     * Clear all event subscribers (cleanup method).
     */
    public void clearAllSubscribers() {
        if (eventService != null) {
            eventService.clearAllSubscribers();
        }
    }
}