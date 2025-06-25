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