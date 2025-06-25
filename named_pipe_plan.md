### **Project Refactor Plan: Migrating from FastAPI to Named Pipes**

**Project Goal:** To replace the existing HTTP-based communication (FastAPI) between the Java RuneLite plugin and the Python automation server with a high-performance, low-latency Inter-Process Communication (IPC) mechanism using Windows named pipes.

**Core Rationale:** The current REST API, while functional, introduces unnecessary overhead (TCP/IP stack, HTTP parsing, JSON serialization) for local, high-frequency communication. Named pipes provide a direct, kernel-managed communication channel that is significantly faster and more resource-efficient, which is critical for a responsive bot.

---

### **Core Architecture: The Named Pipe Bridge**

The new architecture will consist of two main components communicating over a single, persistent named pipe.

*   **The Listener (Python Server):**
    *   A Python script will create and listen on a Windows named pipe (e.g., `\\.\pipe\OSRSBot`).
    *   It will run in a loop, blocking until the Java client connects.
    *   Once connected, it will continuously read data from the pipe.
    *   It is responsible for parsing messages and dispatching them to the appropriate `automation.py` functions.

*   **The Commander (Java Plugin):**
    *   The RuneLite plugin will act as the client.
    *   On startup, it will connect to the named pipe by opening it like a file.
    *   It will send commands by writing simple, structured messages to the pipe.
    *   Communication is one-way (Java -> Python). Java sends a command and does not wait for a response.

*   **Message Protocol:**
    *   To ensure simplicity and structure, messages will be **single-line JSON objects**.
    *   Each JSON message **must** be terminated by a newline character (`\n`). This is the delimiter that the Python server will use to separate one command from the next.
    *   **Example Command:** `{"action": "click", "x": 550, "y": 320}\n`

---

### **Phase 1: Python Server Refactoring**

**Goal:** Replace the FastAPI web server with a dedicated named pipe listener.

*   **Task 1.1: Implement the Named Pipe Server Loop**
    *   **File:** `main.py`
    *   **Action:**
        1.  Remove all `fastapi` and `uvicorn` imports and application setup.
        2.  Import `win32pipe`, `win32file`, and `pywintypes`.
        3.  In the main execution block, create a class or main loop to manage the pipe.
        4.  Define a pipe name: `PIPE_NAME = r'\\.\pipe\OSRSBot'`.
        5.  Use `win32pipe.CreateNamedPipe()` to create the pipe.
        6.  Create a primary `while True:` loop for the server's lifetime.
        7.  Inside the loop, call `win32pipe.ConnectNamedPipe(pipe)`. This call will block until the Java client connects.
        8.  After a client connects, enter a nested `while True:` loop to read messages.

*   **Task 1.2: Implement Message Reading and Parsing**
    *   **File:** `main.py`
    *   **Action:**
        1.  Inside the inner "connected" loop, use `win32file.ReadFile(pipe, buffer_size)` to read data.
        2.  This call can return partial messages. It is crucial to accumulate the results in a buffer and process it line by line, splitting on the `\n` delimiter.
        3.  When a full line (a complete JSON string) is received, use Python's `json.loads()` to parse it into a dictionary.
        4.  Handle `pywintypes.error` exceptions. Specifically, `ERROR_BROKEN_PIPE` indicates the client has disconnected. When this happens, break the inner loop, close the current pipe handle with `win32file.CloseHandle(pipe)`, and allow the outer loop to create a new pipe and wait for a new connection.

*   **Task 1.3: Create a Command Dispatcher**
    *   **File:** `main.py`
    *   **Action:**
        1.  The `automation.py` file (with its `client` object for `RemoteInput`) should still be initialized once when the server starts.
        2.  After parsing a JSON message into a dictionary, inspect the `action` key.
        3.  Use an `if/elif/else` block or a dictionary mapping to call the correct function from `automation.py`.
        4.  Pass the parameters from the parsed dictionary to the function (e.g., `automation.click(client, x=data['x'], y=data['y'])`).

---

### **Phase 2: Java Client Refactoring**

**Goal:** Replace all HTTP request logic with code that writes to the named pipe.

*   **Task 2.1: Implement a Pipe Communication Service**
    *   **File:** Create a new `PipeService.java` or add logic to an existing service class.
    *   **Action:**
        1.  Remove all dependencies and code related to `java.net.http.HttpClient`.
        2.  Add a `private PrintWriter pipeWriter;` field.
        3.  Create a `connect()` method:
            *   It will take the pipe name (`\\.\pipe\OSRSBot`) as an argument.
            *   It will attempt to instantiate the writer: `pipeWriter = new PrintWriter(new FileWriter(pipeName), true);`. The `true` argument enables auto-flushing, which is important for real-time commands.
            *   This method should be wrapped in a `try/catch` block to handle `IOException`, which will be thrown if the Python server is not running. Log errors appropriately.
        4.  Create a `disconnect()` method that closes the `pipeWriter` if it's not null.
        5.  Create a `sendCommand(String jsonCommand)` method that writes the command string followed by a newline: `pipeWriter.println(jsonCommand);`.

*   **Task 2.2: Integrate Pipe Service into the Plugin**
    *   **File:** `MiningBotPlugin.java`
    *   **Action:**
        1.  Instantiate your `PipeService`.
        2.  In the plugin's `startUp()` method, call `pipeService.connect()`.
        3.  In the plugin's `shutDown()` method, call `pipeService.disconnect()`.
        4.  Add a status indicator to the plugin panel to show whether the connection to the pipe is active.

*   **Task 2.3: Refactor Action-Sending Logic**
    *   **File:** `MiningTask.java` (and other future tasks).
    *   **Action:**
        1.  Wherever an HTTP request was previously sent, now construct a JSON string. Using a library like `Gson` is recommended, but manual string building is also possible for simple objects.
            *   **Example:** `String command = String.format("{\"action\": \"click\", \"x\": %d, \"y\": %d}", x, y);`
        2.  Call `pipeService.sendCommand(command)` to send the action to the Python server.

---

### **Phase 3: Testing and Validation**

**Goal:** Ensure the new IPC system is robust and reliable.

*   **Task 3.1: Define the Correct Startup Procedure**
    1.  The user must first run the Python server (`python main.py`). The console should indicate it is waiting for a connection.
    2.  The user then starts the RuneLite client. The plugin should automatically connect, and the Python server's console should print a "Client connected" message.

*   **Task 3.2: Full End-to-End Test**
    *   Run the bot's mining loop.
    *   Verify that clicks and key presses are being executed correctly in the game client.
    *   Check the Python console for any JSON parsing errors or other exceptions.

*   **Task 3.3: Test Connection Resiliency**
    *   **Scenario 1: Kill Python Server:** With the plugin running, terminate the `main.py` process. The next time the Java plugin tries to send a command, it should catch an `IOException`. The plugin should handle this gracefully (e.g., stop the bot, show a "Disconnected" status) without crashing.
    *   **Scenario 2: Restart Python Server:** After killing the server, restart it. The Java plugin should have a mechanism (e.g., a "Reconnect" button in the panel, or an automatic periodic retry) to re-establish the connection by calling its `connect()` method again.
    *   **Scenario 3: Close RuneLite Client:** When the user closes RuneLite, the `shutDown()` method should close the pipe connection. The Python server should detect the broken pipe, log "Client disconnected," and loop back to waiting for a new connection. 