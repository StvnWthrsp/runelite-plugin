package com.example.utils;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import javax.inject.Inject;
import java.awt.Rectangle;
import java.awt.Point;

public class ClickObstructionChecker {

    private final Client client;
    private enum CameraMode {
        STRETCH,
        RESIZABLE,
        FIXED
    }
    private CameraMode cameraMode;

    @Inject
    public ClickObstructionChecker(Client client) {
        this.client = client;
    }

    /**
     * Checks if a given screen point (canvas point) is obstructed by any visible RuneLite interface widget.
     *
     * @param canvasPoint The screen coordinates (e.g., from GameObject.getCanvasTilePoly().getBounds().getCenterX(), getCenterY())
     * or any point you intend to click on the game canvas.
     * @return true if the point is obstructed by an interface, false otherwise.
     */
    public boolean isClickObstructed(Point canvasPoint) {
        if (canvasPoint == null) {
            return true; // Or throw an IllegalArgumentException, depending on desired behavior.
        }

        Widget safeArea = client.getWidget(InterfaceID.ToplevelOsrsStretch.HUD_CONTAINER_FRONT);
        if (safeArea == null) {
            safeArea = client.getWidget(InterfaceID.ToplevelPreEoc.HUD_CONTAINER_FRONT);
            cameraMode = CameraMode.RESIZABLE;
        }
        if (safeArea == null) {
            safeArea = client.getWidget(InterfaceID.Toplevel.MAIN);
            cameraMode = CameraMode.FIXED;
        }
        if (safeArea == null) {
            return false;
        }
        cameraMode = CameraMode.STRETCH;

        if (cameraMode != CameraMode.RESIZABLE) {
            Rectangle safeAreaBounds = safeArea.getBounds();
            if (safeAreaBounds.contains(canvasPoint.getX(), canvasPoint.getY())) {
                return false;
            }
            return true;
        }

        Widget sideContainer = client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
        if (sideContainer == null) {
            return false;
        }
        Rectangle sideContainerBounds = sideContainer.getBounds();
        if (sideContainerBounds.contains(canvasPoint.getX(), canvasPoint.getY())) {
            return true;
        }
        return false;
    }
}
