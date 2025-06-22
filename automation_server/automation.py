import win32gui
import win32api
import win32con
import time
import random

def get_game_window_hwnd():
    """Finds the handle for the 'SunAwtCanvas' child window within the main 'RuneLite' window."""
    
    # First, find the main RuneLite window
    main_hwnd = None
    def enum_main_windows_callback(hwnd, windows):
        if win32gui.IsWindowVisible(hwnd) and win32gui.GetWindowText(hwnd).startswith("RuneLite"):
            windows.append(hwnd)
    
    main_windows = []
    win32gui.EnumWindows(enum_main_windows_callback, main_windows)
    
    if not main_windows:
        raise Exception("Main RuneLite window not found. Please ensure RuneLite is running.")
    main_hwnd = main_windows[0]

    # Now, find the 'SunAwtCanvas' child window within the main window
    canvas_hwnd = None
    def enum_child_windows_callback(hwnd, child_windows):
        class_name = win32gui.GetClassName(hwnd)
        if class_name == "SunAwtCanvas":
            child_windows.append(hwnd)

    child_windows = []
    win32gui.EnumChildWindows(main_hwnd, enum_child_windows_callback, child_windows)

    if not child_windows:
        raise Exception("RuneLite game canvas ('SunAwtCanvas') not found within the main window.")
        
    return child_windows[0]

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