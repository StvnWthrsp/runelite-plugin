import remote_input
import time
import random
import win32con

# A selection of virtual-key codes from win32con
VIRTUAL_KEYS = {
    'shift': win32con.VK_SHIFT,
    'control': win32con.VK_CONTROL,
    'alt': win32con.VK_MENU,
    'esc': win32con.VK_ESCAPE,
    'f1': win32con.VK_F1,
    'f2': win32con.VK_F2,
    'f3': win32con.VK_F3,
    'f4': win32con.VK_F4,
    'f5': win32con.VK_F5,
    'f6': win32con.VK_F6,
    'f7': win32con.VK_F7,
    'f8': win32con.VK_F8,
    'f9': win32con.VK_F9,
    'f10': win32con.VK_F10,
    'f11': win32con.VK_F11,
    'f12': win32con.VK_F12,
}

class Automation:
    """
    Manages the RemoteInput client connection and provides methods for background automation.
    This class is intended to be used as a singleton, managed by the FastAPI application.
    """
    def __init__(self):
        self.client = None

    def connect(self, process_name="java.exe"):
        """
        Injects into the target process and pairs with the client.
        
        Args:
            process_name: The name of the process to inject into. Defaults to "java.exe".

        Returns:
            The paired client object if successful.

        Raises:
            Exception: If injection or pairing fails.
        """
        if self.client:
            print("Already connected to a client.")
            return self.client

        print(f"Attempting to inject into '{process_name}'...")
        remote_input.EIOS.inject(process_name)
        time.sleep(3)  # Wait for injection to complete

        client_pids = remote_input.EIOS.get_clients_pids(True)
        if not client_pids:
            raise Exception(f"Injection failed. No unpaired clients found for '{process_name}'. Is RuneLite running?")

        pid = client_pids[0]
        print(f"Found client PID: {pid}. Attempting to pair...")
        
        self.client = remote_input.EIOS.pair_client_pid(pid)
        if not self.client:
            raise Exception("Failed to pair with the client.")

        print(f"Successfully paired with client PID: {pid}")
        self.client.set_mouse_input_enabled(True)
        self.client.set_keyboard_input_enabled(True)
        print("Mouse and keyboard input enabled.")
        return self.client

    def disconnect(self):
        """Kills the client connection."""
        if self.client:
            print("Disconnecting from client...")
            self.client.kill_client()
            self.client = None
            print("Disconnected.")

    def _ensure_connected(self):
        if not self.client:
            raise Exception("Not connected to a client. Please connect first.")

    def click(self, x: int, y: int):
        """
        Moves the mouse to the specified coordinates and performs a left-click.

        Args:
            x: The x-coordinate (relative to the client window).
            y: The y-coordinate (relative to the client window).
        """
        self._ensure_connected()
        if self.client:
            self.client.move_mouse(x, y)
            time.sleep(random.uniform(0.02, 0.05))
            self.client.hold_mouse(1) # 1 for left-click
            time.sleep(random.uniform(0.03, 0.07))
            self.client.release_mouse(1)

    def _get_vk_code(self, key: str):
        key_lower = key.lower()
        vk_code = VIRTUAL_KEYS.get(key_lower)
        if not vk_code:
            raise ValueError(f"Unsupported key: '{key}'.")
        return vk_code

    def key_press(self, key: str):
        """
        Presses and releases a key.

        Args:
            key: The key to press (e.g., "shift").
        """
        self._ensure_connected()
        vk_code = self._get_vk_code(key)
        if self.client:
            self.client.hold_key(vk_code)
            time.sleep(random.uniform(0.02, 0.05))
            self.client.release_key(vk_code)

    def key_hold(self, key: str):
        """
        Holds down a key.

        Args:
            key: The key to hold (e.g., "shift").
        """
        self._ensure_connected()
        vk_code = self._get_vk_code(key)
        if self.client:
            self.client.hold_key(vk_code)
        
    def key_release(self, key: str):
        """
        Releases a key.

        Args:
            key: The key to release (e.g., "shift").
        """
        self._ensure_connected()
        vk_code = self._get_vk_code(key)
        if self.client:
            self.client.release_key(vk_code)

# Create a single instance of the Automation class to be used by the app
automation_manager = Automation() 
