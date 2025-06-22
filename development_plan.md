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

**Phase 2: The "Hands" \- Python Automation Server**

This phase focuses on building the low-level OS interaction capabilities.

* **Task 2.1: Implement Window Handle Discovery**  
  * **Goal:** Create a function to find the OSRS game window.  
  * **Action:** In a new automation.py file, write a function get\_game\_window\_hwnd() that uses win32gui.FindWindow(None, "Old School RuneScape") to find and return the window handle (hwnd). Include error handling for when the window is not found.  
* **Task 2.2: Implement Background Mouse Click**  
  * **Goal:** Create a function to send a background mouse click to the game window.  
  * **Action:** In automation.py, create a function background\_click(hwnd, x, y).  
    1. It must take the window handle, x, and y coordinates as input.  
    2. Use win32api.MAKELONG(x, y) to pack coordinates into the lParam.  
    3. Use win32gui.PostMessage to send WM\_LBUTTONDOWN and WM\_LBUTTONUP messages to the provided hwnd. Add a small, randomized delay (e.g., 30-70ms) between down and up messages.  
* **Task 2.3: Implement Background Mouse Movement**  
  * **Goal:** Create a function that simulates human-like mouse movement in the background.  
  * **Action:** In automation.py, create a function background\_move(hwnd, x, y). This is a non-trivial task.  
    1. It should accept the target x and y coordinates.  
    2. The function should generate a series of intermediate points between the current (unknown) position and the target, following a non-linear path (e.g., a bezier curve).  
    3. For each intermediate point, it will send a WM\_MOUSEMOVE message using win32gui.PostMessage.  
    4. Introduce small, randomized delays between each WM\_MOUSEMOVE message to control the speed of the movement.  
* **Task 2.4: Implement Background Key Press**  
  * **Goal:** Create functions for pressing keyboard keys (specifically for dropping items).  
  * **Action:** In automation.py, create background\_key\_press(hwnd, key) and background\_key\_hold(hwnd, key) / background\_key\_release(hwnd, key) functions. These will use win32gui.PostMessage to send WM\_KEYDOWN and WM\_KEYUP messages with the appropriate virtual key code (e.g., VK\_SHIFT).  
* **Task 2.5: Create the Final API Endpoints**  
  * **Goal:** Expose the automation functions through the FastAPI server.  
  * **Action:** In main.py, create the following endpoints that call the functions from automation.py. Use Pydantic models for request body validation.  
    * POST /click: Body { "x": int, "y": int }. Combines a move and a click for simplicity.  
    * POST /key\_press: Body { "key": str } (e.g., "shift").  
    * POST /key\_hold: Body { "key": str }.  
    * POST /key\_release: Body { "key": str }.

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