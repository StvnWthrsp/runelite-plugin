package com.runepal;

import lombok.extern.slf4j.Slf4j;
import com.google.gson.Gson;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service for communicating with the Python automation server via Windows named pipes.
 * This replaces the HTTP client functionality with a more efficient IPC mechanism.
 */
@Slf4j
@Singleton
public class PipeService {
    
    private static final String PIPE_NAME = "\\\\.\\pipe\\Runepal";
    private PrintWriter pipeWriter;
    private final Gson gson;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    @Inject
    public PipeService(Gson gson) {
        this.gson = gson;
    }
    
    /**
     * Connect to the named pipe server.
     * @return true if connection successful, false otherwise
     */
    public boolean connect() {
        try {
            log.info("Attempting to connect to named pipe: {}", PIPE_NAME);
            
            // Create file writer for the named pipe
            FileWriter fileWriter = new FileWriter(PIPE_NAME);
            pipeWriter = new PrintWriter(fileWriter, true); // true enables auto-flush
            
            connected.set(true);
            log.info("Successfully connected to named pipe server.");
            return true;
            
        } catch (IOException e) {
            log.error("Failed to connect to named pipe server: {}", e.getMessage());
            log.info("Make sure the Python automation server is running.");
            connected.set(false);
            return false;
        }
    }
    
    /**
     * Disconnect from the named pipe server.
     */
    public void disconnect() {
        try {
            if (pipeWriter != null) {
                pipeWriter.close();
                pipeWriter = null;
            }
            connected.set(false);
            log.info("Disconnected from named pipe server.");
        } catch (Exception e) {
            log.warn("Error during disconnect: {}", e.getMessage());
        }
    }
    
    /**
     * Check if connected to the pipe server.
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get() && pipeWriter != null && !pipeWriter.checkError();
    }
    
    /**
     * Send a JSON command to the server.
     * @param command The command object to send
     * @return true if sent successfully
     */
    private boolean sendCommand(Object command) {
        if (!isConnected()) {
            log.warn("Cannot send command - not connected to pipe server");
            return false;
        }
        
        try {
            String jsonCommand = gson.toJson(command);
            pipeWriter.println(jsonCommand); // Automatically adds \n delimiter
            
            // Check for write errors
            if (pipeWriter.checkError()) {
                log.error("Write error detected on pipe");
                connected.set(false);
                return false;
            }
            
            log.debug("Sent command: {}", jsonCommand);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send command: {}", e.getMessage());
            connected.set(false);
            return false;
        }
    }
    
    /**
     * Send a connect command to initialize the automation client.
     * @return true if sent successfully
     */
    public boolean sendConnect() {
        ConnectCommand command = new ConnectCommand();
        return sendCommand(command);
    }
    
    /**
     * Send a click command.
     * @param x X coordinate
     * @param y Y coordinate
     * @param move Move mouse
     * @return true if sent successfully
     */
    public boolean sendClick(int x, int y, boolean move) {
        ClickCommand command = new ClickCommand(x, y, move, false);
        return sendCommand(command);
    }

    /**
     * Send a right-click command.
     * @param x X coordinate
     * @param y Y coordinate
     * @param move Move mouse
     * @return true if sent successfully
     */
    public boolean sendRightClick(int x, int y, boolean move) {
        ClickCommand command = new ClickCommand(x, y, move, true);
        return sendCommand(command);
    }

    /**
     * Send a mouse move command, without clicking.
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if sent successfully
     */
    public boolean sendMouseMove(int x, int y) {
        MouseMoveCommand command = new MouseMoveCommand(x, y);
        return sendCommand(command);
    }
    
    /**
     * Send a key press command.
     * @param key The key to press
     * @return true if sent successfully
     */
    public boolean sendKeyPress(String key) {
        KeyCommand command = new KeyCommand("key_press", key);
        return sendCommand(command);
    }
    
    /**
     * Send a key hold command.
     * @param key The key to hold
     * @return true if sent successfully
     */
    public boolean sendKeyHold(String key) {
        KeyCommand command = new KeyCommand("key_hold", key);
        return sendCommand(command);
    }
    
    /**
     * Send a key release command.
     * @param key The key to release
     * @return true if sent successfully
     */
    public boolean sendKeyRelease(String key) {
        KeyCommand command = new KeyCommand("key_release", key);
        return sendCommand(command);
    }

    
    /**
     * Send an exit command to kill the injected client.
     * @return true if sent successfully
     */
    public boolean sendExit() {
        ExitCommand command = new ExitCommand();
        return sendCommand(command);
    }
    
    // Command classes for JSON serialization
    @SuppressWarnings("unused")
    private static class ConnectCommand {
        public final String action = "connect";
    }
    
    @SuppressWarnings("unused")
    private static class ClickCommand {
        public final String action;
        public final int x;
        public final int y;
        public final boolean move;

        public  ClickCommand(int x, int y, boolean move, boolean rightClick) {
            this.x = x;
            this.y = y;
            this.move = move;
            if (rightClick) {
                this.action = "right_click";
            } else {
                this.action = "click";
            }
        }
    }

    @SuppressWarnings("unused")
    private static class MouseMoveCommand {
        public final String action = "move";
        public final int x;
        public final int y;

        public MouseMoveCommand(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
    
    @SuppressWarnings("unused")
    private static class KeyCommand {
        public final String action;
        public final String key;
        
        public KeyCommand(String action, String key) {
            this.action = action;
            this.key = key;
        }
    }

    @SuppressWarnings("unused")
    private static class ExitCommand {
        public final String action = "exit";
    }
} 