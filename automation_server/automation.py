import win32gui
import win32api
import win32con
import time
import random

def get_game_window_hwnd():
    """Finds the window handle for a window whose title starts with 'RuneLite'."""
    
    def enum_windows_callback(hwnd, windows):
        if win32gui.IsWindowVisible(hwnd) and win32gui.GetWindowText(hwnd).startswith("RuneLite"):
            print(f"Found window: {win32gui.GetWindowText(hwnd)}")
            if win32gui.FindWindowEx(hwnd, None, "SunAwtCanvas", None) is not None:
                print(f"Found canvas window: {win32gui.FindWindowEx(hwnd, None, "SunAwtCanvas", None)}")
                windows.append(win32gui.FindWindowEx(hwnd, None, "SunAwtCanvas", None))

    windows = []
    win32gui.EnumWindows(enum_windows_callback, windows)
    
    if not windows:
        raise Exception("Game window not found. Please ensure RuneLite is running.")
        
    return windows[0]


def background_click(hwnd, x, y):
    """Sends a background left-click to the specified window coordinates."""
    # Convert client coordinates to screen coordinates
    screen_x, screen_y = win32gui.ClientToScreen(hwnd, (x, y))

    # --- Visual Debugger: Draw a red dot at the click location ---
    hdc = win32gui.GetDC(0)  # Get device context for the entire screen
    red = win32api.RGB(255, 0, 0)
    brush = win32gui.CreateSolidBrush(red)
    
    # Define a small 4x4 pixel rectangle for the dot
    rect = (screen_x - 2, screen_y - 2, screen_x + 2, screen_y + 2)
    win32gui.FillRect(hdc, rect, brush)
    
    # Clean up GDI objects
    win32gui.DeleteObject(brush)
    win32gui.ReleaseDC(0, hdc)
    
    # The dot is drawn, now proceed with the click
    lParam = win32api.MAKELONG(x, y)
    win32gui.PostMessage(hwnd, win32con.WM_LBUTTONDOWN, win32con.MK_LBUTTON, lParam)
    # Add a small, randomized delay to mimic human behavior
    time.sleep(random.uniform(0.03, 0.07))
    win32gui.PostMessage(hwnd, win32con.WM_LBUTTONUP, 0, lParam)
    
    # Give a moment for the dot to be visible before erasing it
    time.sleep(0.1)
    
    # Erase the dot by telling the window manager to repaint that area
    win32gui.InvalidateRect(0, rect, True)


VIRTUAL_KEYS = {
    'shift': win32con.VK_SHIFT,
}

def background_key_press(hwnd, key):
    """Sends a background key press (down and up) to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.PostMessage(hwnd, win32con.WM_KEYDOWN, vk_code, 0)
    time.sleep(random.uniform(0.02, 0.05))
    win32gui.PostMessage(hwnd, win32con.WM_KEYUP, vk_code, 0)

def background_key_hold(hwnd, key):
    """Sends a background key down message to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.PostMessage(hwnd, win32con.WM_KEYDOWN, vk_code, 0)

def background_key_release(hwnd, key):
    """Sends a background key up message to the specified window."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    win32gui.PostMessage(hwnd, win32con.WM_KEYUP, vk_code, 0) 
