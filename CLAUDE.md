# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a RuneLite plugin called "Runepal" that implements automated bot behaviors for Old School RuneScape.
The source-of-truth input architecture is **RemoteInput** via native library bindings (JNA), not the old Python named-pipe server.

## Build and Development Commands

Run commands from repository root.
Use `gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

### Java Runtime Requirement (Important)

- Run Gradle with JDK 11 (`JAVA_HOME` should point to a Java 11 installation).
- IntelliJ can look healthy while terminal Gradle fails if IntelliJ uses Project SDK 11 but the shell `JAVA_HOME` points to a newer JDK.
- Common wrong-JDK failures include:
  - `Unsupported class file major version 69`
  - `ExceptionInInitializerError` from Lombok (`TypeTag :: UNKNOWN`)

Example one-off override from Git Bash:
```bash
JAVA_HOME="$HOME/.jdks/ms-11.0.30" ./gradlew compileJava
```

### Core Gradle Commands
```bash
# discover tasks
./gradlew tasks --all

# compile main code
./gradlew compileJava

# compile tests
./gradlew compileTestJava

# run tests
./gradlew test

# run all verification lifecycle checks
./gradlew check

# full build
./gradlew build

# clean outputs
./gradlew clean

# build runnable fat jar
./gradlew shadowJar
```

### Running a Single Test
```bash
# single test class
./gradlew test --tests "com.runepal.ExampleTest"

# single test method
./gradlew test --tests "com.runepal.ExampleTest.testSpecificScenario"
```

Notes:
- `src/test/java/com/runepal/RunepalTest.java` is launcher-style, not a typical assertion-heavy JUnit suite.
- Manual in-game validation is still required for many behavior changes.

## Architecture

### Core Components

1. **RunepalPlugin.java** - Main plugin lifecycle and service wiring
2. **TaskManager.java** - Stack-based task execution manager
3. **BotTask.java** - Task lifecycle contract for all bot tasks
4. **ActionService.java** - High-level action APIs used by tasks
5. **RemoteInputService.java** - Native RemoteInput injection/connection and IO operations
6. **WindmouseService.java** - Human-like mouse movement using RemoteInput
7. **EventService.java** - Internal pub/sub event bus

### Task System

The plugin uses modular tasks with finite-state-machine style flow where appropriate.
Common task classes include:

- **MiningTask**
- **CombatTask**
- **FishingTask**
- **WoodcuttingTask**
- **SandCrabTask**
- **GemstoneCrabTask**
- **HighAlchTask**
- **WalkTask**
- **BankTask**

Tasks are pushed onto a stack by `TaskManager` to handle composed flows (for example gather -> bank -> return).

### Input Architecture (Source of Truth)

- Input is executed through **RemoteInputService** and related services.
- `RunepalPlugin` initializes `RemoteInputService` during startup and attempts to inject/connect.
- Mouse movement is routed through `WindmouseService`, which sends movement to RemoteInput.
- Key and click operations route through `ActionService` to RemoteInput-backed operations.

Legacy Python named-pipe workflow is not part of active instructions and should not be treated as current architecture.

## Important Configuration

- Plugin name: "Runepal"
- Main class: `com.runepal.RunepalPlugin`
- Bot types: `MINING_BOT`, `COMBAT_BOT`, `FISHING_BOT`, `WOODCUTTING_BOT`, `SAND_CRAB_BOT`, `GEMSTONE_CRAB_BOT`, `HIGH_ALCH_BOT`
- Pathfinding code is in-repo under `src/main/java/com/runepal/shortestpath/**`
- Java target: 11
- Native dependency expected by JNA: `src/main/resources/win32-x86-64/libRemoteInput.dll`

## Development Notes

- Keep event/tick loop work non-blocking.
- Preserve task lifecycle symmetry (`onStart` subscriptions and `onStop` cleanup).
- Use `HumanizerService` for timing variance instead of ad hoc sleeps when practical.
- Prefer SLF4J placeholder logging (`log.info("... {}", value)`).
- Manual game-client verification is required for interaction-heavy changes.

## Key Files to Understand

- `src/main/java/com/runepal/RunepalPlugin.java` - Entry point, startup/shutdown, task orchestration
- `src/main/java/com/runepal/TaskManager.java` - Task stack behavior
- `src/main/java/com/runepal/ActionService.java` - Action dispatch and interaction APIs
- `src/main/java/com/runepal/services/RemoteInputService.java` - Native input lifecycle and primitives
- `src/main/java/com/runepal/services/WindmouseService.java` - Mouse path generation and movement execution
- `src/main/java/com/runepal/MiningTask.java` - Representative complex FSM task
