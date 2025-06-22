import win32gui
import win32api
import win32con
import time
import random
import ctypes
from ctypes import wintypes
import threading
import queue
import tkinter as tk

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

class KEYBDINPUT(ctypes.Structure):
    _fields_ = (("wVk",         wintypes.WORD),
                ("wScan",       wintypes.WORD),
                ("dwFlags",     wintypes.DWORD),
                ("time",        wintypes.DWORD),
                ("dwExtraInfo", ctypes.POINTER(ULONG_PTR)))

class INPUT_I(ctypes.Union):
    _fields_ = (("mi", MOUSEINPUT),
                ("ki", KEYBDINPUT))

class INPUT(ctypes.Structure):
    _fields_ = (("type", wintypes.DWORD),
                ("ii",   INPUT_I))

# --- Persistent Visual Indicator ---
class IndicatorWindow:
    def __init__(self):
        self.root = None
        self.pos_queue = queue.Queue()
        self.current_pos = (0, 0)
        self.thread = threading.Thread(target=self._run, daemon=True)

    def _run(self):
        self.root = tk.Tk()
        self.root.overrideredirect(True)
        self.root.geometry("5x5+0+0")
        self.root.attributes("-topmost", True, "-alpha", 0.5)
        self.root.configure(bg='red')
        
        self._check_queue()
        self.root.mainloop()

    def _check_queue(self):
        try:
            while True:
                new_pos = self.pos_queue.get_nowait()
                self.current_pos = new_pos
                self.root.geometry(f"+{new_pos[0]}+{new_pos[1]}")
        except queue.Empty:
            pass
        
        if self.root:
            self.root.after(5, self._check_queue)

    def start(self):
        self.thread.start()

    def stop(self):
        if self.root:
            self.root.quit()
    
    def move(self, x, y):
        self.pos_queue.put((x, y))

indicator = IndicatorWindow()

# --- Automation Functions ---

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
    """Moves mouse, sends a click using SendInput, and restores the original cursor position."""
    # --- Save original cursor position ---
    orig_x, orig_y = win32gui.GetCursorPos()

    # --- Verification Step ---
    target_class = win32gui.GetClassName(hwnd)
    print(f"VERIFICATION: Sending click to HWND: {hwnd}, Class: '{target_class}'")

    # --- Coordinate Conversion ---
    screen_x, screen_y = win32gui.ClientToScreen(hwnd, (x, y))
    screen_width = win32api.GetSystemMetrics(win32con.SM_CXSCREEN)
    screen_height = win32api.GetSystemMetrics(win32con.SM_CYSCREEN)
    
    # --- Movement Simulation ---
    start_x, start_y = indicator.current_pos
    num_steps = 20
    
    for i in range(num_steps + 1):
        t = i / num_steps
        inter_x = int(start_x + t * (screen_x - start_x))
        inter_y = int(start_y + t * (screen_y - start_y))
        
        nx = int(inter_x * 65535 / screen_width)
        ny = int(inter_y * 65535 / screen_height)

        move_flags = win32con.MOUSEEVENTF_MOVE | win32con.MOUSEEVENTF_ABSOLUTE
        move = INPUT(type=win32con.INPUT_MOUSE, ii=INPUT_I(mi=MOUSEINPUT(dx=nx, dy=ny, dwFlags=move_flags)))
        ctypes.windll.user32.SendInput(1, ctypes.byref(move), ctypes.sizeof(INPUT))
        indicator.move(inter_x, inter_y)
        time.sleep(0.01) # Controls movement speed

    # --- SendInput Click ---
    click_flags = win32con.MOUSEEVENTF_LEFTDOWN | win32con.MOUSEEVENTF_LEFTUP | win32con.MOUSEEVENTF_ABSOLUTE
    target_nx = int(screen_x * 65535 / screen_width)
    target_ny = int(screen_y * 65535 / screen_height)
    click = INPUT(type=win32con.INPUT_MOUSE, ii=INPUT_I(mi=MOUSEINPUT(dx=target_nx, dy=target_ny, dwFlags=click_flags)))
    ctypes.windll.user32.SendInput(1, ctypes.byref(click), ctypes.sizeof(INPUT))

    # --- Restore original cursor position ---
    orig_nx = int(orig_x * 65535 / screen_width)
    orig_ny = int(orig_y * 65535 / screen_height)
    restore = INPUT(type=win32con.INPUT_MOUSE, ii=INPUT_I(mi=MOUSEINPUT(dx=orig_nx, dy=orig_ny, dwFlags=win32con.MOUSEEVENTF_MOVE | win32con.MOUSEEVENTF_ABSOLUTE)))
    ctypes.windll.user32.SendInput(1, ctypes.byref(restore), ctypes.sizeof(INPUT))

VIRTUAL_KEYS = {
    'shift': win32con.VK_SHIFT,
}

def _send_key_input(vk_code, is_down):
    flags = win32con.KEYEVENTF_SCANCODE
    if not is_down:
        flags |= win32con.KEYEVENTF_KEYUP
        
    scan_code = win32api.MapVirtualKey(vk_code, 0)
    key_input = INPUT(type=win32con.INPUT_KEYBOARD, ii=INPUT_I(ki=KEYBDINPUT(wVk=0, wScan=scan_code, dwFlags=flags)))
    ctypes.windll.user32.SendInput(1, ctypes.byref(key_input), ctypes.sizeof(INPUT))

def background_key_press(hwnd, key):
    """Sends a background key press (down and up) using SendInput."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    _send_key_input(vk_code, is_down=True)
    time.sleep(random.uniform(0.02, 0.05))
    _send_key_input(vk_code, is_down=False)

def background_key_hold(hwnd, key):
    """Sends a background key down message using SendInput."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    _send_key_input(vk_code, is_down=True)

def background_key_release(hwnd, key):
    """Sends a background key up message using SendInput."""
    vk_code = VIRTUAL_KEYS.get(key.lower())
    if not vk_code:
        raise ValueError(f"Unsupported key: {key}")
    
    _send_key_input(vk_code, is_down=False) 
