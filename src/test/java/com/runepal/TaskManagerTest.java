package com.runepal;

import org.junit.Assert;
import org.junit.Test;

public class TaskManagerTest {

    private static class RecordingTask implements BotTask {
        private final String taskName;
        private int stopCount;

        private RecordingTask(String taskName) {
            this.taskName = taskName;
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onLoop() {
        }

        @Override
        public void onStop() {
            stopCount++;
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public String getTaskName() {
            return taskName;
        }
    }

    @Test
    public void clearTasksStopsAllTasksInStack() {
        TaskManager taskManager = new TaskManager();
        RecordingTask taskA = new RecordingTask("A");
        RecordingTask taskB = new RecordingTask("B");
        RecordingTask taskC = new RecordingTask("C");

        taskManager.pushTask(taskA);
        taskManager.pushTask(taskB);
        taskManager.pushTask(taskC);

        taskManager.clearTasks();

        Assert.assertEquals(1, taskA.stopCount);
        Assert.assertEquals(1, taskB.stopCount);
        Assert.assertEquals(1, taskC.stopCount);
        Assert.assertNull(taskManager.getCurrentTask());
    }
}
