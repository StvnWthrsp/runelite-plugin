package com.runepal.services;

import com.runepal.remoteinput.RemoteInput;
import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-level service for interacting with the RemoteInput native library.
 * This service handles:
 * - Injecting RemoteInput into the target Java process
 * - Pairing with the injected client
 * - Providing clean APIs for mouse and keyboard operations
 * - Managing the connection lifecycle
 * 
 * All mouse and keyboard input in the application should route through this
 * service.
 */
@Slf4j
public class RemoteInputService {

    private static final int MOUSE_BUTTON_LEFT = 1;
    private static final int MOUSE_BUTTON_MIDDLE = 2;
    private static final int MOUSE_BUTTON_RIGHT = 3;

    private static final int INJECTION_WAIT_MS = 1500;
    private static final int MAX_INJECTION_RETRIES = 3;
    private static final int RETRY_WAIT_MS = 500;

    private Pointer eios;

    @Getter
    private int pid;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * Inject RemoteInput into the target process and establish a connection.
     * This method will:
     * 1. First try injecting into the current process (most reliable)
     * 2. Wait for injection to complete
     * 3. Find and pair with an unpaired client
     * 
     * @return true if injection and pairing succeeded, false otherwise
     */
    public boolean injectAndConnect() {
        // Since this plugin runs inside RuneLite, inject into our own process
        int currentPid = (int) ProcessHandle.current().pid();
        log.info("Attempting to inject RemoteInput into current process PID: {}", currentPid);

        // Try injecting into the current process first
        if (injectAndConnectByPid(currentPid)) {
            return true;
        }

        // Fallback: try by process name (covers edge cases)
        log.info("Direct PID injection failed, trying by process name...");

        // Try javaw.exe first (used by RuneLite on Windows)
        if (injectAndConnectByName("javaw.exe")) {
            return true;
        }

        // Try java.exe as last resort
        return injectAndConnectByName("java.exe");
    }

    /**
     * Inject RemoteInput into a specific process by PID and establish a connection.
     * 
     * @param targetPid Process ID to inject into
     * @return true if injection and pairing succeeded, false otherwise
     */
    public boolean injectAndConnectByPid(int targetPid) {
        log.info("Injecting RemoteInput into process PID: {}", targetPid);

        try {
            // Perform injection by PID
            RemoteInput.INSTANCE.EIOS_Inject_PID(targetPid);

            // Wait for injection to complete
            Thread.sleep(INJECTION_WAIT_MS);

            // Try to pair with this specific PID
            for (int retry = 0; retry < MAX_INJECTION_RETRIES; retry++) {
                log.debug("Attempting to pair with PID {} (attempt {}/{})", targetPid, retry + 1,
                        MAX_INJECTION_RETRIES);

                this.eios = RemoteInput.INSTANCE.EIOS_PairClient(targetPid);

                if (this.eios != null) {
                    this.pid = targetPid;
                    this.connected.set(true);
                    log.info("Successfully paired with RemoteInput client PID: {}", targetPid);
                    return true;
                }

                if (retry < MAX_INJECTION_RETRIES - 1) {
                    Thread.sleep(RETRY_WAIT_MS);
                }
            }

            log.warn("Failed to pair with PID {} after {} retries", targetPid, MAX_INJECTION_RETRIES);
            return false;

        } catch (Exception e) {
            log.error("Error during RemoteInput injection by PID: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Inject RemoteInput into a specific process name and establish a connection.
     * 
     * @param processName Name of the process to inject into
     * @return true if injection and pairing succeeded, false otherwise
     */
    public boolean injectAndConnectByName(String processName) {
        log.info("Injecting RemoteInput into process: {}", processName);

        try {
            // Perform injection
            RemoteInput.INSTANCE.EIOS_Inject(processName);

            // Wait for injection to complete
            Thread.sleep(INJECTION_WAIT_MS);

            // Try to find and pair with a client
            for (int retry = 0; retry < MAX_INJECTION_RETRIES; retry++) {
                List<Integer> pids = getClientPids(true);

                if (!pids.isEmpty()) {
                    // Try to pair with the first available client
                    int targetPid = pids.get(0);
                    log.info("Found unpaired client with PID: {}", targetPid);

                    this.eios = RemoteInput.INSTANCE.EIOS_PairClient(targetPid);

                    if (this.eios != null) {
                        this.pid = targetPid;
                        this.connected.set(true);
                        log.info("Successfully paired with RemoteInput client PID: {}", targetPid);
                        return true;
                    } else {
                        log.warn("Failed to pair with client PID: {}", targetPid);
                    }
                }

                if (retry < MAX_INJECTION_RETRIES - 1) {
                    log.debug("No unpaired clients found, retrying in {}ms (attempt {}/{})",
                            RETRY_WAIT_MS, retry + 1, MAX_INJECTION_RETRIES);
                    Thread.sleep(RETRY_WAIT_MS);
                }
            }

            log.warn("Failed to find or pair with any RemoteInput client for process: {}", processName);
            return false;

        } catch (Exception e) {
            log.error("Error during RemoteInput injection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get a list of PIDs for injected RemoteInput clients.
     * 
     * @param unpairedOnly If true, only return unpaired clients
     * @return List of process IDs
     */
    public List<Integer> getClientPids(boolean unpairedOnly) {
        List<Integer> pids = new ArrayList<>();

        try {
            long clientCount = RemoteInput.INSTANCE.EIOS_GetClients(unpairedOnly);
            log.debug("Found {} {}clients", clientCount, unpairedOnly ? "unpaired " : "");

            for (long i = 0; i < clientCount; i++) {
                int clientPid = RemoteInput.INSTANCE.EIOS_GetClientPID(i);
                if (clientPid > 0) {
                    pids.add(clientPid);
                }
            }
        } catch (Exception e) {
            log.error("Error getting client PIDs: {}", e.getMessage(), e);
        }

        return pids;
    }

    /**
     * Check if the service is connected to a RemoteInput client.
     * 
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get() && eios != null;
    }

    /**
     * Disconnect from the RemoteInput client and release resources.
     */
    public void disconnect() {
        if (eios != null) {
            try {
                log.info("Disconnecting from RemoteInput client PID: {}", pid);
                RemoteInput.INSTANCE.EIOS_ReleaseTarget(eios);
            } catch (Exception e) {
                log.warn("Error releasing RemoteInput target: {}", e.getMessage());
            } finally {
                eios = null;
                pid = 0;
                connected.set(false);
            }
        }
    }

    // ============== Mouse Operations ==============

    /**
     * Move the mouse cursor to the specified coordinates.
     * 
     * @param x Target X coordinate
     * @param y Target Y coordinate
     */
    public void moveMouse(int x, int y) {
        if (!ensureConnected())
            return;

        try {
            RemoteInput.INSTANCE.EIOS_MoveMouse(eios, x, y);
        } catch (Exception e) {
            log.error("Error moving mouse to ({}, {}): {}", x, y, e.getMessage());
        }
    }

    /**
     * Press and hold a mouse button.
     * 
     * @param button Mouse button (1 = left, 2 = middle, 3 = right)
     */
    public void holdMouse(int button) {
        if (!ensureConnected())
            return;

        try {
            // Get current position for the hold operation
            int[] xArr = new int[1];
            int[] yArr = new int[1];
            RemoteInput.INSTANCE.EIOS_GetMousePosition(eios, xArr, yArr);
            RemoteInput.INSTANCE.EIOS_HoldMouse(eios, xArr[0], yArr[0], button);
        } catch (Exception e) {
            log.error("Error holding mouse button {}: {}", button, e.getMessage());
        }
    }

    /**
     * Release a mouse button.
     * 
     * @param button Mouse button (1 = left, 2 = middle, 3 = right)
     */
    public void releaseMouse(int button) {
        if (!ensureConnected())
            return;

        try {
            // Get current position for the release operation
            int[] xArr = new int[1];
            int[] yArr = new int[1];
            RemoteInput.INSTANCE.EIOS_GetMousePosition(eios, xArr, yArr);
            RemoteInput.INSTANCE.EIOS_ReleaseMouse(eios, xArr[0], yArr[0], button);
        } catch (Exception e) {
            log.error("Error releasing mouse button {}: {}", button, e.getMessage());
        }
    }

    /**
     * Perform a complete click (press and release) with the specified button.
     * 
     * @param button Mouse button (1 = left, 2 = middle, 3 = right)
     */
    public void click(int button) {
        holdMouse(button);
        try {
            // Small delay between press and release for realism
            Thread.sleep(20 + (int) (Math.random() * 30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        releaseMouse(button);
    }

    /**
     * Perform a left click at the current mouse position.
     */
    public void leftClick() {
        click(MOUSE_BUTTON_LEFT);
    }

    /**
     * Perform a right click at the current mouse position.
     */
    public void rightClick() {
        click(MOUSE_BUTTON_RIGHT);
    }

    /**
     * Scroll the mouse wheel.
     * 
     * @param lines Number of lines to scroll (positive = up, negative = down)
     */
    public void scrollMouse(int lines) {
        if (!ensureConnected())
            return;

        try {
            int[] xArr = new int[1];
            int[] yArr = new int[1];
            RemoteInput.INSTANCE.EIOS_GetMousePosition(eios, xArr, yArr);
            RemoteInput.INSTANCE.EIOS_ScrollMouse(eios, xArr[0], yArr[0], lines);
        } catch (Exception e) {
            log.error("Error scrolling mouse: {}", e.getMessage());
        }
    }

    // ============== Keyboard Operations ==============

    /**
     * Press and hold a keyboard key.
     * 
     * @param vkCode Windows virtual key code (e.g., KeyEvent.VK_SHIFT)
     */
    public void holdKey(int vkCode) {
        if (!ensureConnected())
            return;

        try {
            RemoteInput.INSTANCE.EIOS_HoldKey(eios, vkCode);
        } catch (Exception e) {
            log.error("Error holding key {}: {}", vkCode, e.getMessage());
        }
    }

    /**
     * Release a keyboard key.
     * 
     * @param vkCode Windows virtual key code
     */
    public void releaseKey(int vkCode) {
        if (!ensureConnected())
            return;

        try {
            RemoteInput.INSTANCE.EIOS_ReleaseKey(eios, vkCode);
        } catch (Exception e) {
            log.error("Error releasing key {}: {}", vkCode, e.getMessage());
        }
    }

    /**
     * Perform a complete key press (press and release).
     * 
     * @param vkCode Windows virtual key code
     */
    public void pressKey(int vkCode) {
        holdKey(vkCode);
        try {
            // Small delay between press and release
            Thread.sleep(30 + (int) (Math.random() * 50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        releaseKey(vkCode);
    }

    /**
     * Type a string of characters.
     * 
     * @param text      Text to type
     * @param keyWaitMs Delay between key presses in milliseconds
     */
    public void sendString(String text, int keyWaitMs) {
        if (!ensureConnected())
            return;

        try {
            RemoteInput.INSTANCE.EIOS_SendString(eios, text, keyWaitMs, keyWaitMs);
        } catch (Exception e) {
            log.error("Error sending string: {}", e.getMessage());
        }
    }

    /**
     * Type a string with default timing.
     * 
     * @param text Text to type
     */
    public void sendString(String text) {
        sendString(text, 50);
    }

    // ============== Helper Methods ==============

    /**
     * Ensure connection is established before performing operations.
     * 
     * @return true if connected, false otherwise
     */
    private boolean ensureConnected() {
        if (!isConnected()) {
            log.warn("RemoteInput not connected - operation skipped");
            return false;
        }
        return true;
    }
}
