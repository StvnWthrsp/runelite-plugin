from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import automation

app = FastAPI()

# --- Pydantic Models for Request Bodies ---
class ClickRequest(BaseModel):
    x: int
    y: int

class KeyRequest(BaseModel):
    key: str

# --- Helper Function ---
def get_hwnd():
    try:
        return automation.get_game_window_hwnd()
    except Exception as e:
        raise HTTPException(status_code=404, detail=str(e))

# --- API Endpoints ---
@app.post("/click")
def click(req: ClickRequest):
    hwnd = get_hwnd()
    automation.background_click(hwnd, req.x, req.y)
    return {"status": "click sent"}

@app.post("/key_press")
def key_press(req: KeyRequest):
    hwnd = get_hwnd()
    try:
        automation.background_key_press(hwnd, req.key)
        return {"status": f"key '{req.key}' pressed"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/key_hold")
def key_hold(req: KeyRequest):
    hwnd = get_hwnd()
    try:
        automation.background_key_hold(hwnd, req.key)
        return {"status": f"key '{req.key}' held"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/key_release")
def key_release(req: KeyRequest):
    hwnd = get_hwnd()
    try:
        automation.background_key_release(hwnd, req.key)
        return {"status": f"key '{req.key}' released"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/status")
def get_status():
    print("Plugin connected")
    return {"status": "ok"} 