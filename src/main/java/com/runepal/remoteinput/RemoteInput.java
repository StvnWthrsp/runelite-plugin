package com.runepal.remoteinput;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA interface mapping to the RemoteInput native library (libRemoteInput.dll).
 * This provides low-level access to the EIOS API for injecting into Java
 * processes
 * and performing mouse/keyboard input operations.
 * 
 * The library must be placed in
 * src/main/resources/win32-x86-64/libRemoteInput.dll
 * for JNA to automatically locate it from the classpath.
 */
public interface RemoteInput extends Library {

    /**
     * Singleton instance loaded from the native library.
     * JNA will search for libRemoteInput.dll in:
     * 1. java.library.path
     * 2. Classpath under {os}-{arch}/ (e.g., win32-x86-64/)
     * 3. System PATH
     */
    RemoteInput INSTANCE = Native.load("libRemoteInput", RemoteInput.class);

    // ============== Injection and Discovery ==============

    /**
     * Inject the RemoteInput library into all processes matching the given name.
     * 
     * @param processName Name of the process to inject into (e.g., "java.exe")
     */
    void EIOS_Inject(String processName);

    /**
     * Inject the RemoteInput library into a specific process by PID.
     * 
     * @param pid Process ID to inject into
     */
    void EIOS_Inject_PID(int pid);

    /**
     * Get the number of clients (injected processes) available.
     * 
     * @param unpairedOnly If true, only count unpaired clients
     * @return Number of clients available
     */
    long EIOS_GetClients(boolean unpairedOnly);

    /**
     * Get the PID of a client at the given index.
     * 
     * @param index Index of the client (0 to EIOS_GetClients() - 1)
     * @return Process ID of the client
     */
    int EIOS_GetClientPID(long index);

    // ============== Connection Management ==============

    /**
     * Pair with a client by PID to establish a connection.
     * 
     * @param pid Process ID to pair with
     * @return EIOS handle pointer, or null if pairing failed
     */
    Pointer EIOS_PairClient(int pid);

    /**
     * Request a target by PID string (alternative to PairClient).
     * 
     * @param pid PID as a string
     * @return EIOS handle pointer
     */
    Pointer EIOS_RequestTarget(String pid);

    /**
     * Release a previously acquired EIOS target.
     * 
     * @param eios The EIOS handle to release
     */
    void EIOS_ReleaseTarget(Pointer eios);

    /**
     * Kill a client, releasing its resources.
     * 
     * @param pid Process ID of the client to kill
     */
    void EIOS_KillClient(int pid);

    // ============== Mouse Operations ==============

    /**
     * Move the mouse cursor to the specified coordinates.
     * 
     * @param eios EIOS handle
     * @param x    Target X coordinate
     * @param y    Target Y coordinate
     */
    void EIOS_MoveMouse(Pointer eios, int x, int y);

    /**
     * Press and hold a mouse button at the current position.
     * 
     * @param eios   EIOS handle
     * @param x      X coordinate (used for position tracking)
     * @param y      Y coordinate (used for position tracking)
     * @param button Mouse button (1 = left, 2 = middle, 3 = right)
     */
    void EIOS_HoldMouse(Pointer eios, int x, int y, int button);

    /**
     * Release a mouse button at the current position.
     * 
     * @param eios   EIOS handle
     * @param x      X coordinate (used for position tracking)
     * @param y      Y coordinate (used for position tracking)
     * @param button Mouse button (1 = left, 2 = middle, 3 = right)
     */
    void EIOS_ReleaseMouse(Pointer eios, int x, int y, int button);

    /**
     * Scroll the mouse wheel.
     * 
     * @param eios  EIOS handle
     * @param x     X coordinate
     * @param y     Y coordinate
     * @param lines Number of lines to scroll (positive = up, negative = down)
     */
    void EIOS_ScrollMouse(Pointer eios, int x, int y, int lines);

    /**
     * Check if a mouse button is currently held.
     * 
     * @param eios   EIOS handle
     * @param button Mouse button to check
     * @return true if the button is held
     */
    boolean EIOS_IsMouseButtonHeld(Pointer eios, int button);

    /**
     * Get the current mouse position.
     * 
     * @param eios EIOS handle
     * @param x    Pointer to store X coordinate
     * @param y    Pointer to store Y coordinate
     */
    void EIOS_GetMousePosition(Pointer eios, int[] x, int[] y);

    // ============== Keyboard Operations ==============

    /**
     * Press and hold a keyboard key.
     * 
     * @param eios EIOS handle
     * @param key  Virtual key code (Windows VK_* constants)
     */
    void EIOS_HoldKey(Pointer eios, int key);

    /**
     * Release a keyboard key.
     * 
     * @param eios EIOS handle
     * @param key  Virtual key code (Windows VK_* constants)
     */
    void EIOS_ReleaseKey(Pointer eios, int key);

    /**
     * Check if a keyboard key is currently held.
     * 
     * @param eios EIOS handle
     * @param key  Virtual key code to check
     * @return true if the key is held
     */
    boolean EIOS_IsKeyHeld(Pointer eios, int key);

    /**
     * Send a string of characters as key presses.
     * 
     * @param eios       EIOS handle
     * @param text       Text to type
     * @param keyWait    Delay between key presses in milliseconds
     * @param keyModWait Delay for modifier keys in milliseconds
     */
    void EIOS_SendString(Pointer eios, String text, int keyWait, int keyModWait);
}
