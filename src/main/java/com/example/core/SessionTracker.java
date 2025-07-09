package com.example.core;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Tracks session statistics including XP gained and runtime.
 */
@Singleton
@Slf4j
public class SessionTracker {
    private final Client client;
    
    private long sessionStartXp = 0;
    private Instant sessionStartTime = null;

    public SessionTracker(Client client) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
    }

    /**
     * Initialize session tracking when player logs in.
     */
    public void initializeSession() {
        if (client.getLocalPlayer() != null && sessionStartTime == null) {
            sessionStartXp = client.getSkillExperience(Skill.MINING);
            sessionStartTime = Instant.now();
            log.info("Session tracking initialized. Starting XP: {}", sessionStartXp);
        }
    }

    /**
     * Reset session tracking.
     */
    public void resetSession() {
        if (client.getLocalPlayer() != null) {
            sessionStartXp = client.getSkillExperience(Skill.MINING);
            sessionStartTime = Instant.now();
            log.info("Session tracking reset. New starting XP: {}", sessionStartXp);
        }
    }

    /**
     * Get the total XP gained during this session.
     * 
     * @return XP gained since session start
     */
    public long getSessionXpGained() {
        if (sessionStartTime == null || client.getLocalPlayer() == null) {
            return 0;
        }
        return client.getSkillExperience(Skill.MINING) - sessionStartXp;
    }

    /**
     * Get the runtime of the current session.
     * 
     * @return duration since session start
     */
    public Duration getSessionRuntime() {
        if (sessionStartTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(sessionStartTime, Instant.now());
    }

    /**
     * Calculate XP per hour for the current session.
     * 
     * @return formatted XP per hour string
     */
    public String getXpPerHour() {
        Duration runtime = getSessionRuntime();
        long xpGained = getSessionXpGained();
        
        if (runtime.isZero() || xpGained == 0) {
            return "0";
        }
        
        long seconds = runtime.getSeconds();
        double xpPerSecond = (double) xpGained / seconds;
        long xpPerHour = (long) (xpPerSecond * 3600);
        
        return String.format("%,d", xpPerHour);
    }

    /**
     * Get the session start time.
     * 
     * @return session start instant or null if not started
     */
    public Instant getSessionStartTime() {
        return sessionStartTime;
    }

    /**
     * Get the starting XP for this session.
     * 
     * @return starting XP amount
     */
    public long getSessionStartXp() {
        return sessionStartXp;
    }

    /**
     * Check if session tracking is active.
     * 
     * @return true if session is being tracked, false otherwise
     */
    public boolean isSessionActive() {
        return sessionStartTime != null;
    }
}