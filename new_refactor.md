### **Project Runepal: Architectural Refactor Plan**

**Objective:** To refactor the existing codebase to improve modularity, reduce coupling, and establish a more scalable and maintainable architecture before implementing new features. This plan is based on the initial architectural analysis.

---

### **Phase 1: Decouple Core Services from `RunepalPlugin`**

**Rationale:** The main `RunepalPlugin` class has become a "God Object," handling too many responsibilities (game state querying, external communication, utility functions). This phase will extract these responsibilities into dedicated, injectable service classes.

*   **Task 1.1: Create and Populate `GameService`**
    *   **Action:** Create a new file `src/main/java/com/example/GameService.java`.
    *   **Details:**
        1.  The class will be a `@Singleton` managed by Guice.
        2.  It will `@Inject` the RuneLite `Client`.
        3.  Move all methods that *query* or *read* game state from `RunepalPlugin` into this service. This includes:
            *   `isInventoryFull()`
            *   `isInventoryEmpty()`
            *   `isPlayerIdle()`
            *   `findNearestGameObject(int... ids)`
            *   `getInventoryItemPoint(int slot)`
            *   `getRandomClickablePoint(GameObject gameObject)`
        4.  Move the NPC-finding and interaction logic from `CombatTask` here to centralize it:
            *   `findNearestNpc()`
            *   `getRandomClickablePoint(NPC npc)`
    *   **Goal:** Any class that needs to know something about the game state will depend on `GameService`, not `RunepalPlugin`.

*   **Task 1.2: Refactor `ActionService` for External Communication**
    *   **Action:** Modify the existing `ActionService.java`.
    *   **Details:**
        1.  Make `ActionService` a Guice-managed `@Singleton`.
        2.  It will be the *sole* component responsible for sending commands to the Python server.
        3.  Move all methods that *write* or *send* actions from `RunepalPlugin` into this service:
            *   `sendClickRequest(...)`
            *   `sendMouseMoveRequest(...)`
            *   `sendKeyRequest(...)`
        4.  The `ActionService` will depend on the `PipeService` for the actual HTTP communication.
    *   **Goal:** Any class that needs to perform an action (like clicking or typing) will depend on `ActionService`. This completely decouples tasks from the underlying communication mechanism.

*   **Task 1.3: Refactor `PipeService` and Connection Logic**
    *   **Action:** Update `PipeService.java` and the connection logic in `RunepalPlugin`.
    *   **Details:**
        1.  Refactor `PipeService` to be an injectable `@Singleton`. Remove its dependency on `RunepalPlugin`.
        2.  The `RunepalPlugin` will no longer create the `PipeService` directly. Instead, it will `@Inject` it.
        3.  The "Connect" button in the UI will now call a method on the injected `PipeService` instance (e.g., `pipeService.connect()`).
    *   **Goal:** The `PipeService` becomes a self-contained, injectable service responsible only for managing the connection state and sending raw messages.

*   **Task 1.4: Update Tasks to Use Services**
    *   **Action:** Refactor `MiningTask`, `CombatTask`, `WalkTask`, etc.
    *   **Details:**
        1.  Change the constructors of all tasks to accept the new `GameService` and `ActionService` instead of the `RunepalPlugin`.
        2.  Update all method calls within the tasks to use the new services (e.g., `plugin.isInventoryFull()` becomes `gameService.isInventoryFull()`).
    *   **Goal:** Tasks are now fully decoupled from the main plugin class, improving modularity and making them easier to test and reason about.

---

### **Phase 2: Introduce a Shared `Entity` Abstraction**

**Rationale:** There is duplicated code for finding and interacting with different entity types (GameObjects, NPCs). This phase will unify this logic using a common interface.

*   **Task 2.1: Define `Interactable` Interface**
    *   **Action:** Create a new interface `src/main/java/com/example/entity/Interactable.java`.
    *   **Details:** Define common methods like `getName()`, `getWorldLocation()`, `getClickbox()`.
*   **Task 2.2: Create Adapter Classes**
    *   **Action:** Create `GameObjectEntity` and `NpcEntity` classes that wrap the RuneLite `GameObject` and `NPC` types and implement the `Interactable` interface.
*   **Task 2.3: Generalize `GameService`**
    *   **Action:** Refactor `GameService` to use the `Interactable` interface.
    *   **Details:** Replace `findNearestGameObject` and `findNearestNpc` with a single, generic method: `findNearest(Predicate<Interactable> predicate)`. Replace the separate `getRandomClickablePoint` methods with a single one that accepts an `Interactable`.
*   **Goal:** Drastically reduce code duplication and make it trivial to add support for new types of interactable entities in the future.

---

### **Phase 3: Refine Task Management and Reusability**

**Rationale:** The current `TaskManager` is robust but can be improved. Complex tasks like `CombatTask` contain logic for sub-actions (like eating) that could be extracted and reused.

*   **Task 3.1: Create Reusable Sub-Tasks**
    *   **Action:** Create new, small, single-purpose tasks.
    *   **Details:** Implement `EatFoodTask` and `LootItemsTask`. These tasks will contain only the logic for their specific action.
*   **Task 3.2: Refactor `CombatTask` to Orchestrate**
    *   **Action:** Modify `CombatTask.java`.
    *   **Details:** Remove the `EATING` and `LOOTING` states from its internal FSM. Instead, when it needs to eat, it will `taskManager.pushTask(new EatFoodTask())`. The `CombatTask` will become a simpler orchestrator that pushes other, more specific tasks onto the stack.
*   **Goal:** Create a library of composable, reusable bot actions, making it easier to build complex behaviors by assembling simple, well-tested parts.
