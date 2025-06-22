import win32gui
import win32api
import win32con
import time
import random

def get_game_window_hwnd():
    """Finds the window handle for a window whose title starts with 'RuneLite'."""
    
    def enum_windows_callback(hwnd, windows):
        if win32gui.IsWindowVisible(hwnd) and win32gui.GetWindowText(hwnd).startswith("RuneLite"):
            windows.append(hwnd)

    windows = []
    win32gui.EnumWindows(enum_windows_callback, windows)
    
    if not windows:
        raise Exception("Game window not found. Please ensure RuneLite is running.")
        
    return windows[0]

def background_click(hwnd, x, y):
    """Sends a background left-click to the specified window coordinates."""
    lParam = win32api.MAKELONG(x, y)
    win32gui.SendMessage(hwnd, win32con.WM_LBUTTONDOWN, win32con.MK_LBUTTON, lParam)
    # Add a small, randomized delay to mimic human behavior
    time.sleep(random.uniform(0.03, 0.07))
    win32gui.SendMessage(hwnd, win32con.WM_LBUTTONUP, 0, lParam)

VIRTUAL_KEYS = {
    'shift': win32con.VK_SHIFT,
}

def background_key_press(hwnd, key):
    """Sends a background key press (down and up) to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.SendMessage(hwnd, win32con.WM_KEYDOWN, vk_code, 0)
    time.sleep(random.uniform(0.02, 0.05))
    win32gui.SendMessage(hwnd, win32con.WM_KEYUP, vk_code, 0)

def background_key_hold(hwnd, key):
    """Sends a background key down message to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.SendMessage(hwnd, win32con.WM_KEYDOWN, vk_code, 0)

def background_key_release(hwnd, key):
    """Sends a background key up message to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.SendMessage(hwnd, win32con.WM_KEYUP, vk_code, 0) 