import win32gui
import win32api
import win32con
import time
import random
import ctypes
from ctypes import wintypes

# --- CTypes Structures for SendInput ---
if ctypes.sizeof(ctypes.c_void_p) == 8: # 64-bit
    ULONG_PTR = ctypes.c_ulonglong
else: # 32-bit
    ULONG_PTR = ctypes.c_ulong

class MOUSEINPUT(ctypes.Structure):
    _fields_ = (("dx",          wintypes.LONG),
                ("dy",          wintypes.LONG),
                ("mouseData",   wintypes.DWORD),
                ("dwFlags",     wintypes.DWORD),
                ("time",        wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ULONG_PTR)))

class INPUT_I(ctypes.Union):
    _fields_ = (("mi", MOUSEINPUT),)

class INPUT(ctypes.Structure):
    _fields_ = (("type", wintypes.DWORD),
                ("ii",   INPUT_I))

def get_game_window_hwnd():
    """Finds the window handle for a window whose title starts with 'RuneLite'."""
    
    def enum_windows_callback(hwnd, windows):
        if win32gui.IsWindowVisible(hwnd) and win32gui.GetWindowText(hwnd).startswith("RuneLite"):
            canvas_hwnd = win32gui.FindWindowEx(hwnd, None, "SunAwtCanvas", None)
            if canvas_hwnd:
                class_name = win32gui.GetClassName(canvas_hwnd)
                print(f"VERIFICATION: Found canvas. HWND: {canvas_hwnd}, Class: '{class_name}'")
                windows.append(canvas_hwnd)

    windows = []
    win32gui.EnumWindows(enum_windows_callback, windows)
    
    if not windows:
        raise Exception("Game window not found. Please ensure RuneLite is running.")
        
    return windows[0]


def background_click(hwnd, x, y):
    """Sends a background left-click using SendInput and restores the original cursor position."""
    # --- Save original cursor position ---
    orig_x, orig_y = win32gui.GetCursorPos()

    # --- Verification Step ---
    target_class = win32gui.GetClassName(hwnd)
    print(f"VERIFICATION: Sending click to HWND: {hwnd}, Class: '{target_class}'")

    # --- Coordinate Conversion for SendInput ---
    screen_x, screen_y = win32gui.ClientToScreen(hwnd, (x, y))
    
    screen_width = win32api.GetSystemMetrics(win32con.SM_CXSCREEN)
    screen_height = win32api.GetSystemMetrics(win32con.SM_CYSCREEN)
    
    # Target coordinates
    target_nx = int(screen_x * 65535 / screen_width)
    target_ny = int(screen_y * 65535 / screen_height)

    # Original coordinates
    orig_nx = int(orig_x * 65535 / screen_width)
    orig_ny = int(orig_y * 65535 / screen_height)

    # --- Visual Debugger ---
    hdc = win32gui.GetDC(0)
    red = win32api.RGB(255, 0, 0)
    brush = win32gui.CreateSolidBrush(red)
    rect = (screen_x - 2, screen_y - 2, screen_x + 2, screen_y + 2)
    win32gui.FillRect(hdc, rect, brush) # type: ignore
    win32gui.DeleteObject(brush)
    win32gui.ReleaseDC(0, hdc)
    
    # --- SendInput Implementation ---
    # Sequence of events: move to target, click down, click up, move back
    flags = win32con.MOUSEEVENTF_MOVE | win32con.MOUSEEVENTF_ABSOLUTE
    
    # Move to target
    move = INPUT(type=win32con.INPUT_MOUSE,
                 ii=INPUT_I(mi=MOUSEINPUT(dx=target_nx, dy=target_ny, dwFlags=flags, mouseData=0, time=0, dwExtraInfo=None)))
                 
    # Left button down
    down = INPUT(type=win32con.INPUT_MOUSE,
                 ii=INPUT_I(mi=MOUSEINPUT(dx=target_nx, dy=target_ny, dwFlags=flags | win32con.MOUSEEVENTF_LEFTDOWN, mouseData=0, time=0, dwExtraInfo=None)))
                 
    # Left button up
    up = INPUT(type=win32con.INPUT_MOUSE,
               ii=INPUT_I(mi=MOUSEINPUT(dx=target_nx, dy=target_ny, dwFlags=flags | win32con.MOUSEEVENTF_LEFTUP, mouseData=0, time=0, dwExtraInfo=None)))

    # Move back to original position
    restore = INPUT(type=win32con.INPUT_MOUSE,
                    ii=INPUT_I(mi=MOUSEINPUT(dx=orig_nx, dy=orig_ny, dwFlags=flags, mouseData=0, time=0, dwExtraInfo=None)))

    inputs = (INPUT * 4)(move, down, up, restore)
    ctypes.windll.user32.SendInput(4, ctypes.byref(inputs), ctypes.sizeof(INPUT))

    # --- Cleanup Visual Debugger ---
    time.sleep(0.1) # Keep the dot visible briefly
    win32gui.InvalidateRect(0, rect, True) # type: ignore


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
