# Combat Bot Enhancement Plan

## Overview
This plan outlines the focused improvements for the RuneLite Runepal combat bot, prioritizing essential features for extended combat training sessions while maintaining code quality and client stability.

## Current Combat Bot Analysis

### Existing Features
- Automated NPC targeting and attacking
- Basic health management with food consumption (50% threshold)
- FSM-based combat states (IDLE, FINDING_NPC, ATTACKING, EATING, etc.)
- Target selection with distance prioritization and smart filtering
- Visual debugging overlays
- Support for common food types (lobster, shark, monkfish, etc.)

### Current Limitations
- No potion consumption support
- Placeholder looting system
- No banking integration for supply management
- No prayer management

## Priority Features for Implementation

### Phase 1: Core Infrastructure Enhancements

#### 1. Advanced Potion System
**Priority:** High - Foundational for sustained combat

**Implementation Details:**
- Create `PotionService.java` for centralized potion management
- Define potion categories with item IDs:
  ```java
  enum PotionType {
      PRAYER_POTION(2434, 139, 141, 143),  // 4-dose to 1-dose
      SUPER_COMBAT(12695, 12697, 12699, 12701),
      ANTIPOISON(175, 177, 179, 181),
      ENERGY(3016, 3018, 3020, 3022)
  }
  ```
- Add new `CombatState.DRINKING_POTION` to FSM
- Configuration options:
  - Prayer potion threshold (prayer points %)
  - Combat potion usage trigger (on combat start)
  - Antipoison usage (when poisoned status detected)
- Use `ScheduledExecutorService` for potion consumption delays

#### 2. Banking Integration
**Priority:** High - Enables extended combat sessions

**Implementation Details:**
- Create `SupplyManager.java` to monitor consumable quantities
- Define supply thresholds:
  ```java
  private int minFoodCount = 5;
  private int minPrayerPotions = 2;
  private int minCombatPotions = 1;
  ```
- Integrate with existing `BankTask` and `WalkTask`:
  ```java
  if (suppliesLow()) {
      taskManager.pushTask(new BankTask(getBankLocation()));
      taskManager.pushTask(new WalkTask(getCombatLocation()));
  }
  ```
- Configuration: Banking locations, supply quantities, banking triggers
- Bank task sequence: Walk to bank → Bank items → Withdraw supplies → Walk back

#### 3. Prayer Management System
**Priority:** High - Major combat enhancement

**Implementation Details:**
- Create `PrayerService.java` with prayer monitoring
- Prayer automation features:
  - Auto-activate offensive/defensive prayers based on configuration
  - Monitor prayer points (threshold: 10-20%)
  - Auto-deactivate prayers when points critically low
- Integration with prayer potion consumption
- Configuration options:
  - Prayer preferences (Piety, Ultimate Strength, etc.)
  - Activation triggers (combat start, specific NPCs)
  - Prayer point thresholds
- Use existing prayer point API: `client.getBoostedSkillLevel(Skill.PRAYER)`

### Phase 2: Combat Optimization Features

#### 4. Enhanced Loot Collection
**Priority:** Medium - Improves profitability

**Implementation Details:**
- Replace placeholder `doLooting()` with functional implementation:
  ```java
  private void doLooting() {
      List<TileItem> valuableItems = getValuableLoot();
      if (!valuableItems.isEmpty() && hasInventorySpace()) {
          pickupHighestValueItem(valuableItems);
      }
  }
  ```
- Loot filtering system:
  - Configurable loot value thresholds (GP value)
  - Item whitelist/blacklist functionality
  - Inventory space management
  - Distance-based loot prioritization
- Integration with existing ground item detection
- Configuration: Value thresholds, item filters, pickup behavior

## Updated Implementation Priority

1. **Potion System** (foundational for all other features)
2. **Banking Integration** (enables longer combat sessions)
3. **Prayer Management** (significant combat enhancement)
4. **Enhanced Looting** (improves training profitability)

## Technical Architecture

### Code Organization
- Maintain existing `CombatTask.java` as main coordinator
- Create specialized service classes:
  - `PotionService.java` - Potion detection and consumption
  - `PrayerService.java` - Prayer activation and monitoring
  - `SupplyManager.java` - Inventory and supply tracking
- Extend existing configuration system in `BotConfig` and `CombatBotPanel`
- Preserve FSM architecture, adding states as needed

### Integration Points
- Leverage existing `TaskManager` for seamless task transitions
- Use `EntityService` and `GameService` utilities for game state reading
- Integrate with `ActionService` for hybrid input support (canvas/Python server)
- Extend existing overlay system for visual debugging
- Utilize `HumanizerService` for realistic action delays

### Threading and Timing Considerations
- **CRITICAL:** Never use `Thread.sleep()` as it freezes the client thread
- Use `ScheduledExecutorService` for all timed actions:
  ```java
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  
  // Example: Delayed potion consumption
  executor.schedule(() -> {
      consumePotion(potionType);
  }, humanizedDelay, TimeUnit.MILLISECONDS);
  ```
- Implement proper cleanup in plugin shutdown to avoid resource leaks
- Use atomic operations for thread-safe state management

### Configuration Enhancements

#### New CombatBotPanel Options
```java
// Potion settings
JSpinner prayerPotionThreshold;
JCheckBox useCombatPotions;
JCheckBox useAntipoison;

// Banking settings  
JSpinner minFoodCount;
JSpinner minPotionCount;
JTextField bankLocation;

// Prayer settings
JCheckBox usePrayers;
JComboBox<String> offensivePrayer;
JComboBox<String> defensivePrayer;
JSpinner prayerPointThreshold;

// Loot settings
JSpinner lootValueThreshold;
JTextArea lootWhitelist;
JCheckBox autoLoot;
```

## Testing Strategy
- Manual testing in controlled OSRS environments
- Configuration validation for edge cases
- Performance testing for extended combat sessions
- Thread safety verification for concurrent operations
- Resource cleanup validation

## Success Metrics
- Extended combat sessions without manual intervention
- Efficient supply management with minimal banking trips
- Optimal prayer usage for enhanced combat effectiveness
- Profitable loot collection without inventory issues
- Stable performance without client freezing or crashes

This refined plan focuses on the core features needed for an effective combat training bot while maintaining code quality and client stability.