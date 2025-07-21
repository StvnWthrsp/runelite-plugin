# Woodcutting Bot Implementation Plan

## Overview
Add a woodcutting bot following the existing mining bot pattern. The bot will cut trees until inventory is full, navigate to bank, deposit items, and return to continue cutting.

## Architecture Analysis

### Existing Patterns Identified
1. **Task-based FSM Architecture**: All bots use finite state machines with states like FINDING_TARGET, INTERACTING, WAIT_INTERACTION, CHECK_INVENTORY, BANKING, WALKING
2. **Configuration System**: BotConfig interface with hidden settings, enum-based modes, UI panels for user interaction
3. **Service Layer**: ActionService for interactions, GameService for game state, TaskManager for task orchestration
4. **Navigation**: WalkTask for pathfinding, BankTask for banking operations
5. **Event-Driven**: EventService for game events, subscriptions in onStart(), unsubscriptions in onStop()

### Key Components to Implement

## 1. Core Task Implementation

### 1.1 WoodcuttingTask.java
**Pattern**: Copy MiningTask structure exactly, adapt for woodcutting

**FSM States**:
```java
enum WoodcuttingState {
    IDLE,
    FINDING_TREE,
    CUTTING,
    WAIT_CUTTING,
    HOVER_NEXT_TREE,
    CHECK_INVENTORY,
    DROPPING,
    WALKING_TO_BANK,
    BANKING,
    WALKING_TO_TREES,
    WAITING_FOR_SUBTASK
}
```

**Key Methods**:
- `doFindingTree()` - Find nearest tree using configurable tree IDs
- `doCutting()` - Interact with tree using ActionService.interactWithGameObject()
- `doWaitCutting()` - Wait for woodcutting animation, detect completion via XP gain
- `doHoverNextTree()` - Pre-target next tree for efficiency
- `doCheckInventory()` - Check if inventory full, decide banking vs dropping
- `doDropping()` - Use ActionService.powerDrop() for power chopping mode

**Event Handlers**:
- `onStatChanged()` - Track woodcutting XP gains to detect completion
- `onInteractingChanged()` - Log interaction state changes
- `onGameTick()` - Handle per-tick logic

**Tree Validation**: 
- `isTreeStillValid()` - Check if tree still exists using scene collision detection
- `findNextBestTree()` - Find optimal next tree, prioritize adjacent trees

### 1.2 Tree and Log IDs
**Tree Object IDs** (from RuneLite API research):
- Regular trees: 1276, 1277, 1278, 1279, 1280
- Oak: 1281, 4540, 10820
- Willow: 1308, 10829, 10831, 10833
- Maple: 1307, 10832, 36681
- Yew: 1309, 10823, 36683
- Magic: 1306, 10834, 10835
- Teak: 36686
- Mahogany: 36688

**Log Item IDs**:
- Regular logs: 1511
- Oak logs: 1521
- Willow logs: 1519
- Maple logs: 1517
- Yew logs: 1515
- Magic logs: 1513
- Teak logs: 6333
- Mahogany logs: 6332

## 2. Configuration System

### 2.1 WoodcuttingMode.java
```java
public enum WoodcuttingMode {
    POWER_CHOP,  // Drop logs
    BANK         // Bank logs
}
```

### 2.2 BotConfig.java Extensions
Add woodcutting-specific configuration options:

```java
// Woodcutting mode selection
@ConfigItem(keyName = "woodcuttingMode", name = "Woodcutting Mode", 
           description = "Choose between power chopping and banking", 
           position = 40, hidden = true)
default WoodcuttingMode woodcuttingMode() { return WoodcuttingMode.POWER_CHOP; }

// Tree types to cut
@ConfigItem(keyName = "treeTypes", name = "Tree Types",
           description = "Comma-separated list of tree types (e.g., Oak, Willow)",
           position = 41, hidden = true)
default String treeTypes() { return "Oak"; }

// Banking location
@ConfigItem(keyName = "woodcuttingBank", name = "Bank Name",
           description = "Bank location for banking mode",
           position = 42, hidden = true)
default String woodcuttingBank() { return "VARROCK_EAST"; }

// Debugging section
@ConfigSection(name = "Woodcutting Bot - Debugging", 
              description = "Visual debugging options",
              position = 50)
String woodcuttingDebugSection = "woodcuttingDebugging";

@ConfigItem(keyName = "highlightTargetTree", name = "Highlight Target Tree",
           description = "Visually highlight the targeted tree",
           position = 0, section = woodcuttingDebugSection)
default boolean highlightTargetTree() { return false; }
```

### 2.3 BotType.java Extension
```java
public enum BotType {
    MINING_BOT("Mining"),
    COMBAT_BOT("Combat"),
    FISHING_BOT("Fishing"),
    WOODCUTTING_BOT("Woodcutting"),  // Add this line
    ;
}
```

## 3. UI Components

### 3.1 WoodcuttingBotPanel.java
**Pattern**: Copy MiningBotPanel structure exactly

**UI Components**:
- `JComboBox<WoodcuttingMode>` for mode selection
- `JTextField` for tree types input
- `JComboBox<Banks>` for bank selection
- Status display and start/stop button
- Configuration persistence via ConfigManager

**Key Methods**:
- `createConfigurationPanel()` - Build UI with tree type selection, mode, bank
- `loadConfigurationValues()` - Load saved configuration
- `setStatus()` and `setButtonText()` - Update UI state

### 3.2 BotPanel.java Integration
Add woodcutting case to `updateContentPanel()`:

```java
case WOODCUTTING_BOT:
    currentBotPanel = createWoodcuttingBotPanel();
    break;
```

Add `createWoodcuttingBotPanel()` method.

## 4. Plugin Integration

### 4.1 RunepalPlugin.java Extensions
**New Methods**:
- `getTreeIds()` - Parse tree types config into ObjectID array
- `getLogIds()` - Parse tree types into corresponding log ItemID array
- `startWoodcuttingBot()` - Initialize and start woodcutting task
- `setTargetTree()` - Store target tree for overlay highlighting

**Task Creation**:
```java
case WOODCUTTING_BOT:
    BotTask woodcuttingTask = new WoodcuttingTask(this, config, taskManager, 
        pathfinderConfig, actionService, gameService, eventService, humanizerService);
    taskManager.pushTask(woodcuttingTask);
    break;
```

### 4.2 Overlay Integration
Add tree highlighting overlay following the same pattern as rock highlighting:
- Green highlight for detected trees
- Yellow highlight for currently targeted tree
- Optional debug information display

## 5. Implementation Approach

### 5.1 Recommended Implementation Strategy
1. **Copy and Adapt**: Start by copying MiningTask.java to WoodcuttingTask.java
2. **Find and Replace**: Replace mining-specific terms with woodcutting equivalents
3. **Update IDs**: Replace rock/ore IDs with tree/log IDs
4. **Test Incrementally**: Test each state transition independently
5. **Add Configuration**: Implement UI and configuration after core logic works

### 5.2 Key Differences from Mining
- **Animation Detection**: Use woodcutting animation instead of mining
- **XP Tracking**: Track Skill.WOODCUTTING instead of Skill.MINING
- **Object Interaction**: Trees vs rocks (same ActionService.interactWithGameObject pattern)
- **Item Management**: Logs vs ores (same inventory management pattern)

### 5.3 Tree-Specific Considerations
- **Tree Depletion**: Trees respawn after being cut, need validation logic
- **Tree Types**: Different trees have different XP rates and respawn times
- **Location Variance**: Trees are found in different locations than rocks

## 6. Testing Strategy

### 6.1 Unit Testing Approach
1. **State Machine Testing**: Test each FSM state independently
2. **Tree Detection**: Test tree finding logic with different tree types
3. **Banking Integration**: Test banking sequence with WalkTask and BankTask
4. **Configuration Testing**: Test UI configuration persistence

### 6.2 Integration Testing
1. **Full Bot Loop**: Test complete cut->bank->return cycle
2. **Mode Switching**: Test both power chopping and banking modes
3. **Tree Type Switching**: Test different tree types and locations
4. **Error Recovery**: Test behavior when trees are unavailable

## 7. Configuration Examples

### 7.1 Power Chopping Setup
- Mode: POWER_CHOP
- Tree Types: "Willow"
- Location: Draynor Village willows
- Expected behavior: Cut willows, drop logs, repeat

### 7.2 Banking Setup
- Mode: BANK
- Tree Types: "Yew"
- Bank: "VARROCK_EAST"
- Expected behavior: Cut yews, bank logs, return to trees

## 8. Future Enhancements

### 8.1 Possible Improvements
- **Multi-tree Support**: Cut multiple tree types in same session
- **Optimal Tree Selection**: Choose best tree based on XP/profit
- **Forestry Integration**: Support for forestry events and mechanics
- **Advanced Banking**: Selective banking based on log value

### 8.2 Performance Optimizations
- **Pre-targeting**: Hover next tree while cutting current
- **Efficient Pathfinding**: Use shortest-path for optimal navigation
- **Smart Dropping**: Drop in efficient patterns for power chopping

## 9. Files to Create/Modify

### New Files:
1. `WoodcuttingTask.java` - Core task implementation
2. `WoodcuttingBotPanel.java` - UI panel
3. `WoodcuttingMode.java` - Mode enum

### Modified Files:
1. `BotConfig.java` - Add woodcutting configuration
2. `BotType.java` - Add WOODCUTTING_BOT enum
3. `BotPanel.java` - Add woodcutting panel integration
4. `RunepalPlugin.java` - Add woodcutting bot support

## 10. Implementation Timeline

### Phase 1: Core Logic (2-3 hours)
- Create WoodcuttingTask with basic FSM
- Implement tree finding and cutting logic
- Add XP tracking and completion detection

### Phase 2: Inventory Management (1-2 hours)
- Add power chopping mode
- Integrate banking with existing BankTask
- Add navigation between trees and bank

### Phase 3: UI and Configuration (1-2 hours)
- Create WoodcuttingBotPanel
- Add configuration options to BotConfig
- Update BotPanel integration

### Phase 4: Testing and Polish (1-2 hours)
- Test different tree types and modes
- Add debugging overlays
- Fix any edge cases or issues

**Total Estimated Time**: 5-9 hours for complete implementation

This plan follows the established patterns exactly while adapting them for woodcutting functionality. The implementation will be consistent with the existing codebase and provide a solid foundation for the woodcutting bot.