import remote_input
import time
import tkinter as tk
import pygetwindow as gw # New dependency for getting window position

def main():
    """
    Performs an interactive test with a correctly positioned visual
    indicator for the click, and waits for user input before cleanup.
    """
    print("--- RemoteInput Final Test with Visual Indicator ---")

    client = None
    try:
        # Step 1: Inject and Pair
        print("Injecting into RuneLite.exe...")
        remote_input.EIOS.inject("java.exe")
        time.sleep(3)

        client_pids = remote_input.EIOS.get_clients_pids(True)
        if not client_pids:
            print("\n[FAIL] No client found after injection. Is RuneLite running?")
            return

        pid = client_pids[0]
        client = remote_input.EIOS.pair_client_pid(pid)
        if not client:
            print("\n[FAIL] Failed to pair with the client.")
            return
        print(f"[SUCCESS] Paired with client PID: {pid}")
        client.set_mouse_input_enabled(True)

        # Step 2: Get RuneLite window position
        runelite_windows = gw.getWindowsWithTitle('RuneLite')
        if not runelite_windows:
            print("\n[FAIL] Could not find RuneLite window to create overlay.")
            return
        window = runelite_windows[0]
        win_x, win_y = window.left, window.top
        print(f"Found RuneLite window at screen coordinates: ({win_x}, {win_y})")


        # Step 3: Perform a virtual click with visual feedback
        target_x, target_y = 420, 250
        print(f"\nMoving virtual mouse to window coordinates ({target_x}, {target_y}) and clicking.")

        # --- Create a correctly positioned visual indicator ---
        overlay_x = win_x + target_x
        overlay_y = win_y + target_y
        overlay = tk.Tk()
        overlay.overrideredirect(True)
        overlay.geometry(f"20x20+{overlay_x - 10}+{overlay_y - 10}")
        overlay.configure(bg='red')
        overlay.wm_attributes("-topmost", True)
        overlay.wm_attributes("-alpha", 0.6)
        overlay.update()

        client.move_mouse(target_x, target_y)
        time.sleep(0.05)
        client.hold_mouse(1)
        time.sleep(0.1)
        client.release_mouse(1)
        print("[SUCCESS] Virtual click sent.")

        time.sleep(0.5)
        overlay.destroy()

        # Step 4: Wait for user to signal cleanup
        print("\n--------------------------------------------------------------------")
        print("Connection is live. PRESS ENTER IN THIS CONSOLE to exit.")
        print("--------------------------------------------------------------------")
        input()

    except Exception as e:
        print(f"\n[ERROR] An unexpected error occurred: {e}")

    finally:
        # Step 5: Clean up
        if client:
            print("\nUser requested cleanup. Killing client process...")
            client.kill_client()
            print("[SUCCESS] Cleanup complete.")

if __name__ == "__main__":
    main() 