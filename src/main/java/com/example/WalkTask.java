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
    private final MiningBotPlugin plugin;
    private final Client client;
    private final PathfinderConfig pathfinderConfig;

    private WalkState state = WalkState.IDLE;
    private List<WorldPoint> path;
    private Pathfinder pathfinder;
    private Future<?> pathfinderFuture;
    private final ExecutorService pathfinderExecutor;

    public WalkTask(MiningBotPlugin plugin, PathfinderConfig pathfinderConfig, WorldPoint destination) {
        this.plugin = plugin;
        this.client = plugin.getClient();
        this.pathfinderConfig = pathfinderConfig;
        this.destination = destination;
        ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("walk-task-%d").build();
        this.pathfinderExecutor = Executors.newSingleThreadExecutor(shortestPathNaming);
    }

    @Override
    public void onStart() {
        log.info("Starting walk task to {}", destination);
        this.state = WalkState.IDLE;
    }

    @Override
    public void onLoop() {
        switch (state) {
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
            state = WalkState.FINISHED;
            return;
        }

        log.info("Calculating path from {} to {}", start, destination);
        int startPacked = WorldPointUtil.packWorldPoint(start);
        int endPacked = WorldPointUtil.packWorldPoint(destination);

        pathfinderConfig.refresh();
        pathfinder = new Pathfinder(pathfinderConfig, startPacked, Collections.singleton(endPacked));
        pathfinderFuture = pathfinderExecutor.submit(pathfinder);
        state = WalkState.CALCULATING_PATH;
    }

    private void checkPathCalculation() {
        if (pathfinder == null || !pathfinder.isDone()) {
            return;
        }

        List<Integer> resultPath = pathfinder.getPath();
        if (resultPath.isEmpty()) {
            log.warn("No path found to {}", destination);
            state = WalkState.FAILED;
            return;
        }

        this.path = resultPath.stream()
                .map(WorldPointUtil::unpackWorldPoint)
                .collect(Collectors.toList());
        log.info("Path calculated with {} steps.", this.path.size());
        state = WalkState.WALKING;
    }

    private void handleWalking() {
        WorldPoint currentLocation = client.getLocalPlayer().getWorldLocation();

        if (currentLocation.distanceTo(destination) < 2) {
            log.info("Arrived at destination {}", destination);
            state = WalkState.FINISHED;
            return;
        }

        path.removeIf(p -> p.distanceTo(currentLocation) < 5 && p.getPlane() == currentLocation.getPlane());
        if (path.isEmpty()) {
            log.warn("Path became empty before reaching destination. Recalculating...");
            state = WalkState.IDLE;
            return;
        }

        if (client.getLocalDestinationLocation() == null) {
            WorldPoint target = getNextMinimapTarget();
            if (target != null) {
                plugin.walkTo(target);
            } else {
                log.warn("Could not find a reachable target on the minimap. Recalculating path.");
                state = WalkState.IDLE;
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
        if (client.isResized()) {
            if (client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP) != null) {
                return client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP);
            }
        }
        return client.getWidget(InterfaceID.Toplevel.MINIMAP);
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
        return state == WalkState.FINISHED || state == WalkState.FAILED;
    }

    @Override
    public String getTaskName() {
        return "Walking to " + destination.toString();
    }
} 