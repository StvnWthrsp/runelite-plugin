package com.runepal.harness.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runepal.MouseMovementCompletedEvent;
import com.runepal.RunepalPlugin;
import com.runepal.services.RemoteInputService;
import com.runepal.services.WindmouseService;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;

@Slf4j
public class HarnessActionRuntime {
    private static final int DEFAULT_WAIT_UNTIL_TIMEOUT_TICKS = 100;

    private final RunepalPlugin plugin;
    private final RemoteInputService remoteInputService;
    private final WindmouseService windmouseService;
    private final Consumer<HarnessActionStatusEvent> statusListener;

    private final Queue<HarnessAction> queue = new ArrayDeque<>();
    private HarnessAction activeAction;
    private long actionStartTick;
    private Integer waitTicksRemaining;
    private Integer waitUntilTimeoutTicks;
    private java.util.concurrent.CompletableFuture<MouseMovementCompletedEvent> moveFuture;

    public HarnessActionRuntime(
            RunepalPlugin plugin,
            RemoteInputService remoteInputService,
            WindmouseService windmouseService,
            Consumer<HarnessActionStatusEvent> statusListener) {
        this.plugin = plugin;
        this.remoteInputService = remoteInputService;
        this.windmouseService = windmouseService;
        this.statusListener = statusListener;
    }

    public int enqueue(JsonObject payload, long tick) {
        int added = 0;
        if (payload == null) {
            return 0;
        }

        if (readBoolean(payload, "replaceQueue", false)) {
            clearQueued();
            cancelActive("Replaced by new queue", tick);
        }

        if (payload.has("actions") && payload.get("actions").isJsonArray()) {
            JsonArray actions = payload.getAsJsonArray("actions");
            for (JsonElement actionElement : actions) {
                if (!actionElement.isJsonObject()) {
                    continue;
                }
                HarnessAction action = parseAction(actionElement.getAsJsonObject());
                if (action != null) {
                    queue.offer(action);
                    added++;
                }
            }
            return added;
        }

        HarnessAction singleAction = parseAction(payload);
        if (singleAction != null) {
            queue.offer(singleAction);
            added = 1;
        }
        return added;
    }

    public int clearQueued() {
        int removed = queue.size();
        queue.clear();
        return removed;
    }

    public boolean cancelActive(String reason, long tick) {
        if (activeAction == null) {
            return false;
        }

        if ("mouse_move_point".equals(activeAction.getType())) {
            windmouseService.cancelMovement();
        }
        finishActive("cancelled", "cancelled", reason == null ? "Cancelled" : reason, tick);
        return true;
    }

    public JsonObject getStatusSnapshot() {
        JsonObject status = new JsonObject();
        status.addProperty("queuedCount", queue.size());
        status.addProperty("hasActive", activeAction != null);
        if (activeAction != null) {
            JsonObject active = new JsonObject();
            active.addProperty("actionId", activeAction.getActionId());
            active.addProperty("type", activeAction.getType());
            active.add("payload", activeAction.getPayload());
            status.add("active", active);
        }
        return status;
    }

    public void onGameTick(long tick) {
        if (activeAction == null) {
            activeAction = queue.poll();
            if (activeAction == null) {
                return;
            }
            actionStartTick = tick;
            waitTicksRemaining = null;
            waitUntilTimeoutTicks = null;
            moveFuture = null;
            emit("started", "", "", tick);
        }

        String type = activeAction.getType();
        JsonObject payload = activeAction.getPayload();

        switch (type) {
            case "wait_ticks":
                handleWaitTicks(payload, tick);
                break;
            case "wait_until":
                handleWaitUntil(payload, tick);
                break;
            case "mouse_move_point":
                handleMouseMovePoint(payload, tick);
                break;
            case "mouse_click":
                if (!ensureRemoteInput(tick)) {
                    return;
                }
                if (!handleMouseClick(payload)) {
                    finishActive("failed", "invalid_payload", "mouse_click payload is invalid", tick);
                    return;
                }
                finishActive("succeeded", "", "Mouse click completed", tick);
                break;
            case "key_press":
                if (!ensureRemoteInput(tick)) {
                    return;
                }
                Integer pressKeyCode = readKeyCode(payload);
                if (pressKeyCode == null) {
                    finishActive("failed", "invalid_payload", "key_press requires key or keyCode", tick);
                    return;
                }
                remoteInputService.pressKey(pressKeyCode);
                finishActive("succeeded", "", "Key press completed", tick);
                break;
            case "key_hold":
                if (!ensureRemoteInput(tick)) {
                    return;
                }
                Integer holdKeyCode = readKeyCode(payload);
                if (holdKeyCode == null) {
                    finishActive("failed", "invalid_payload", "key_hold requires key or keyCode", tick);
                    return;
                }
                remoteInputService.holdKey(holdKeyCode);
                finishActive("succeeded", "", "Key hold completed", tick);
                break;
            case "key_release":
                if (!ensureRemoteInput(tick)) {
                    return;
                }
                Integer releaseKeyCode = readKeyCode(payload);
                if (releaseKeyCode == null) {
                    finishActive("failed", "invalid_payload", "key_release requires key or keyCode", tick);
                    return;
                }
                remoteInputService.releaseKey(releaseKeyCode);
                finishActive("succeeded", "", "Key release completed", tick);
                break;
            default:
                finishActive("failed", "unsupported_action", "Unsupported action type: " + type, tick);
                break;
        }
    }

    private void handleWaitTicks(JsonObject payload, long tick) {
        if (waitTicksRemaining == null) {
            waitTicksRemaining = Math.max(0, readInt(payload, "ticks", 1));
        }

        waitTicksRemaining = waitTicksRemaining - 1;
        if (waitTicksRemaining <= 0) {
            finishActive("succeeded", "", "Wait ticks completed", tick);
        }
    }

    private void handleWaitUntil(JsonObject payload, long tick) {
        if (waitUntilTimeoutTicks == null) {
            waitUntilTimeoutTicks = Math.max(1, readInt(payload, "timeoutTicks", DEFAULT_WAIT_UNTIL_TIMEOUT_TICKS));
        }

        String condition = readString(payload, "condition", "");
        if (evaluateCondition(condition, payload)) {
            finishActive("succeeded", "", "Condition met: " + condition, tick);
            return;
        }

        long elapsed = tick - actionStartTick;
        if (elapsed >= waitUntilTimeoutTicks) {
            finishActive("failed", "timeout", "Condition timeout: " + condition, tick);
        }
    }

    private void handleMouseMovePoint(JsonObject payload, long tick) {
        if (!ensureRemoteInput(tick)) {
            return;
        }

        if (moveFuture == null) {
            int x = readInt(payload, "x", -1);
            int y = readInt(payload, "y", -1);
            if (x < 0 || y < 0) {
                finishActive("failed", "invalid_payload", "mouse_move_point requires x and y", tick);
                return;
            }

            Point start = new Point(
                    plugin.getClient().getMouseCanvasPosition().getX(),
                    plugin.getClient().getMouseCanvasPosition().getY());
            Point destination = new Point(x, y);
            moveFuture = windmouseService.moveToPointAsync(start, destination, "harness-" + UUID.randomUUID());
            return;
        }

        if (!moveFuture.isDone()) {
            return;
        }

        try {
            MouseMovementCompletedEvent event = moveFuture.getNow(null);
            if (event == null) {
                finishActive("failed", "movement_failed", "Windmouse movement finished without event", tick);
                return;
            }
            if (event.isCancelled()) {
                finishActive("cancelled", "cancelled", "Mouse movement cancelled", tick);
                return;
            }
            finishActive("succeeded", "", "Mouse movement completed", tick);
        } catch (Exception e) {
            finishActive("failed", "movement_failed", "Windmouse movement failed", tick);
        }
    }

    private boolean handleMouseClick(JsonObject payload) {
        int x = readInt(payload, "x", Integer.MIN_VALUE);
        int y = readInt(payload, "y", Integer.MIN_VALUE);
        if (x != Integer.MIN_VALUE || y != Integer.MIN_VALUE) {
            if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || x < 0 || y < 0) {
                return false;
            }
            remoteInputService.moveMouse(x, y);
        }

        String button = readString(payload, "button", "left").toLowerCase();
        switch (button) {
            case "left":
                remoteInputService.leftClick();
                return true;
            case "right":
                remoteInputService.rightClick();
                return true;
            case "middle":
                remoteInputService.click(2);
                return true;
            default:
                return false;
        }
    }

    private boolean evaluateCondition(String condition, JsonObject payload) {
        switch (condition) {
            case "player_idle": {
                Player player = plugin.getClient().getLocalPlayer();
                return player != null && player.getAnimation() == -1;
            }
            case "player_animation_equals": {
                Player player = plugin.getClient().getLocalPlayer();
                int expectedAnimation = readInt(payload, "animation", -1);
                return player != null && player.getAnimation() == expectedAnimation;
            }
            case "inventory_full": {
                return countInventoryItems() >= 28;
            }
            case "inventory_empty": {
                return countInventoryItems() == 0;
            }
            case "menu_open": {
                boolean expected = readBoolean(payload, "open", true);
                return plugin.getClient().isMenuOpen() == expected;
            }
            case "varbit_equals": {
                int varbit = readInt(payload, "varbit", -1);
                int value = readInt(payload, "value", Integer.MIN_VALUE);
                return varbit >= 0 && value != Integer.MIN_VALUE && plugin.getClient().getVarbitValue(varbit) == value;
            }
            case "widget_visible": {
                boolean expected = readBoolean(payload, "visible", true);
                int packedWidgetId = readInt(payload, "widgetId", -1);
                if (packedWidgetId >= 0) {
                    Widget widget = plugin.getClient().getWidget(packedWidgetId);
                    boolean visible = widget != null && !widget.isHidden();
                    return visible == expected;
                }
                int group = readInt(payload, "group", -1);
                int child = readInt(payload, "child", -1);
                if (group < 0 || child < 0) {
                    return false;
                }
                Widget widget = plugin.getClient().getWidget(group, child);
                boolean visible = widget != null && !widget.isHidden();
                return visible == expected;
            }
            default:
                return false;
        }
    }

    private int countInventoryItems() {
        ItemContainer inventory = plugin.getClient().getItemContainer(InventoryID.INV);
        if (inventory == null || inventory.getItems() == null) {
            return 0;
        }

        int count = 0;
        for (Item item : inventory.getItems()) {
            if (item != null && item.getId() > 0 && item.getQuantity() > 0) {
                count++;
            }
        }
        return count;
    }

    private boolean ensureRemoteInput(long tick) {
        if (remoteInputService.isConnected()) {
            return true;
        }
        finishActive("failed", "remoteinput_disconnected", "RemoteInput is disconnected", tick);
        return false;
    }

    private void finishActive(String status, String code, String message, long tick) {
        emit(status, code, message, tick);
        activeAction = null;
        waitTicksRemaining = null;
        waitUntilTimeoutTicks = null;
        moveFuture = null;
    }

    private void emit(String status, String code, String message, long tick) {
        if (activeAction == null || statusListener == null) {
            return;
        }
        statusListener.accept(new HarnessActionStatusEvent(
                activeAction.getActionId(),
                activeAction.getType(),
                status,
                code,
                message,
                tick));
    }

    private HarnessAction parseAction(JsonObject actionJson) {
        String type = readString(actionJson, "type", "");
        if (type.trim().isEmpty()) {
            type = readString(actionJson, "kind", "");
        }
        if (type.trim().isEmpty()) {
            return null;
        }

        String actionId = readString(actionJson, "actionId", "");
        if (actionId.trim().isEmpty()) {
            actionId = "a-" + UUID.randomUUID();
        }

        JsonObject payload = actionJson.has("payload") && actionJson.get("payload").isJsonObject()
                ? actionJson.getAsJsonObject("payload").deepCopy()
                : actionJson.deepCopy();
        payload.remove("type");
        payload.remove("kind");
        payload.remove("actionId");
        return new HarnessAction(actionId, type, payload);
    }

    private String readString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Integer readKeyCode(JsonObject object) {
        int keyCode = readInt(object, "keyCode", Integer.MIN_VALUE);
        if (keyCode != Integer.MIN_VALUE) {
            return keyCode;
        }
        String key = readString(object, "key", "").trim().toLowerCase();
        if (key.isEmpty()) {
            return null;
        }
        switch (key) {
            case "esc":
            case "escape":
                return KeyEvent.VK_ESCAPE;
            case "space":
                return KeyEvent.VK_SPACE;
            case "left":
                return KeyEvent.VK_LEFT;
            case "right":
                return KeyEvent.VK_RIGHT;
            case "up":
                return KeyEvent.VK_UP;
            case "down":
                return KeyEvent.VK_DOWN;
            case "f1":
                return KeyEvent.VK_F1;
            case "f2":
                return KeyEvent.VK_F2;
            case "f3":
                return KeyEvent.VK_F3;
            case "f4":
                return KeyEvent.VK_F4;
            case "f5":
                return KeyEvent.VK_F5;
            case "f6":
                return KeyEvent.VK_F6;
            case "f7":
                return KeyEvent.VK_F7;
            case "f8":
                return KeyEvent.VK_F8;
            case "f9":
                return KeyEvent.VK_F9;
            case "f10":
                return KeyEvent.VK_F10;
            case "f11":
                return KeyEvent.VK_F11;
            case "f12":
                return KeyEvent.VK_F12;
            default:
                return null;
        }
    }

    private boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
