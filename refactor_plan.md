### **Project: Hybrid OSRS Bot - Refactoring Plan**

**Project Goal:** To refactor the existing codebase to improve architectural soundness, reduce code duplication, and increase scalability before adding new features. This plan replaces the original development plan.

---

### **Phase 1: Core Architectural Refactoring**

**Rationale:** The current architecture suffers from tight coupling, duplicated logic across tasks, and a flawed concurrency model. This phase introduces core services and a new base class to create a robust, message-driven foundation.

*   **Task 1.1: Create the `ServiceLocator` and Core Services**
    *   **Goal:** Establish a centralized dependency provider to decouple components.
    *   **Action:**
        1.  Create a new package `com.example.services`.
        2.  Create a `ServiceLocator` class to hold and provide singleton instances of all services.
        3.  Create an `ActionService` class. Move reusable logic like `findNearestGameObject`, `findItemInInventory`, and `getRandomClickablePoint` into this service.
        4.  Create a `HumanizerService` class. This service will provide methods to generate human-like delays (e.g., `getShortDelay()`, `getMediumDelay()`), replacing all manual `delayTicks` and `Math.random` implementations. The delays should use a Gaussian distribution instead of basic random numbers.
        5.  Create a simple `EventService` (or `EventBus`). This service will have `subscribe` and `publish` methods to manage game events.
*   **Task 1.2: Refactor `BotTask` to an Abstract Class**
    *   **Goal:** Eliminate redundant code in task implementations by providing base functionality.
    *   **Action:**
        1.  Change `BotTask.java` from an `interface` to an `abstract class`.
        2.  Add a protected `tickDelay` counter.
        3.  Implement a public `delay(int ticks)` method that sets this counter.
        4.  Implement a final `onLoop()` method. This method will check the `tickDelay`. If it's greater than zero, it decrements the counter. If it's zero, it calls a new abstract method `protected abstract void onTick()`.
        5.  All existing tasks will need to be updated to `extend BotTask` and implement `onTick()` instead of `onLoop()`.
*   **Task 1.3: Refactor the Main Plugin (`AndromedaPlugin`)**
    *   **Goal:** Adapt the main plugin to use the new service-oriented architecture.
    *   **Action:**
        1.  In the `startUp()` method, instantiate the `ServiceLocator` and all core services.
        2.  Inject services into the `TaskManager` and other components as needed.
        3.  Change all RuneLite `@Subscribe` methods (e.g., `onGameTick`, `onAnimationChanged`) to simply publish the event to the `EventService`. For example: `eventService.publish(gameTickEvent)`.

---

### **Phase 2: Task Implementation Refactoring**

**Rationale:** With the new architecture in place, all existing tasks must be updated to conform to the new patterns, ensuring they are decoupled, lean, and use the centralized services.

*   **Task 2.1: Refactor `CombatTask`**
    *   **Goal:** Update the most complex task to serve as a template for the others.
    *   **Action:**
        1.  Change `CombatTask` to `extends BotTask`.
        2.  Replace the `onLoop` implementation with `onTick`.
        3.  Remove all local `ScheduledExecutorService`, `delayTicks`, and random delay generation.
        4.  Use `delay(humanizerService.getShortDelay())` for all waits.
        5.  Replace direct `plugin.getClient()` calls with calls to the `ActionService` (e.g., `actionService.findNearestNpc(...)`).
        6.  Remove the `onAnimationChanged` and `onInteractingChanged` methods. Instead, subscribe to these events from the `EventService` within the task's `onStart()` method.
*   **Task 2.2: Refactor `MiningTask`**
    *   **Goal:** Apply the new architecture to the mining logic.
    *   **Action:**
        1.  Apply the same refactoring steps as in Task 2.1.
        2.  Ensure that all object finding, inventory checks, and delays are handled by the appropriate services.
*   **Task 2.3: Refactor `WalkTask` and `BankTask`**
    *   **Goal:** Update the utility tasks to the new standard.
    *   **Action:**
        1.  Apply the same refactoring steps as in Task 2.1.
        2.  These tasks should become much simpler, primarily relying on the `ActionService` and the base `BotTask`'s delay mechanism.

---

### **Phase 3: Validation and Future Planning**

*   **Task 3.1: Full End-to-End Testing**
    *   **Goal:** Ensure the refactored system is fully functional for all bot types (Combat, Mining).
    *   **Action:**
        1.  Run the bot in Combat mode and verify the entire loop (finding, attacking, eating, looting) works correctly.
        2.  Run the bot in Mining (and Banking) mode and verify its loop is correct.
        3.  Monitor logs for any errors or unexpected behavior.
*   **Task 3.2: Plan for Phase 8 (Humanization and Error Recovery)**
    *   **Goal:** Re-evaluate the original Phase 8 tasks in the context of the new architecture.
    *   **Action:** Review the original plan for "Advanced Action Timing", "AFK Actions", and "Error Recovery". The new `HumanizerService` and `TaskManager` provide a much better foundation for implementing these features. Create a new, more detailed plan for these features.