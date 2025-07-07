# Architecture Review & Refactoring Plan

## Overview

This document provides a comprehensive refactoring plan to address 12 critical architecture issues identified in the Runepal RuneLite plugin codebase. Each section includes detailed step-by-step instructions with specific line numbers, method signatures, and file structures.

---

## **HIGH PRIORITY REFACTORINGS**

### 1. **God Object: Split GameService.java into Focused Services**

**Current Issue**: GameService.java (522 lines) handles too many responsibilities:
- Game state reading (lines 37-52)
- Entity management (lines 53-310)
- Click point generation (lines 105-271)
- Animation checking (lines 487-521)
- Inventory management (lines 37-47, 82-103, 468-480)

#### **Step-by-Step Plan:**

**1.1 Create GameStateService.java**
```java
package com.example.services;

@Singleton
@Slf4j
public class GameStateService {
    private final Client client;
    
    @Inject
    public GameStateService(Client client) {
        this.client = client;
    }
    
    // Move these methods from GameService:
    // - isInventoryFull() (lines 37-41)
    // - isInventoryEmpty() (lines 43-47)
    // - isPlayerIdle() (lines 49-51)
    // - getInventoryItemId() (lines 82-87)
    // - getInventoryItemPoint() (lines 89-95)
    // - getBankItemPoint() (lines 97-103)
    // - hasItem() (lines 468-480)
    // - getCurrentAnimation() (lines 487-489)
    // - isCurrentAnimation() (lines 497-499)
    // - isCurrentlyMining() (lines 506-521)
    // - getPlayerLocation() (lines 422-424)
}
```

**1.2 Create EntityService.java**
```java
package com.example.services;

@Singleton
@Slf4j
public class EntityService {
    private final Client client;
    private final RunepalPlugin plugin;
    
    @Inject
    public EntityService(Client client, RunepalPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
    }
    
    // Move these methods from GameService:
    // - findNearestGameObject() (lines 53-80)
    // - findNearestNpc() (lines 170-214)
    // - findNearest() (lines 223-233)
    // - getAllInteractables() (lines 279-310)
    // - findNearestGameObjectNew() (lines 321-331)
    // - findNearestNpcNew() (lines 340-378)
    // - findNearestNpc(int npcId) (lines 432-442)
    // - findNearestGameObject(int gameObjectId) (lines 450-460)
}
```

**1.3 Create ClickService.java (addresses finding #3)**
```java
package com.example.services;

@Singleton
@Slf4j
public class ClickService {
    private final Random random = new Random();
    
    // Move and unify these methods from GameService:
    // - getRandomClickablePoint(NPC npc) (lines 105-112)
    // - getRandomClickablePoint(GameObject gameObject) (lines 114-140)
    // - getRandomClickablePoint(TileObject tileObject) (lines 142-168)
    // - getRandomClickablePoint(Interactable interactable) (lines 242-271)
    // - getRandomPointInBounds() (lines 389-396)
    
    public Point getRandomClickablePoint(Object entity) {
        if (entity instanceof NPC) {
            return getClickablePointForNpc((NPC) entity);
        } else if (entity instanceof GameObject) {
            return getClickablePointForGameObject((GameObject) entity);
        } else if (entity instanceof TileObject) {
            return getClickablePointForTileObject((TileObject) entity);
        } else if (entity instanceof Interactable) {
            return getClickablePointForInteractable((Interactable) entity);
        }
        return new Point(-1, -1);
    }
    
    // Private helper methods for each entity type
}
```

**1.4 Create UtilityService.java**
```java
package com.example.services;

@Singleton
public class UtilityService {
    // Move these utility methods from GameService:
    // - isItemInList() (lines 380-387)
    // - verifyHoverAction() (lines 398-420)
}
```

**1.5 Update GameService.java**
```java
package com.example;

@Singleton
@Slf4j
public class GameService {
    private final GameStateService gameStateService;
    private final EntityService entityService;
    private final ClickService clickService;
    private final UtilityService utilityService;
    
    @Inject
    public GameService(GameStateService gameStateService, EntityService entityService, 
                      ClickService clickService, UtilityService utilityService) {
        this.gameStateService = gameStateService;
        this.entityService = entityService;
        this.clickService = clickService;
        this.utilityService = utilityService;
    }
    
    // Delegate methods that call the appropriate service
    public boolean isInventoryFull() {
        return gameStateService.isInventoryFull();
    }
    
    public GameObject findNearestGameObject(int... ids) {
        return entityService.findNearestGameObject(ids);
    }
    
    public Point getRandomClickablePoint(Object entity) {
        return clickService.getRandomClickablePoint(entity);
    }
    
    // ... other delegation methods
}
```

**1.6 Update all task classes**
- In MiningTask.java (line 30): Inject individual services instead of GameService
- In FishingTask.java (line 26): Update constructor to inject required services
- In CombatTask.java (line 25): Update constructor to inject required services
- In WalkTask.java (line 63): Update constructor to inject required services

---

### 2. **Extract CookingTask from FishingTask.java**

**Current Issue**: FishingTask.java (lines 271-330) contains cooking logic that should be reusable by other tasks.

#### **Step-by-Step Plan:**

**2.1 Create CookingTask.java**
```java
package com.example;

@Slf4j
public class CookingTask implements BotTask {
    
    private enum CookingState {
        WALKING_TO_RANGE,
        COOKING,
        WAIT_COOKING,
        FINISHED
    }
    
    private final RunepalPlugin plugin;
    private final ActionService actionService;
    private final GameService gameService;
    private final EventService eventService;
    private final WorldPoint rangeLocation;
    private final int rangeObjectId;
    private final int[] rawFishIds;
    
    private CookingState currentState = CookingState.WALKING_TO_RANGE;
    private boolean cookingStarted = false;
    private int idleTicks = 0;
    private GameObject cookingRange = null;
    
    public CookingTask(RunepalPlugin plugin, ActionService actionService, GameService gameService, 
                      EventService eventService, WorldPoint rangeLocation, int rangeObjectId, int[] rawFishIds) {
        // Constructor implementation
    }
    
    // Move these methods from FishingTask:
    // - doCooking() logic (lines 271-295)
    // - doWaitCooking() logic (lines 297-318)
    // - finishCooking() logic (lines 320-329)
    // - hasRawFish() logic (lines 374-376)
    // - getRawFishId() logic (lines 378-385)
}
```

**2.2 Update FishingTask.java**
```java
// Remove lines 271-329 (cooking logic)
// Replace with:

private void doWalkingToCooking() {
    WorldPoint playerLocation = gameService.getPlayerLocation();
    if (playerLocation.distanceTo(LUMBRIDGE_KITCHEN_RANGE) <= 10) {
        log.info("Arrived at kitchen, starting cooking task");
        CookingTask cookingTask = new CookingTask(plugin, actionService, gameService, eventService,
                LUMBRIDGE_KITCHEN_RANGE, KITCHEN_RANGE_ID, 
                new int[]{RAW_SHRIMP_ID, RAW_ANCHOVIES_ID});
        taskManager.pushTask(cookingTask);
        currentState = FishingState.WAITING_FOR_SUBTASK;
    } else {
        log.info("Walking to Lumbridge Castle kitchen");
        taskManager.pushTask(new WalkTask(plugin, pathfinderConfig, LUMBRIDGE_KITCHEN_RANGE, actionService, gameService));
        currentState = FishingState.WAITING_FOR_SUBTASK;
    }
}

// Remove these methods (move to CookingTask):
// - doCooking() (lines 271-295)
// - doWaitCooking() (lines 297-318)  
// - finishCooking() (lines 320-329)
// - hasRawFish() (lines 374-376)
// - getRawFishId() (lines 378-385)
```

---

### 3. **Unify Click Point Generation Logic**

**Current Issue**: Duplicated click point generation in GameService.java (lines 105-168, 242-271).

#### **Step-by-Step Plan:**

**3.1 Already addressed in GameService refactoring above (section 1.3)**

The ClickService.java created in section 1.3 consolidates all click point generation logic into a single, generic service.

---

### 4. **Simplify WalkTask.handleWalking() Method**

**Current Issue**: WalkTask.handleWalking() method (lines 179-334) is 155 lines and handles multiple responsibilities.

#### **Step-by-Step Plan:**

**4.1 Create WalkTaskHandlers package**
```
src/main/java/com/example/walking/
├── TransportHandler.java
├── DoorHandler.java
├── StairsHandler.java
└── PathHandler.java
```

**4.2 Create TransportHandler.java**
```java
package com.example.walking;

@Slf4j
public class TransportHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    
    // Move these methods from WalkTask:
    // - executeTransport() (lines 340-363)
    // - determineTeleportType() (lines 365-392)
    // - handleTeleportExecution() (lines 394-417)
    // - isTransportStep() (lines 862-875)
    // - getTransportInfo() (lines 882-906)
    // - executeTransportInteraction() (lines 913-938)
    
    public boolean handleTransportStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        // Extract transport detection logic from handleWalking() lines 207-231
    }
}
```

**4.3 Create DoorHandler.java**
```java
package com.example.walking;

@Slf4j
public class DoorHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    
    // Move these methods from WalkTask:
    // - findDoorBlockingPath() (lines 422-447)
    // - isMovementBlockedByDoor() (lines 452-513)
    // - findDoorObject() (lines 518-569)
    // - isClosedDoor() (lines 574-601)
    // - handleDoorOpening() (lines 770-783)
    // - handleDoorInteraction() (lines 977-989)
    
    public boolean handleDoorStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        // Extract door detection logic from handleWalking() lines 301-323
    }
}
```

**4.4 Create StairsHandler.java**
```java
package com.example.walking;

@Slf4j
public class StairsHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    
    // Move these methods from WalkTask:
    // - handleStairs() (lines 785-800)
    // - findStairObjectGeneric() (lines 1092-1132)
    // - isStairObjectGeneric() (lines 1139-1163)
    // - isLumbridgeStaircase() (lines 1170-1187)
    // - handleLumbridgeStaircase() (lines 1194-1209)
    // - findStairObject() (lines 997-1037)
    // - isStairObject() (lines 1045-1085)
    // - handleStairsOrLadder() (lines 946-969)
    
    public boolean handleStairsStep(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        // Extract stairs detection logic from handleWalking() lines 273-298
    }
}
```

**4.5 Create PathHandler.java**
```java
package com.example.walking;

@Slf4j
public class PathHandler {
    private final Client client;
    private final GameService gameService;
    private final ActionService actionService;
    
    // Move these methods from WalkTask:
    // - updatePathIndex() (lines 618-647)
    // - getNextMinimapTarget() (lines 649-686)
    // - isPointOnMinimap() (lines 688-715)
    // - getMinimapDrawWidget() (lines 717-728)
    // - walkTo() (lines 755-768)
    
    public boolean handleNormalWalking(List<WorldPoint> path, int pathIndex, WorldPoint currentLocation) {
        // Extract normal walking logic from handleWalking() lines 325-334
    }
}
```

**4.6 Refactor WalkTask.handleWalking()**
```java
// Replace lines 179-334 with:
private void handleWalking() {
    WorldPoint currentLocation = gameService.getPlayerLocation();

    // Check if we're already walking to a destination
    if (client.getLocalDestinationLocation() != null) {
        return;
    }

    // Check if we're already at the destination
    if (currentLocation.equals(destination)) {
        log.info("Already at destination.");
        currentState = WalkState.FINISHED;
        return;
    }

    // Update path index based on current location
    pathHandler.updatePathIndex(currentLocation, path, pathIndex);

    if (pathIndex >= path.size()) {
        log.warn("Reached end of path but not at destination. Recalculating...");
        currentState = WalkState.IDLE;
        return;
    }

    // Delegate to specialized handlers
    if (transportHandler.handleTransportStep(path, pathIndex, currentLocation)) {
        return;
    }
    
    if (stairsHandler.handleStairsStep(path, pathIndex, currentLocation)) {
        return;
    }
    
    if (doorHandler.handleDoorStep(path, pathIndex, currentLocation)) {
        return;
    }

    // Handle normal walking
    pathHandler.handleNormalWalking(path, pathIndex, currentLocation);
}
```

---

## **MEDIUM PRIORITY REFACTORINGS**

### 5. **Centralize Delay Logic in HumanizerService**

**Current Issue**: Duplicated `setRandomDelay()` methods in:
- MiningTask.java (lines 223-225)
- FishingTask.java (lines 199-201)
- WalkTask.java (lines 336-338)
- CombatTask.java (lines 187-189)

#### **Step-by-Step Plan:**

**5.1 Remove duplicate methods from task classes**
```java
// Delete these identical methods from all task classes:
private void setRandomDelay(int minTicks, int maxTicks) {
    delayTicks = plugin.getRandom().nextInt(maxTicks - minTicks + 1) + minTicks;
}
```

**5.2 Update HumanizerService.java (add method)**
```java
// Add to HumanizerService.java:
/**
 * Gets a random delay within a specified range (replacing old setRandomDelay pattern)
 * @param minTicks minimum delay in ticks
 * @param maxTicks maximum delay in ticks
 * @return delay in game ticks
 */
public int getRandomDelay(int minTicks, int maxTicks) {
    if (minTicks >= maxTicks) {
        return minTicks;
    }
    return getRangeDelay(minTicks, maxTicks);
}
```

**5.3 Update all task classes to use HumanizerService**
```java
// In MiningTask.java constructor (line 62):
private final HumanizerService humanizerService;

// Update constructor to inject HumanizerService:
public MiningTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, 
                  PathfinderConfig pathfinderConfig, ActionService actionService, 
                  GameService gameService, EventService eventService, HumanizerService humanizerService) {
    // ... existing assignments
    this.humanizerService = Objects.requireNonNull(humanizerService, "humanizerService cannot be null");
}

// Replace all calls to setRandomDelay() with:
delayTicks = humanizerService.getRandomDelay(minTicks, maxTicks);
```

**Repeat for FishingTask.java, WalkTask.java, and CombatTask.java**

---

### 6. **Split RunepalPlugin into Focused Components**

**Current Issue**: RunepalPlugin.java (430 lines) handles too many responsibilities:
- Plugin lifecycle (lines 107-171)
- Event handling (lines 173-227, 236-289)
- Service coordination (lines 129-140)
- UI management (lines 112-128)
- Configuration helpers (lines 299-349)
- Session tracking (lines 360-382)

#### **Step-by-Step Plan:**

**6.1 Create PluginCoordinator.java**
```java
package com.example.core;

@Singleton
@Slf4j
public class PluginCoordinator {
    private final TaskManager taskManager;
    private final BotConfig config;
    private final ConfigManager configManager;
    
    // Move these methods from RunepalPlugin:
    // - Bot start/stop logic (lines 248-288)
    // - Task management (lines 260-274)
    // - Connection management (lines 384-430)
    
    public void startBot() {
        // Extract logic from onGameTick() lines 250-277
    }
    
    public void stopBot() {
        // Move stopBot() method (lines 291-293)
    }
}
```

**6.2 Create SessionTracker.java**
```java
package com.example.core;

@Singleton
@Slf4j
public class SessionTracker {
    private long sessionStartXp = 0;
    private Instant sessionStartTime = null;
    private final Client client;
    
    // Move these methods from RunepalPlugin:
    // - getSessionXpGained() (lines 360-364)
    // - getSessionRuntime() (lines 366-370)
    // - getXpPerHour() (lines 372-382)
    // - Session initialization logic (lines 142-146, 184-189)
}
```

**6.3 Create ConfigurationHelper.java**
```java
package com.example.core;

@Singleton
public class ConfigurationHelper {
    private final BotConfig config;
    
    // Move these methods from RunepalPlugin:
    // - getRockIds() (lines 299-328)
    // - getOreIds() (lines 330-336)
    // - getBankCoordinates() (lines 338-349)
}
```

**6.4 Create EventManager.java**
```java
package com.example.core;

@Singleton
@Slf4j
public class EventManager {
    private final EventService eventService;
    
    // Move event handling methods from RunepalPlugin:
    // - onAnimationChanged() (lines 209-213)
    // - onStatChanged() (lines 216-220)
    // - onInteractingChanged() (lines 223-227)
    // - Event publishing logic (lines 203-205, 238-240)
}
```

**6.5 Update RunepalPlugin.java**
```java
@Slf4j
@PluginDescriptor(name = "Runepal")
public class RunepalPlugin extends Plugin {
    // Inject the new coordinator services
    @Inject private PluginCoordinator pluginCoordinator;
    @Inject private SessionTracker sessionTracker;
    @Inject private ConfigurationHelper configHelper;
    @Inject private EventManager eventManager;
    
    // Keep only essential plugin methods:
    // - startUp() (simplified)
    // - shutDown() (simplified)
    // - onGameStateChanged() (simplified)
    // - onGameTick() (delegate to coordinator)
    // - Overlay management
    // - UI management
    
    @Subscribe
    public void onGameTick(GameTick gameTick) {
        eventManager.publishEvent(gameTick);
        pluginCoordinator.handleGameTick();
        updateUI();
    }
}
```

---

### 7. **Standardize Event Subscription Patterns**

**Current Issue**: Identical subscribe/unsubscribe patterns across:
- MiningTask.java (lines 77-81, 89-93)
- FishingTask.java (lines 94, 102)
- CombatTask.java (lines 75-76, 90-91)

#### **Step-by-Step Plan:**

**7.1 Create EventAwareTask.java (abstract base class)**
```java
package com.example;

@Slf4j
public abstract class EventAwareTask implements BotTask {
    protected final EventService eventService;
    private final Set<Class<? extends Event>> subscribedEvents = new HashSet<>();
    
    protected EventAwareTask(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService, "eventService cannot be null");
    }
    
    protected final <T extends Event> void subscribeToEvent(Class<T> eventType, Consumer<T> handler) {
        eventService.subscribe(eventType, handler);
        subscribedEvents.add(eventType);
    }
    
    @Override
    public final void onStart() {
        subscribeToEvents();
        onTaskStart();
    }
    
    @Override
    public final void onStop() {
        unsubscribeFromAllEvents();
        onTaskStop();
    }
    
    private void unsubscribeFromAllEvents() {
        for (Class<? extends Event> eventType : subscribedEvents) {
            eventService.unsubscribe(eventType, this::handleEvent);
        }
        subscribedEvents.clear();
    }
    
    // Abstract methods for subclasses to implement
    protected abstract void subscribeToEvents();
    protected abstract void onTaskStart();
    protected abstract void onTaskStop();
}
```

**7.2 Update MiningTask.java**
```java
// Change class declaration:
public class MiningTask extends EventAwareTask {

    // Remove eventService field (inherited from base class)
    
    // Replace constructor:
    public MiningTask(RunepalPlugin plugin, BotConfig config, TaskManager taskManager, 
                     PathfinderConfig pathfinderConfig, ActionService actionService, 
                     GameService gameService, EventService eventService) {
        super(eventService);
        // ... other assignments
    }
    
    // Replace onStart() method (lines 73-81):
    @Override
    protected void subscribeToEvents() {
        subscribeToEvent(AnimationChanged.class, this::onAnimationChanged);
        subscribeToEvent(StatChanged.class, this::onStatChanged);
        subscribeToEvent(InteractingChanged.class, this::onInteractingChanged);
        subscribeToEvent(GameTick.class, this::onGameTick);
    }
    
    @Override
    protected void onTaskStart() {
        log.info("Starting Mining Task.");
        this.currentState = MiningState.FINDING_ROCK;
        this.lastMiningXp = plugin.getClient().getSkillExperience(Skill.MINING);
    }
    
    // Replace onStop() method (lines 84-93):
    @Override
    protected void onTaskStop() {
        log.info("Stopping Mining Task.");
        this.targetRock = null;
        this.nextRock = null;
        plugin.setTargetRock(null);
    }
}
```

**Repeat similar changes for FishingTask.java and CombatTask.java**

---

### 8. **Enhance BankTask for Consistent Banking**

**Current Issue**: Banking handled differently in FishingTask.java (lines 343-357) vs dedicated BankTask.

#### **Step-by-Step Plan:**

**8.1 Read current BankTask.java to understand existing interface**
```bash
# First examine the existing BankTask implementation
```

**8.2 Create enhanced BankTask with standardized interface**
```java
package com.example;

@Slf4j
public class BankTask implements BotTask {
    
    public enum BankOperation {
        DEPOSIT_ALL,
        DEPOSIT_ALL_EXCEPT,
        WITHDRAW_ITEM,
        WITHDRAW_QUANTITY,
        CLOSE_BANK
    }
    
    private final BankOperation operation;
    private final int[] itemsToKeep;  // For DEPOSIT_ALL_EXCEPT
    private final int itemIdToWithdraw;  // For WITHDRAW operations
    private final int quantityToWithdraw;  // For WITHDRAW_QUANTITY
    
    // Constructor for different banking operations
    public static BankTask depositAll() {
        return new BankTask(BankOperation.DEPOSIT_ALL, null, -1, -1);
    }
    
    public static BankTask depositAllExcept(int[] itemsToKeep) {
        return new BankTask(BankOperation.DEPOSIT_ALL_EXCEPT, itemsToKeep, -1, -1);
    }
    
    public static BankTask withdrawItem(int itemId) {
        return new BankTask(BankOperation.WITHDRAW_ITEM, null, itemId, 1);
    }
    
    public static BankTask withdrawQuantity(int itemId, int quantity) {
        return new BankTask(BankOperation.WITHDRAW_QUANTITY, null, itemId, quantity);
    }
}
```

**8.3 Update FishingTask to use enhanced BankTask**
```java
// Replace doDepositing() method (lines 343-347):
private void doDepositing() {
    log.info("Banking all items");
    taskManager.pushTask(BankTask.depositAll());
    currentState = FishingState.WAITING_FOR_SUBTASK;
}

// Replace doWithdrawing() method (lines 349-357):
private void doWithdrawing() {
    log.info("Withdrawing small fishing net");
    taskManager.pushTask(BankTask.withdrawItem(ItemID.SMALL_FISHING_NET));
    currentState = FishingState.WAITING_FOR_SUBTASK;
}
```

---

## **LOW PRIORITY REFACTORINGS**

### 9. **Remove Unused Methods and Dead Code**

#### **Step-by-Step Plan:**

**9.1 Remove unused methods from ActionService.java**
```java
// Delete these unused methods:
// - useInventoryItem() (lines 88-112) - has TODO, incomplete
// - sendUseItemOnObjectRequest() (lines 479-500) - placeholder
```

**9.2 Remove unused methods from GameService.java**
```java
// Delete these methods (replaced by unified versions):
// - findNearestGameObjectNew() (lines 321-331)
// - findNearestNpcNew() (lines 340-378)
// - verifyHoverAction() (lines 398-420) - not used anywhere
```

**9.3 Clean up UI panels**
```java
// Remove from BotPanel.java:
// - updateConnectionStatus() method if it does nothing
// - Any unused delegate methods
```

**9.4 Remove commented code**
```java
// Search for and remove any commented-out methods in overlay classes
```

---

### 10. **Create BotConstants Class**

#### **Step-by-Step Plan:**

**10.1 Create BotConstants.java**
```java
package com.example.constants;

public final class BotConstants {
    
    // Lumbridge locations (from FishingTask)
    public static final class Lumbridge {
        public static final WorldPoint SWAMP_FISHING = new WorldPoint(3241, 3149, 0);
        public static final WorldPoint KITCHEN_RANGE = new WorldPoint(3211, 3215, 0);
        public static final WorldPoint BANK = new WorldPoint(3208, 3220, 2);
    }
    
    // Varrock locations (from MiningTask)  
    public static final class Varrock {
        public static final WorldPoint EAST_MINE = new WorldPoint(3285, 3365, 0);
        public static final WorldPoint EAST_BANK = new WorldPoint(3253, 3420, 0);
    }
    
    // Object IDs
    public static final class ObjectIds {
        public static final int FISHING_SPOT = 1530;
        public static final int KITCHEN_RANGE = 114;
        // ... other hardcoded IDs from throughout the codebase
    }
    
    // Animation IDs
    public static final class Animations {
        public static final int COOKING = AnimationID.HUMAN_COOKING;
        public static final int COOKING_LOOP = AnimationID.HUMAN_COOKING_LOOP;
        // ... other animation constants
    }
    
    // Timeouts and delays (in ticks)
    public static final class Timeouts {
        public static final int COMBAT_TIMEOUT = 100;
        public static final int TELEPORT_TIMEOUT = 30000;
        public static final int GENERAL_ACTION_TIMEOUT = 10;
    }
    
    private BotConstants() {
        // Utility class
    }
}
```

**10.2 Update all classes to use constants**
```java
// In FishingTask.java, replace:
// private static final WorldPoint LUMBRIDGE_SWAMP_FISHING = new WorldPoint(3241, 3149, 0);
// with:
// Use BotConstants.Lumbridge.SWAMP_FISHING

// In MiningTask.java, replace:
// private static final WorldPoint VARROCK_EAST_MINE = new WorldPoint(3285, 3365, 0);
// with:
// Use BotConstants.Varrock.EAST_MINE

// Continue throughout codebase...
```

---

### 11. **Standardize Error Handling**

#### **Step-by-Step Plan:**

**11.1 Create BotException hierarchy**
```java
package com.example.exceptions;

public class BotException extends RuntimeException {
    public BotException(String message) {
        super(message);
    }
    
    public BotException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class BotConfigurationException extends BotException {
    public BotConfigurationException(String message) {
        super(message);
    }
}

public class BotAutomationException extends BotException {
    public BotAutomationException(String message) {
        super(message);
    }
}

public class BotPathfindingException extends BotException {
    public BotPathfindingException(String message) {
        super(message);
    }
}
```

**11.2 Replace null returns with Optional**
```java
// In GameService methods, replace patterns like:
// return null;
// with:
// return Optional.empty();

// Update method signatures:
// public GameObject findNearestGameObject(int... ids)
// becomes:
// public Optional<GameObject> findNearestGameObject(int... ids)
```

**11.3 Add consistent validation**
```java
// Add validation methods to each service:
public class GameStateService {
    
    private void validateSlot(int slot) {
        if (slot < 0 || slot > 27) {
            throw new BotConfigurationException("Invalid inventory slot: " + slot);
        }
    }
    
    private void validatePoint(Point point) {
        if (point == null || point.x == -1 || point.y == -1) {
            throw new BotAutomationException("Invalid click point");
        }
    }
}
```

---

### 12. **Decouple UI from Business Logic**

#### **Step-by-Step Plan:**

**12.1 Create BotPanelPresenter.java**
```java
package com.example.ui;

@Slf4j
public class BotPanelPresenter {
    private final BotConfig config;
    private final ConfigManager configManager;
    private final PluginCoordinator coordinator;
    private final SessionTracker sessionTracker;
    
    public void startBot() {
        coordinator.startBot();
    }
    
    public void stopBot() {
        coordinator.stopBot();
    }
    
    public String getCurrentStatus() {
        return coordinator.getCurrentState();
    }
    
    public boolean isConnected() {
        return coordinator.isConnected();
    }
    
    public SessionStats getSessionStats() {
        return new SessionStats(
            sessionTracker.getSessionXpGained(),
            sessionTracker.getSessionRuntime(),
            sessionTracker.getXpPerHour()
        );
    }
    
    public void updateConfiguration(String key, Object value) {
        configManager.setConfiguration("runepal", key, value);
    }
}
```

**12.2 Create SessionStats.java**
```java
package com.example.ui;

public class SessionStats {
    private final long xpGained;
    private final Duration runtime;
    private final String xpPerHour;
    
    public SessionStats(long xpGained, Duration runtime, String xpPerHour) {
        this.xpGained = xpGained;
        this.runtime = runtime;
        this.xpPerHour = xpPerHour;
    }
    
    // Getters...
}
```

**12.3 Update BotPanel.java**
```java
// Remove direct config manipulation
// Replace with presenter calls:

public class BotPanel extends PluginPanel {
    private final BotPanelPresenter presenter;
    
    public BotPanel(BotPanelPresenter presenter) {
        this.presenter = presenter;
    }
    
    private void onStartButtonClicked() {
        if (presenter.isRunning()) {
            presenter.stopBot();
        } else {
            presenter.startBot();
        }
    }
    
    public void updateDisplay() {
        SessionStats stats = presenter.getSessionStats();
        statusLabel.setText(presenter.getCurrentStatus());
        connectionLabel.setText(presenter.isConnected() ? "Connected" : "Disconnected");
        // ... update other UI elements
    }
}
```

---

## **IMPLEMENTATION ORDER**

### Phase 1 (Critical Foundation)
1. Split GameService into focused services (#1)
2. Create unified ClickService (#3)
3. Centralize delay logic in HumanizerService (#5)

### Phase 2 (Architecture Improvements)
4. Extract CookingTask from FishingTask (#2)
5. Simplify WalkTask.handleWalking() (#4)
6. Split RunepalPlugin responsibilities (#6)

### Phase 3 (Code Quality)
7. Standardize event subscription patterns (#7)
8. Enhance BankTask consistency (#8)
9. Remove unused code (#9)

### Phase 4 (Final Polish)
10. Create BotConstants class (#10)
11. Standardize error handling (#11)
12. Decouple UI from business logic (#12)

---

## **TESTING STRATEGY**

After each phase:

1. **Compile and verify** - Ensure all dependencies are properly injected
2. **Run existing functionality** - Test that mining, fishing, and combat still work
3. **Verify logging** - Check that debug information is still available
4. **Test error conditions** - Ensure graceful failure handling
5. **Performance check** - Verify no performance regressions

---

## **ESTIMATED EFFORT**

- **Phase 1**: 2-3 days (high complexity, foundational changes)
- **Phase 2**: 2-3 days (moderate complexity, architectural changes)  
- **Phase 3**: 1-2 days (low-medium complexity, code cleanup)
- **Phase 4**: 1-2 days (low complexity, final polish)

**Total**: 6-10 days for complete refactoring

Each phase can be completed independently, allowing for incremental improvement of the codebase.