import win32pipe
import win32file
import win32security
import pywintypes
import json
import threading
import logging
from automation import automation_manager

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
log = logging.getLogger(__name__)

class PipeServer:
    """
    Named pipe server that replaces the FastAPI HTTP server.
    Listens for JSON commands from the Java plugin and dispatches them to automation functions.
    """
    
    def __init__(self, pipe_name=r'\\.\pipe\Runepal'):
        self.pipe_name = pipe_name
        self.running = False
        self.connected = False
        
    def start(self):
        """Start the named pipe server."""
        self.running = True
        log.info(f"Starting named pipe server on {self.pipe_name}")
        log.info("Server ready. Waiting for RuneLite plugin to connect and send commands.")
        
        while self.running:
            try:
                # Create the named pipe
                sa = win32security.SECURITY_ATTRIBUTES()
                sa.bInheritHandle = False
                pipe = win32pipe.CreateNamedPipe(
                    self.pipe_name,
                    win32pipe.PIPE_ACCESS_INBOUND,  # Server reads from client
                    win32pipe.PIPE_TYPE_MESSAGE | win32pipe.PIPE_READMODE_MESSAGE | win32pipe.PIPE_WAIT,
                    1,  # Max instances
                    65536,  # Out buffer size
                    65536,  # In buffer size
                    0,  # Default timeout
                    sa  # Security attributes
                )
                
                log.info("Waiting for client connection...")
                
                # Wait for client to connect
                win32pipe.ConnectNamedPipe(pipe, None)
                self.connected = True
                log.info("Client connected!")
                
                # Handle the connected client
                self._handle_client(pipe)
                
            except pywintypes.error as e:
                if e.winerror == 231:  # ERROR_PIPE_BUSY
                    log.warning("Pipe is busy, retrying...")
                    continue
                else:
                    log.error(f"Pipe error: {e}")
                    break
            except Exception as e:
                log.error(f"Unexpected error: {e}")
                break
            finally:
                try:
                    win32file.CloseHandle(pipe)
                except:
                    pass
                self.connected = False
                
        log.info("Named pipe server stopped.")
    
    def _handle_client(self, pipe):
        """Handle communication with a connected client."""
        buffer = ""
        
        while self.running:
            try:
                # Read data from the pipe
                result, data = win32file.ReadFile(pipe, 4096)
                
                if not data:
                    break
                    
                # Decode and add to buffer
                if isinstance(data, bytes):
                    buffer += data.decode('utf-8')
                else:
                    buffer += str(data)
                
                # Process complete lines (commands)
                while '\n' in buffer:
                    line, buffer = buffer.split('\n', 1)
                    line = line.strip()
                    
                    if line:
                        self._process_command(line)
                        
            except pywintypes.error as e:
                if e.winerror == 109:  # ERROR_BROKEN_PIPE
                    log.info("Client disconnected.")
                    break
                else:
                    log.error(f"Read error: {e}")
                    break
            except Exception as e:
                log.error(f"Error handling client: {e}")
                break
    
    def _process_command(self, command_json):
        """Process a JSON command from the client."""
        try:
            command = json.loads(command_json)
            action = command.get('action')
            
            if not action:
                log.warning(f"Command missing action: {command_json}")
                return
            
            log.debug(f"Processing command: {action}")
            
            # Dispatch command to appropriate automation function
            if action == 'connect':
                self._handle_connect()
            elif action == 'click':
                self._handle_click(command)
            elif action == 'move':
                self._handle_move(command)
            elif action == 'key_press':
                self._handle_key_press(command)
            elif action == 'key_hold':
                self._handle_key_hold(command)
            elif action == 'key_release':
                self._handle_key_release(command)
            else:
                log.warning(f"Unknown action: {action}")
                
        except json.JSONDecodeError as e:
            log.error(f"Invalid JSON command: {command_json} - {e}")
        except Exception as e:
            log.error(f"Error processing command '{command_json}': {e}")
    
    def _handle_connect(self):
        """Handle connect command."""
        try:
            automation_manager.connect()
            log.info("Connect command processed successfully.")
        except Exception as e:
            log.error(f"Connect command failed: {e}")
    
    def _handle_click(self, command):
        """Handle click command."""
        try:
            x = command.get('x')
            y = command.get('y')
            move = command.get('move')
            
            if x is None or y is None:
                log.warning(f"Click command missing coordinates: {command}")
                return
            
            if move:
                automation_manager.move_and_click(x, y)
            else:
                automation_manager.click(x, y)
            log.debug(f"Click executed at ({x}, {y})")
            
        except Exception as e:
            log.error(f"Click command failed: {e}")
    
    def _handle_move(self, command):
        """Handle move command."""
        try:
            x = command.get('x')
            y = command.get('y')
            if x is None or y is None:
                log.warning(f"Move command missing coordinates: {command}")
                return
            
            automation_manager.move_mouse(x, y)
            log.debug(f"Move executed at ({x}, {y})")
            
        except Exception as e:
            log.error(f"Move command failed: {e}")

    
    def _handle_key_press(self, command):
        """Handle key_press command."""
        try:
            key = command.get('key')
            
            if not key:
                log.warning(f"Key press command missing key: {command}")
                return
            
            automation_manager.key_press(key)
            log.debug(f"Key press executed: {key}")
            
        except Exception as e:
            log.error(f"Key press command failed: {e}")
    
    def _handle_key_hold(self, command):
        """Handle key_hold command."""
        try:
            key = command.get('key')
            
            if not key:
                log.warning(f"Key hold command missing key: {command}")
                return
            
            automation_manager.key_hold(key)
            log.debug(f"Key hold executed: {key}")
            
        except Exception as e:
            log.error(f"Key hold command failed: {e}")
    
    def _handle_key_release(self, command):
        """Handle key_release command."""
        try:
            key = command.get('key')
            
            if not key:
                log.warning(f"Key release command missing key: {command}")
                return
            
            automation_manager.key_release(key)
            log.debug(f"Key release executed: {key}")
            
        except Exception as e:
            log.error(f"Key release command failed: {e}")
    
    def stop(self):
        """Stop the named pipe server."""
        self.running = False
        log.info("Stopping named pipe server...")

def main():
    """Main entry point."""
    log.info("Runepal Automation Server")
    log.info("Version: 2.0.0")
    log.info("Waiting for RuneLite plugin to connect...")
    
    server = PipeServer()
    
    try:
        server.start()
    except KeyboardInterrupt:
        log.info("Received interrupt signal.")
    except Exception as e:
        log.error(f"Server error: {e}")
    finally:
        server.stop()
        automation_manager.disconnect()
        log.info("Server shutdown complete.")

if __name__ == "__main__":
    main() 