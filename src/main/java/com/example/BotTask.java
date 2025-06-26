package com.example;

/**
 * Defines the contract for a modular, executable task that the bot can perform.
 * This interface allows for the creation of complex, stateful actions that can be
 * managed and sequenced by a TaskManager.
 */
public interface BotTask {
    /**
     * Called once when the task is started by the TaskManager.
     * Use this for any initial setup.
     */
    void onStart();

    /**
     * The main logic loop for the task, called on every tick by the TaskManager.
     */
    void onLoop();

    /**
     * Called once when the task is stopped, either because it finished or was
     * interrupted. Use this for any necessary cleanup.
     */
    void onStop();

    /**
     * Determines if the task has completed its objective.
     *
     * @return true if the task is finished and should be popped from the stack,
     *         false otherwise.
     */
    boolean isFinished();

    /**
     * Provides a human-readable name for the task.
     *
     * @return The name of the task for logging or UI purposes.
     */
    String getTaskName();
} 