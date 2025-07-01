package com.example;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.widgets.Widget;
import shortestpath.WorldPointUtil;
import shortestpath.pathfinder.Pathfinder;
import shortestpath.pathfinder.PathfinderConfig;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

@Slf4j
public class WalkTask implements BotTask {

    private enum WalkState {
        IDLE,
        CALCULATING_PATH,
        WALKING,
        FAILED,
        FINISHED
    }

    @Getter
    private final WorldPoint destination;
    private final RunepalPlugin plugin;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;

    private WalkState currentState = WalkState.IDLE;
    private List<WorldPoint> path;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;
    private final ActionService actionService;

    public WalkTask(RunepalPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination, ActionService actionService) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.pathfinderConfig = pathfinderConfig;
        this.destination = destination;
        ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("walk-task-%d").build();
        this.pathfinderExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
        this.actionService = actionService;
    }

    @Override
    public void onStart() {
        log.info("Starting walk task to {}", destination);
        this.currentState = WalkState.IDLE;
    }

    @Override
    public void onLoop() {
        switch (currentState) {
            case IDLE:
                calculatePath();
                break;
            case CALCULATING_PATH:
                checkPathCalculation();
                break;
            case WALKING:
                handleWalking();
                break;
            default:
                break;
        }
    }

    private void calculatePath() {
        WorldPoint start = client.getLocalPlayer().getWorldLocation();
        if (start.distanceTo(destination) < 2) {
            log.info("Already at destination.");
            currentState = WalkState.FINISHED;
            return;
        }

        log.info("Calculating path from {} to {}", start, destination);
        int startPacked = WorldPointUtil.packWorldPoint(start);
        int endPacked = WorldPointUtil.packWorldPoint(destination);

        pathfinderConfig.refresh();
        pathfinder = new Pathfinder(pathfinderConfig, startPacked, Collections.singleton(endPacked));
        pathfinderFuture = pathfinderExecutor.submit(pathfinder);
        currentState = WalkState.CALCULATING_PATH;
    }

    private void checkPathCalculation() {
        if (pathfinder == null || !pathfinder.isDone()) {
            return;
        }

        List<Integer> resultPath = pathfinder.getPath();
        if (resultPath.isEmpty()) {
            log.warn("No path found to {}", destination);
            currentState = WalkState.FAILED;
            return;
        }

        this.path = resultPath.stream()
                .map(WorldPointUtil::unpackWorldPoint)
                .collect(Collectors.toList());
        log.info("Path calculated with {} steps.", this.path.size());
        currentState = WalkState.WALKING;
    }

    private void handleWalking() {
        WorldPoint currentLocation = client.getLocalPlayer().getWorldLocation();

        if (currentLocation.distanceTo(destination) < 2) {
            log.info("Arrived at destination {}", destination);
            currentState = WalkState.FINISHED;
            return;
        }

        path.removeIf(p -> p.distanceTo(currentLocation) < 5 && p.getPlane() == currentLocation.getPlane());
        if (path.isEmpty()) {
            log.warn("Path became empty before reaching destination. Recalculating...");
            currentState = WalkState.IDLE;
            return;
        }

        if (client.getLocalDestinationLocation() == null) {
            WorldPoint target = getNextMinimapTarget();
            if (target != null) {
                walkTo(target);
            } else {
                log.warn("Could not find a reachable target on the minimap. Recalculating path.");
                currentState = WalkState.IDLE;
            }
        }
    }

    private WorldPoint getNextMinimapTarget() {
        for (int i = path.size() - 1; i >= 0; i--) {
            WorldPoint point = path.get(i);
            if (isPointOnMinimap(point)) {
                return point;
            }
        }
        return path.get(0);
    }

    private boolean isPointOnMinimap(WorldPoint point) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), point);
        if (localPoint == null) {
            return false;
        }

        Point minimapPoint = Perspective.localToMinimap(client, localPoint);
        if (minimapPoint == null) {
            return false;
        }

        Widget minimapWidget = getMinimapDrawWidget();
        if (minimapWidget == null) {
            return false;
        }

        java.awt.Rectangle bounds = minimapWidget.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        int radius = Math.min(bounds.width / 2, bounds.height / 2);

        int distanceSq = (minimapPoint.getX() - centerX) * (minimapPoint.getX() - centerX) +
                         (minimapPoint.getY() - centerY) * (minimapPoint.getY() - centerY);

        int radiusSq = (radius - 5) * (radius - 5);

        return distanceSq <= radiusSq;
    }

    private Widget getMinimapDrawWidget() {
        Widget minimapWidget = client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
        }
        if (minimapWidget == null)
        {
            minimapWidget = client.getWidget(InterfaceID.Toplevel.MINIMAP);
        }
        return minimapWidget;
    }

    @Override
    public void onStop() {
        log.info("Stopping walk task.");
        if (pathfinderFuture != null && !pathfinderFuture.isDone()) {
            pathfinderFuture.cancel(true);
        }
        pathfinderExecutor.shutdownNow();
    }

    @Override
    public boolean isFinished() {
        return currentState == WalkState.FINISHED || currentState == WalkState.FAILED;
    }

    @Override
    public boolean isStarted() {
        if (currentState == null) {
            return false;
        }
        return true;
    }

    @Override
    public String getTaskName() {
        return "Walking to " + destination.toString();
    }

    public void walkTo(WorldPoint worldPoint) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getWorldView(-1), worldPoint);
        if (localPoint != null) {
            net.runelite.api.Point minimapPoint = Perspective.localToMinimap(client, localPoint);
            if (minimapPoint != null) {
                log.info("Requesting walk to {} via minimap click at {}", worldPoint, minimapPoint);
                actionService.sendClickRequest(new java.awt.Point(minimapPoint.getX(), minimapPoint.getY()), true);
            } else {
                log.warn("Cannot walk to {}: not visible on minimap.", worldPoint);
            }
        } else {
            log.warn("Cannot walk to {}: not in scene.", worldPoint);
        }
    }
} 