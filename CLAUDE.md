# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a RuneLite plugin called "Runepal" that implements an automated bot system for Old School RuneScape. It uses a hybrid architecture with a Java RuneLite plugin (the "brain") that makes decisions and a Python automation server (the "hands") that executes OS-level commands via named pipes.

## Build and Development Commands

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Creating Shadow JAR
```bash
./gradlew shadowJar
```

### Running the Python Automation Server
```bash
cd automation_server
python main.py
```

### Installing Python Dependencies
```bash
cd automation_server
pip install -r requirements.txt
```

## Architecture

### Core Components

1. **RunepalPlugin.java** - Main plugin class that manages the overall system
2. **TaskManager.java** - Manages a stack-based task execution system
3. **BotTask interface** - Base interface for all bot activities
4. **PipeService.java** - Handles named pipe communication with Python server
5. **automation_server/main.py** - Python server that executes OS-level automation

### Task System

The codebase uses a modular task-based architecture where complex bot behaviors are broken down into reusable tasks:

- **MiningTask** - Handles mining operations with FSM states (FINDING_ROCK, MINING, CHECK_INVENTORY, etc.)
- **CombatTask** - Manages combat with NPCs including health management
- **WalkTask** - Handles pathfinding and movement
- **BankTask** - Manages banking operations

Tasks are pushed onto a stack managed by TaskManager, allowing for complex sequences like "mine -> walk to bank -> bank -> walk back to mine".

### Communication Architecture

- **Java Plugin** (Brain): Reads game state, makes decisions, calculates coordinates
- **Python Server** (Hands): Receives JSON commands via named pipes, executes background input
- **Named Pipe**: `\\.\pipe\Runepal` for Windows IPC between Java and Python

### Key Services

- **GameService** - Utilities for reading game state (inventory, player status, object finding)
- **ActionService** - Handles sending commands to the Python automation server
- **EventService** - Pub/sub system for game events
- **HumanizerService** - Adds human-like delays and randomization

## Important Configuration

- Plugin name: "Runepal"
- Main class: `com.example.RunepalPlugin`
- Supported bot types: MINING_BOT, COMBAT_BOT
- Uses `shortest-path` submodule for pathfinding
- Requires Java 11+ and Python 3.8+

## Development Notes

- The plugin includes extensive overlays for debugging (rock highlighting, status display, etc.)
- All automation actions go through the Python server for background execution
- The system includes safety features like logout detection and error recovery
- Uses Lombok for reducing boilerplate code
- All bot logic is event-driven and non-blocking

## Key Files to Understand

- `RunepalPlugin.java` - Entry point and service coordination
- `TaskManager.java` - Core task execution system
- `MiningTask.java` - Example of complex FSM-based task
- `automation_server/main.py` - Python automation server
- `PipeService.java` - Java-Python communication layer