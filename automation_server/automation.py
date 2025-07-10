import remote_input
import time
import random
import win32con
import math
import numpy as np

# A selection of virtual-key codes from win32con
VIRTUAL_KEYS = {
    'shift': win32con.VK_SHIFT,
    'control': win32con.VK_CONTROL,
    'space': win32con.VK_SPACE,
    'enter': win32con.VK_RETURN,
    'tab': win32con.VK_TAB,
    'backspace': win32con.VK_BACK,
    'delete': win32con.VK_DELETE,
    'home': win32con.VK_HOME,
    'end': win32con.VK_END,
    'pageup': win32con.VK_PRIOR,
    'pagedown': win32con.VK_NEXT,
    'left': win32con.VK_LEFT,
    'right': win32con.VK_RIGHT,
    'up': win32con.VK_UP,
    'down': win32con.VK_DOWN,
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
    '0': 48,
    '1': 49,
    '2': 50,
    '3': 51,
    '4': 52,
    '5': 53,
    '6': 54,
    '7': 55,
    '8': 56,
    '9': 57,
}

class Automation:
    """
    Manages the RemoteInput client connection and provides methods for background automation.
    This class is intended to be used as a singleton, managed by the FastAPI application.
    """
    def __init__(self):
        self.client = None
        self.mouse_pos = (0, 0)

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
        # Initialize mouse position to a neutral corner
        self.mouse_pos = (0, 0)
        self.client.move_mouse(0, 0)
        return self.client

    def disconnect(self):
        """Kills the client connection."""
        if self.client:
            print("Disconnecting from client...")
            self.client.kill_client()
            self.client = None
            print("Disconnected.")
            self.mouse_pos = (0, 0)

    def _ensure_connected(self):
        if not self.client:
            raise Exception("Not connected to a client. Please connect first.")

    def move_mouse(self, x: int, y: int):
        """
        Moves the mouse to the specified coordinates using a human-like algorithm (WindMouse).

        Args:
            x: The target x-coordinate.
            y: The target y-coordinate.
        """
        self._ensure_connected()
        if self.client:
            start_x, start_y = self.mouse_pos
            self._wind_mouse_move(start_x, start_y, x, y)

    def move_and_click(self, x: int, y: int):

        """
        Moves the mouse to the specified coordinates and performs a left-click.

        Args:
            x: The x-coordinate (relative to the client window).
            y: The y-coordinate (relative to the client window).
        """
        self._ensure_connected()
        if self.client:
            self.move_mouse(x, y)
            time.sleep(random.gauss(mu=0.035, sigma=0.015))
            self.client.hold_mouse(1) # 1 for left-click
            time.sleep(random.gauss(mu=0.05, sigma=0.02))
            self.client.release_mouse(1)

    def click(self):
        """
        Performs a left-click.
        """
        self._ensure_connected()
        if self.client:
            self.client.hold_mouse(1) # 1 for left-click
            time.sleep(random.gauss(mu=0.05, sigma=0.02))
            self.client.release_mouse(1)
    
    def right_click(self):
        """
        Performs a right-click.
        """
        self._ensure_connected()
        if self.client:
            self.client.hold_mouse(0) # 2 for right-click
            time.sleep(random.gauss(mu=0.05, sigma=0.02))
            self.client.release_mouse(0)

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

    def kill_client(self):
        """Kills the client connection."""
        if self.client:
            print("Killing client...")
            self.client.kill_client()
            self.client = None

    def _wind_mouse_move(self, start_x, start_y, dest_x, dest_y, G_0=9, W_0=3, M_0=15, D_0=12):
        '''
        WindMouse algorithm. Calls the move_mouse kwarg with each new step.
        Released under the terms of the GPLv3 license.
        G_0 - magnitude of the gravitational fornce
        W_0 - magnitude of the wind force fluctuations
        M_0 - maximum step size (velocity clip threshold)
        D_0 - distance where wind behavior changes from random to damped
        '''
        if not self.client:
            return

        def move_mouse_wrapper(x, y):
            if self.client:
                self.client.move_mouse(x, y)
                time.sleep(random.uniform(0.005, 0.01))

        sqrt3 = np.sqrt(3)
        sqrt5 = np.sqrt(5)

        current_x, current_y = start_x, start_y
        v_x = v_y = W_x = W_y = 0
        
        loop_start_x, loop_start_y = start_x, start_y

        while (dist := np.hypot(dest_x - loop_start_x, dest_y - loop_start_y)) >= 1:
            W_mag = min(W_0, dist)
            if dist >= D_0:
                W_x = W_x / sqrt3 + (2 * np.random.random() - 1) * W_mag / sqrt5
                W_y = W_y / sqrt3 + (2 * np.random.random() - 1) * W_mag / sqrt5
            else:
                W_x /= sqrt3
                W_y /= sqrt3
                if M_0 < 3:
                    M_0 = np.random.random() * 3 + 3
                else:
                    M_0 /= sqrt5
            
            v_x += W_x + G_0 * (dest_x - loop_start_x) / dist
            v_y += W_y + G_0 * (dest_y - loop_start_y) / dist
            v_mag = np.hypot(v_x, v_y)
            
            if v_mag > M_0:
                v_clip = M_0 / 2 + np.random.random() * M_0 / 2
                v_x = (v_x / v_mag) * v_clip
                v_y = (v_y / v_mag) * v_clip
            
            loop_start_x += v_x
            loop_start_y += v_y
            
            move_x = int(np.round(loop_start_x))
            move_y = int(np.round(loop_start_y))
            
            if current_x != move_x or current_y != move_y:
                move_mouse_wrapper(move_x, move_y)
                current_x, current_y = move_x, move_y

        # Final move to ensure we are at the destination
        self.client.move_mouse(dest_x, dest_y)
        self.mouse_pos = (dest_x, dest_y)

# Create a single instance of the Automation class to be used by the app
automation_manager = Automation() 
