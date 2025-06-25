from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from automation import automation_manager
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

app = FastAPI(
    title="OSRS Automation Server",
    description="A server to send background input to the OSRS client via RemoteInput.",
    version="2.0.0"
)

# --- App Lifecycle ---
@app.on_event("shutdown")
def shutdown_event():
    """On shutdown, ensure the client is disconnected."""
    log.info("Server shutting down. Disconnecting client...")
    automation_manager.disconnect()

# --- Pydantic Models for Request Bodies ---
class ClickRequest(BaseModel):
    x: int
    y: int

class KeyRequest(BaseModel):
    key: str

# --- API Endpoints ---
@app.post("/connect", summary="Connect to RuneLite Client")
def connect():
    """
    Injects into the RuneLite (java.exe) process and establishes a connection.
    This must be called before any other automation endpoints.
    """
    try:
        automation_manager.connect()
        return {"status": "connection successful"}
    except Exception as e:
        log.error(f"Connection failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/click", summary="Send Mouse Click")
def click(req: ClickRequest):
    """Moves the mouse and performs a left-click at the given coordinates."""
    try:
        automation_manager.click(req.x, req.y)
        return {"status": "click sent"}
    except Exception as e:
        log.error(f"Click failed: {e}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/key_press", summary="Press a Key")
def key_press(req: KeyRequest):
    """Presses and releases a single key."""
    try:
        automation_manager.key_press(req.key)
        return {"status": f"key '{req.key}' pressed"}
    except Exception as e:
        log.error(f"Key press failed: {e}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/key_hold", summary="Hold a Key")
def key_hold(req: KeyRequest):
    """Holds a key down (does not release)."""
    try:
        automation_manager.key_hold(req.key)
        return {"status": f"key '{req.key}' held"}
    except Exception as e:
        log.error(f"Key hold failed: {e}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/key_release", summary="Release a Key")
def key_release(req: KeyRequest):
    """Releases a key."""
    try:
        automation_manager.key_release(req.key)
        return {"status": f"key '{req.key}' released"}
    except Exception as e:
        log.error(f"Key release failed: {e}")
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/status", summary="Check API Status")
def get_status():
    """Endpoint for the Runelite plugin to verify the server is running."""
    log.info("Status check received from plugin.")
    return {"status": "ok"} 