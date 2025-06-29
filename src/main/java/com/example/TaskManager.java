package com.example;

import java.util.Stack;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the execution of BotTasks in a stack-based manner.
 * This allows for sequential and nested task execution, enabling complex behaviors.
 */
@Slf4j
public class TaskManager {
    private final Stack<BotTask> tasks = new Stack<>();

    /**
     * The main loop for the task manager, called on every game tick.
     * It processes the current task on top of the stack.
     */
    public void onLoop() {
        if (tasks.isEmpty()) {
            return;
        }

        BotTask currentTask = tasks.peek();

        // If the current task is finished, pop it and start the next one.
        if (currentTask.isFinished()) {
            currentTask.onStop();
            tasks.pop();

            // If there's a new task on the stack, start it.
            if (!tasks.isEmpty()) {
                tasks.peek().onStart();
            }
            return; // Return to process the new task on the next tick
        }

        // Run the main logic for the current task.
        currentTask.onLoop();
    }

    /**
     * Pushes a new task onto the stack.
     * If the stack was empty, the new task is started immediately.
     * Otherwise, the current task is paused and the new one begins.
     *
     * @param task The task to add to the top of the stack.
     */
    public void pushTask(BotTask task) {
        tasks.push(task);
    }

    /**
     * Clears the entire task stack, stopping any current task.
     */
    public void clearTasks() {
        if (!tasks.isEmpty()) {
            tasks.peek().onStop();
            tasks.clear();
        }
    }

    /**
     * Gets the currently active task.
     *
     * @return The task at the top of the stack, or null if the stack is empty.
     */
    public BotTask getCurrentTask() {
        return tasks.isEmpty() ? null : tasks.peek();
    }
} 