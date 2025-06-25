### **Project: Hybrid OSRS Mining Bot \- Development Plan**

**Project Goal:** To create a two-part Oldschool RuneScape mining bot. A Java-based Runelite plugin will act as the "brain," identifying objects and making decisions. A Python-based program will act as the "hands," executing background OS-level commands without hijacking the user's mouse.  
**Core Architecture:**

* **The Brain (Runelite Plugin \- Java):** Contains the Finite State Machine (FSM). It directly reads game data (inventory, player position, object locations) to make strategic decisions. It calculates the precise coordinates for actions and sends simple, specific commands to the Python program.  
* **The Hands (Automation Server \- Python):** A lightweight FastAPI server that listens for commands from the Brain. It is stateless and only responsible for translating commands (e.g., "click at x,y") into background OS events using pywin32. This allows the user to use their PC for other tasks while the bot is running.

### ---

**Phase 1: Environment Setup & Core Components**

This phase establishes the foundational code for both parts of the system.

* **Task 1.1: Setup Runelite Plugin Environment (Java)**  
  * **Goal:** Create a blank, runnable Runelite plugin.  
  * **Action:**  
    1. Install a Java Development Kit (JDK), version 11 is recommended.  
    2. Install IntelliJ IDEA.  
    3. Clone the official Runelite GitHub repository.  
    4. Follow the Runelite documentation to set up the project in IntelliJ, ensuring it builds and runs the OSRS client.  
    5. Create a new plugin module with the basic files: MiningBotPlugin.java, MiningBotConfig.java.  
* **Task 1.2: Setup Automation Server Environment (Python)**  
  * **Goal:** Create a basic, runnable Python server.  
  * **Action:**  
    1. Install Python 3.8+.  
    2. Create a new project directory and set up a Python virtual environment.  
    3. Install necessary libraries: pip install fastapi uvicorn pywin32.  
    4. Create a file main.py.  
* **Task 1.3: Establish Initial API Communication (Proof of Concept)**  
  * **Goal:** Verify that the Java plugin can send a simple HTTP request to the Python server.  
  * **Action (Java):** In MiningBotPlugin.java, use Java's built-in HttpClient to send a simple POST request to http://127.0.0.1:8000/status when the plugin starts.  
  * **Action (Python):** In main.py, create a basic FastAPI app with an endpoint @app.post("/status") that receives the request and prints a "Plugin connected" message to the console.

### ---

**Phase 2: The "Hands" - Python Automation Server with RemoteInput**

This phase focuses on building the low-level OS interaction capabilities using the `RemoteInput` library for true background input, replacing the previous `win32api` approach.

*   **Task 2.1: Implement Injection and Client Pairing**  
    *   **Goal:** Find the OSRS game client, inject the `RemoteInput` library, and establish a persistent connection.  
    *   **Action:** In a new `automation.py` file, create a class or singleton to manage the connection.
        1.  On startup, this manager should find the correct `java.exe` process for RuneLite. An initial implementation can simply find the first one, but a more robust solution should be considered.
        2.  Use `remote_input.EIOS.inject(process_name)` to inject the library.
        3.  Use `remote_input.EIOS.get_clients_pids(True)` and `remote_input.EIOS.pair_client_pid(pid)` to get a `client` object.
        4.  This `client` object must be stored and reused for all subsequent automation calls. It represents the stateful connection to the game.
        5.  Enable mouse and keyboard input via `client.set_mouse_input_enabled(True)` and `client.set_keyboard_input_enabled(True)`.
*   **Task 2.2: Implement Background Mouse Control**  
    *   **Goal:** Create functions to send background mouse events to the game window via `RemoteInput`.  
    *   **Action:** In `automation.py`, create functions that take the `client` object as an argument.
        1.  Create a function `move_mouse(client, x, y)` that calls `client.move_mouse(x, y)`. The coordinates are relative to the game window, not the screen.
        2.  Create a function `click(client, x, y)` that first calls `move_mouse`, then uses `client.hold_mouse(1)` followed by a short, randomized delay and `client.release_mouse(1)`. The button `1` represents a left-click.
*   **Task 2.3: Implement Background Keyboard Control**  
    *   **Goal:** Create functions for pressing and holding keyboard keys.  
    *   **Action:** In `automation.py`, create the following functions:
        1.  `key_press(client, key)`: This function will take a string like "shift", map it to the correct virtual key code, and call `client.hold_key(vk_code)` followed immediately by `client.release_key(vk_code)`.
        2.  `key_hold(client, key)`: Maps the key and calls `client.hold_key(vk_code)`.
        3.  `key_release(client, key)`: Maps the key and calls `client.release_key(vk_code)`.
        4.  A mapping of common key strings ("shift", "escape", etc.) to their corresponding Windows Virtual-Key Codes will be required.
*   **Task 2.4: Create the Final API Endpoints**  
    *   **Goal:** Expose the `RemoteInput` automation functions through the FastAPI server.  
    *   **Action:** In `main.py`, modify the endpoints to use the new `automation.py` functions.
        1.  The FastAPI app must now manage the state of the `client` connection. A global `client` object or a dependency injection system should be initialized on server startup.
        2.  Create an endpoint `POST /connect` that triggers the injection and pairing process from Task 2.1.
        3.  Update `POST /click`: Body `{ "x": int, "y": int }`. This will now call the new `automation.click` function, passing the managed `client` object.
        4.  Update the key-related endpoints (`/key_press`, `/key_hold`, `/key_release`) to call their new `automation.py` counterparts.

### ---

**Phase 3: The "Brain" \- Runelite Plugin FSM & Logic**

This phase implements the decision-making intelligence of the bot.

* **Task 3.1: Define the Finite State Machine (FSM)**  
  * **Goal:** Create the core state management structure in Java.  
  * **Action:** In the plugin's package, create a Java enum named BotState with the following values: IDLE, FINDING\_ROCK, MINING, CHECK\_INVENTORY, DROPPING, WALKING\_TO\_BANK, BANKING. Create a variable private BotState currentState; in the main plugin class.  
* **Task 3.2: Implement the Main FSM Loop**  
  * **Goal:** Create a loop that executes on a regular interval to run the FSM.  
  * **Action:** Use a @Schedule annotation on a method (e.g., runFsm()) to make it run every \~600ms. Inside this method, use a switch statement on currentState to call the appropriate logic function for the current state (e.g., case MINING: doMining(); break;).  
* **Task 3.3: Implement Object/Player Information Gathering**  
  * **Goal:** Create utility functions to get information from the game.  
  * **Action:**  
    1. Create a function isInventoryFull() that gets the inventory widget and checks if its item count is 28\.  
    2. Create a function isPlayerIdle() that checks if client.getLocalPlayer().getAnimation() \== \-1.  
    3. Create a function findNearestGameObject(int... objectIds) that queries all scene objects, filters by ID, and returns the one closest to the player.  
* **Task 3.4: Implement Coordinate Calculation**  
  * **Goal:** Create a function to get a valid, randomized click point inside a game object.  
  * **Action:** Create a helper function getRandomClickablePoint(GameObject object).  
    1. Get the object's clickbox via object.getClickbox(). This returns a java.awt.Shape.  
    2. Get the bounding rectangle of the shape via shape.getBounds().  
    3. In a loop, generate a random x and y within the bounding rectangle.  
    4. Use shape.contains(x, y) to check if the random point is within the actual shape.  
    5. Return the first valid point found. This ensures the click is always valid and not on a transparent corner of the bounding box.  
* **Task 3.5: Implement FSM State Logic**  
  * **Goal:** Implement the specific logic for each state.  
  * **Action:**  
    * **doFindingRock():** Call findNearestGameObject() for rock IDs. If a rock is found, get its coordinates using getRandomClickablePoint(), send a /click command to the Python API, and transition state to MINING.  
    * **doMining():** Check isPlayerIdle(). If the player has been idle for a few cycles, it means the rock is depleted or they were interrupted. Transition to CHECK\_INVENTORY.  
    * **doCheckInventory():** Call isInventoryFull(). If true, transition to DROPPING (or WALKING\_TO\_BANK). If false, transition to FINDING\_ROCK.  
    * **doDropping():** Send a /key\_hold command for "shift" to Python. Loop through the inventory items. For each ore, get its screen coordinates and send a /click command. Finally, send a /key\_release command. Transition to FINDING\_ROCK.  
    * *(Banking logic can be implemented similarly by finding bank booth objects and interacting with widgets.)*

### ---

**Phase 4: Integration, Configuration, and Refinement**

This phase brings the two components together and adds user-facing controls.

* **Task 4.1: Create Plugin Configuration Panel**  
  * **Goal:** Allow the user to configure the bot's behavior.  
  * **Action:** In MiningBotConfig.java, add configuration items (@ConfigItem).  
    * A boolean toggle to start/stop the bot (the FSM will only run if this is true).  
    * A dropdown to select the mining mode (POWER\_MINE\_DROP or MINE\_AND\_BANK).  
    * A text field for the specific ore/rock IDs to mine.  
* **Task 4.2: Add Human-Like Delays (Anti-Detection)**  
  * **Goal:** Avoid robotic, predictable timing.  
  * **Action:** In the Runelite plugin's FSM logic, before sending any command to Python, add a randomized delay. Do not use Thread.sleep(). Instead, use a counter in the scheduled loop to wait a random number of cycles before acting. The delays should vary based on the action being performed.  
* **Task 4.3: Full End-to-End Testing**  
  * **Goal:** Ensure the entire system works together as intended.  
  * **Action:** Run both the Runelite client with the plugin and the Python FastAPI server.  
    1. Start the bot from the config panel.  
    2. Verify that the plugin correctly identifies rocks and sends commands.  
    3. Verify that the Python server receives the commands and that the game client receives background clicks on the correct coordinates.  
    4. Test the full inventory and dropping sequence.  
    5. Monitor console logs on both applications for errors.

### ---

**Phase 5: Architectural Refactoring: The Task System**

**Rationale:** The current FSM is effective for a single purpose (mining) but is not scalable. To support different activities (like banking or other skills) and allow them to run in sequence or be triggered conditionally (e.g., "do a birdhouse run every hour"), we must refactor to a more generic, modular system. This phase replaces the monolithic FSM with a stack-based Task Manager.

*   **Task 5.1: Define the Core Task Abstraction**
    *   **Goal:** Create a blueprint for all future bot activities.
    *   **Action:** Create a new Java interface named `BotTask`. It should define the core lifecycle methods:
        *   `onStart()`: Called when the task begins.
        *   `onLoop()`: The main logic loop, called by the manager on every tick.
        *   `onStop()`: Called for cleanup when the task is finished or interrupted.
        *   `isFinished()`: Returns a boolean indicating if the task has completed its objective.
        *   `getTaskName()`: Returns a string name for the task for logging/UI purposes.
*   **Task 5.2: Implement the Task Manager**
    *   **Goal:** Create a central controller to manage the execution of tasks.
    *   **Action:** Create a `TaskManager.java` class.
        1.  It will manage a `Stack<BotTask>`.
        2.  The main plugin loop (`@Schedule`) will now simply call `taskManager.onLoop()`.
        3.  The manager's `onLoop` method will check the top of the stack. If it's not empty, it calls that task's `onLoop()`. If the task `isFinished()`, it's popped from the stack, and the next task (if any) is started.
        4.  It will have methods like `pushTask(BotTask task)`, `clearTasks()`, and `getCurrentTask()`.
*   **Task 5.3: Refactor Mining into a Task**
    *   **Goal:** Convert the existing mining logic into our first `BotTask`.
    *   **Action:**
        1.  Create a new class `MiningTask implements BotTask`.
        2.  Move the `BotState` enum (IDLE, FINDING_ROCK, etc.) *inside* the `MiningTask` class. It will now be the internal state for this task only.
        3.  Move all the `doFindingRock()`, `doMining()`, etc., logic into the `MiningTask`. The `onLoop` method of `MiningTask` will contain the `switch` statement that drives its internal FSM.
        4.  The task's `isFinished()` method can be controlled by the plugin's main start/stop toggle. It will effectively never finish on its own, but can be cleared by the manager.
        5.  The main plugin's "start" button will now simply do `taskManager.pushTask(new MiningTask(...))`.

### ---

**Phase 6: Feature Expansion: Banking and Pathfinding**

**Rationale:** With the new task architecture in place, we can create complex sequences of actions by creating small, reusable tasks and pushing them onto the stack. This phase implements a full "mine and bank" loop.

*   **Task 6.1: Implement a Reusable Walking Task**
    *   **Goal:** Create a task that can walk a player from point A to point B.
    *   **Action:** Create a `WalkTask implements BotTask`.
        1.  It will take a `WorldPoint` destination in its constructor.
        2.  Its `onLoop()` method will check the player's current location. If not at the destination, it will issue a walk command via the API. RuneLite's `Walker` API could be investigated for this.
        3.  `isFinished()` will return true when the player is within a certain threshold of the destination.
        4.  For now, this task will not perform complex pathfinding; it will assume a direct, walkable path.
*   **Task 6.2: Implement a Reusable Banking Task**
    *   **Goal:** Create a task that opens a bank and deposits items.
    *   **Action:** Create a `BankTask implements BotTask`.
        1.  Its `onLoop()` logic will find the nearest bank booth/chest, click it, wait for the bank interface to open, and then click to deposit all items (or specific items passed in its constructor).
        2.  `isFinished()` will be true once the inventory is empty.
*   **Task 6.3: Update MiningTask to Use Banking**
    *   **Goal:** Modify the main mining task to use the new walking and banking tasks.
    *   **Action:**
        1.  In `MiningBotConfig`, change the dropdown to select between `POWER_MINE` and `BANK`.
        2.  In `MiningTask`, when the inventory is full and the mode is `BANK`:
        3.  Instead of transitioning to the `DROPPING` state, the `MiningTask` will now do the following:
            *   `taskManager.pushTask(new WalkTask(BANK_LOCATION));`
            *   `taskManager.pushTask(new BankTask());`
            *   `taskManager.pushTask(new WalkTask(MINE_LOCATION));`
        4.  Crucially, the `MiningTask` itself does *not* finish. When the `TaskManager` is done with the walking/banking sequence, it will pop them all off and resume the `MiningTask` right where it left off. The `MiningTask` simply needs to know not to do anything while other tasks are running on top of it.

### ---

**Phase 7: Modular Activity: Birdhouse Runs**

**Rationale:** This feature demonstrates the full power and modularity of the Task System by implementing a completely separate, complex activity that can be run independently or interleaved with other tasks.

*   **Task 7.1: Create the Master Birdhouse Run Task**
    *   **Goal:** Create the main task that manages a full birdhouse run.
    *   **Action:** Create `BirdhouseRunTask implements BotTask`.
        1.  This task will have its own complex internal FSM (e.g., `TELEPORTING_TO_VERDANT_VALLEY`, `WALKING_TO_FIRST_HOUSE`, `SERVICING_HOUSE`, `WALKING_TO_SECOND_HOUSE`, etc.).
        2.  When started, it will push a sequence of sub-tasks onto the `TaskManager`, such as `WalkTask`, `TeleportTask`, and a new `InteractWithObjectTask`.
*   **Task 7.2: Implement Required Sub-Tasks**
    *   **Goal:** Build the smaller, reusable actions needed for the run.
    *   **Action:**
        1.  Create an `InteractWithObjectTask` that takes an object ID and an interaction string ("Build", "Harvest", etc.).
        2.  Create an `InventoryCheckTask` to ensure the player has the required items (seeds, logs, clockwork) before starting.
*   **Task 7.3: Add UI Triggers**
    *   **Goal:** Allow the user to start a birdhouse run.
    *   **Action:** In the `MiningBotPanel`, add:
        1.  A button "Start Birdhouse Run Now". This will push a `BirdhouseRunTask` onto the manager stack.
        2.  A configuration toggle and timer to automatically run it every ~50 minutes. The main plugin class will check this timer and push the task when appropriate.

### ---

**Phase 8: Long-Term Viability: Humanization and Error Recovery**

**Rationale:** A bot's ability to run for extended periods without getting stuck or being detected is paramount. This is an ongoing effort to make the bot more human-like and robust.

*   **Task 8.1: Advanced Action Timing**
    *   **Goal:** Move beyond simple linear random delays.
    *   **Action:** Create a `Timing` utility class that generates delays based on a Gaussian (normal) distribution. This more accurately mimics human reaction times. All `Thread.sleep` or cycle-based waits should be replaced with calls to this utility.
*   **Task 8.2: Implement "Afk" Actions**
    *   **Goal:** Simulate a player's natural tendency to get distracted.
    *   **Action:** Create a low-priority `AfkTask`. The `TaskManager` can be configured to occasionally push this task onto the stack. The `AfkTask` might perform random actions like:
        *   Moving the camera.
        *   Checking a random skill in the stats tab.
        *   Moving the mouse around the screen without clicking.
        *   Doing nothing for a variable period of time.
*   **Task 8.3: Basic Error Recovery**
    *   **Goal:** Handle common failure scenarios gracefully.
    *   **Action:** In the `TaskManager`, add a timeout to each task. If a task (e.g., `WalkTask`) runs for too long (implying it's stuck), the manager will clear the entire task stack and could, for example, attempt a "recovery" by using a teleport to a known safe location (like Lumbridge) and then stopping the bot. This prevents the bot from being stuck trying to click on something for hours.