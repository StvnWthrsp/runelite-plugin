# Implement an in-plugin AI agent host with local WebSocket state streaming and template skills

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the RuneLite plugin can expose a live, local-only game snapshot stream over WebSocket and accept structured commands to run predefined automation skill templates. A user (or an in-plugin planner) can observe state changes without HTTP polling and can start or stop known-safe bot templates through one command channel. This proves the core architecture for AI-driven control while keeping real-time game actions inside deterministic Java code paths.

The user-visible demonstration is: start RuneLite with this plugin, enable the local agent server config toggle, connect a local WebSocket client to `ws://127.0.0.1:<port>`, receive periodic `snapshot` messages, send `list_skills`, then send `run_skill` for a template such as mining or woodcutting and observe the bot begin through the existing task engine.

## Progress

- [x] (2026-02-26 15:27Z) Read `.agents/PLANS.md`, `AGENTS.md`, and key architecture files to ground the plan in current code.
- [x] (2026-02-26 15:27Z) Authored this full ExecPlan as a self-contained implementation spec.
- [x] (2026-02-26 15:36Z) Implemented agent module skeleton under `src/main/java/com/runepal/agent/**` with WebSocket lifecycle, command queue, template registry, executor, and snapshot builder.
- [x] (2026-02-26 15:36Z) Integrated `AgentService` lifecycle into `src/main/java/com/runepal/RunepalPlugin.java` startup, tick loop, and shutdown.
- [x] (2026-02-26 15:36Z) Added local agent config entries in `src/main/java/com/runepal/BotConfig.java` for enable, port, stream cadence, and screenshot toggle.
- [x] (2026-02-26 15:36Z) Added dependencies in `build.gradle` for `Java-WebSocket` and explicit `gson`.
- [x] (2026-02-26 15:36Z) Added `src/test/java/com/runepal/agent/AgentSkillTemplateTest.java` for template name normalization and stop-skill semantics.
- [ ] (2026-02-26 15:36Z) Validation partially completed (completed: attempted `./gradlew compileJava`; remaining: re-run compile/tests in an environment with JDK 11 and `JAVA_HOME` configured).
- [x] (2026-02-26 15:36Z) Documented implementation outcomes, manual verification instructions, and remaining gaps in this ExecPlan.

## Surprises & Discoveries

- Observation: The container environment does not have Java available, so Gradle validation could not run here.
  Evidence:

      $ ./gradlew compileJava
      ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

## Decision Log

- Decision: Use a local-only WebSocket server bound to loopback (`127.0.0.1`) rather than exposing HTTP polling endpoints first.
  Rationale: The user asked specifically about WebSocket streaming to avoid polling and local-only operation; this directly validates that architecture.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Keep LLM-style planning out of the real-time game loop and implement template skill execution as the primary control primitive.
  Rationale: OSRS tick cadence is far faster than practical LLM round trips; deterministic template tasks preserve responsiveness.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Reuse existing bot/task infrastructure by mapping template commands to existing bot configuration and task startup logic.
  Rationale: This minimizes risk and proves viability quickly without replacing the mature action/task stack.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Queue mutating WebSocket commands and execute them on game ticks rather than executing directly on socket callback threads.
  Rationale: This keeps bot start/stop transitions on the same execution path as existing RuneLite tick-driven logic and avoids cross-thread runtime hazards.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Add `RunepalPlugin.startBotForType(BotType)` and reuse a shared `startTaskForBotType` switch.
  Rationale: Agent template runs must restart/swap tasks deterministically even when a bot is already active; centralizing startup logic avoids drift between manual and agent paths.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Keep screenshot payloads optional and disabled by default.
  Rationale: Base64 image payloads are large and can degrade stream throughput; state-only snapshots are the safer baseline.
  Date/Author: 2026-02-26 / OpenCode.

## Outcomes & Retrospective

The implementation shipped the local WebSocket control and telemetry path, template execution plumbing, and plugin lifecycle integration. The new code is in `src/main/java/com/runepal/agent/` and is disabled unless `agentEnable` is enabled in config.

The largest current gap is runtime validation in this environment due missing Java toolchain. Manual RuneLite verification is still required on a machine with JDK 11 and the game client.

Lessons learned so far: preserving the existing task stack and adding one explicit restart entrypoint (`startBotForType`) is the smallest reliable way to let external commands switch bot behavior without fragile config-edge timing.

## Context and Orientation

This repository is a Java 11 RuneLite plugin named `runepal`. The plugin entry point is `src/main/java/com/runepal/RunepalPlugin.java`. The current runtime loop receives RuneLite events and calls `TaskManager.onLoop()` each game tick when `config.startBot()` is true. Existing automation is organized as `BotTask` implementations (for example `MiningTask`, `CombatTask`, `FishingTask`, `WoodcuttingTask`) that are selected by `BotType` and created inside `RunepalPlugin`.

Input is executed through RemoteInput (`src/main/java/com/runepal/services/RemoteInputService.java`) and higher-level services (`ActionService`, `WindmouseService`, `GameService`). The plugin already has a custom internal event bus (`src/main/java/com/runepal/EventService.java`) and a custom task stack (`src/main/java/com/runepal/TaskManager.java`).

For this feature, "template skill" means a predefined, parameterized automation mode such as "power mine Iron". It is not arbitrary generated Java code. "WebSocket stream" means a persistent TCP connection where the plugin pushes JSON state messages continuously, so clients do not need to poll repeatedly.

The implementation must preserve current manual panel workflows. The new agent functionality should be additive and disabled by default.

## Plan of Work

Milestone 1 introduces a dedicated agent package. The implementation adds an `AgentService` coordinator that owns server lifecycle, command handling, a queue for control actions, and periodic state broadcast. It also adds a lightweight command protocol (`ping`, `state_request`, `list_skills`, `run_skill`, `stop_skill`) and a template registry that defines supported skills and accepted parameters.

Milestone 2 wires the template executor into existing bot startup behavior. This requires extracting or centralizing task startup logic in `RunepalPlugin` so an agent command can reliably start a specific `BotType` even when another bot is already active. This milestone also adds status tracking so clients can see the active template, started-at time, and last execution result.

Milestone 3 adds streaming snapshots. A snapshot builder will collect player stats, location, animation, inventory slots, open menu entries, and sampled nearby NPC/game object data. The service broadcasts snapshots every configurable number of ticks and supports optional screenshot embedding in Base64 (off by default due payload size).

Milestone 4 adds configuration and validation: new `BotConfig` items to enable/disable the local agent server, set the port, set stream cadence, and toggle screenshot inclusion; then compile/test commands are run and results are recorded. Manual acceptance steps are documented for testing in a real RuneLite session on the user’s machine.

## Concrete Steps

All commands run from repository root: `/home/ubuntu/runelite-plugin`.

1. Add dependencies in `build.gradle` for WebSocket server implementation and explicit JSON support.
2. Add agent config items in `src/main/java/com/runepal/BotConfig.java` under a dedicated config section.
3. Create new files under `src/main/java/com/runepal/agent/`:
   - `AgentService.java` for lifecycle, command queue, and broadcasting.
   - `AgentWebSocketServer.java` for local server bindings and socket callbacks.
   - `AgentSkillTemplate.java` to enumerate templates and metadata.
   - `AgentSkillExecutor.java` to apply config overrides and start/stop templates.
   - `AgentSnapshotBuilder.java` to construct JSON snapshots from client state.
4. Refactor `RunepalPlugin.java` to expose reusable bot startup logic callable by both manual start flow and agent execution.
5. Run validation commands:
   - `./gradlew compileJava`
   - `./gradlew test --tests "com.runepal.TaskManagerTest"` (if test runtime permits)
6. Update this plan with actual command output snippets and note any environment limitations (for example JDK mismatch or inability to run live RuneLite in this container).

Actual transcript from this environment:

    $ ./gradlew compileJava
    ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Follow-up command to run on a machine with Java 11 configured:

    $ JAVA_HOME=/path/to/jdk11 ./gradlew compileJava
    $ JAVA_HOME=/path/to/jdk11 ./gradlew test --tests "com.runepal.TaskManagerTest"

## Validation and Acceptance

Code-level acceptance:

Run `./gradlew compileJava` and expect success. If tests are executed in this environment, run at least one focused test command and expect green results.

Behavioral acceptance for manual verification in RuneLite:

1. Enable the new agent config toggle and set a port such as `8765`.
2. Start RuneLite with the plugin enabled and logged into a character.
3. Connect with a local WebSocket client to `ws://127.0.0.1:8765`.
4. Send:

       {"type":"list_skills"}

   Expect a JSON response listing available template names and parameter hints.

5. Send:

       {"type":"run_skill","skill":"MINE_POWER","params":{"rockTypes":"Iron"}}

   Expect an acknowledgment response and then periodic `snapshot` payloads showing `bot.running=true` and the active template.

6. Send:

       {"type":"stop_skill"}

   Expect acknowledgment and subsequent snapshots showing bot not running.

Real-time expectation clarification:

The stream should update on configured tick cadence, but LLM decision-making still must remain coarse-grained. Real-time action remains in Java task logic.

## Idempotence and Recovery

The changes are additive and safe to re-run. Rebuilding multiple times does not mutate game data. If server startup fails because the configured port is in use, the plugin should log an actionable error and continue running without the agent server; changing to a free port and toggling enable should recover without restarting the whole repository checkout.

If a template run command arrives while another template is active, the executor should explicitly stop or restart using the new template in a controlled way rather than silently no-op. If command JSON is malformed, the server should return a structured error and continue serving other clients.

## Artifacts and Notes

During implementation, keep concise evidence snippets here for:

- build/test command outcomes,
- representative WebSocket request/response examples,
- any notable logs proving lifecycle behavior.

Current evidence:

    Added files:
    - src/main/java/com/runepal/agent/AgentService.java
    - src/main/java/com/runepal/agent/AgentWebSocketServer.java
    - src/main/java/com/runepal/agent/AgentSkillTemplate.java
    - src/main/java/com/runepal/agent/AgentSkillExecutor.java
    - src/main/java/com/runepal/agent/AgentSnapshotBuilder.java
    - src/main/java/com/runepal/agent/AgentExecutionResult.java

    Validation attempt:
    $ ./gradlew compileJava
    ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Expected runtime log after manual enablement:

    Agent WebSocket server listening on ws://127.0.0.1:8765

## Interfaces and Dependencies

Dependencies:

- Add `org.java-websocket:Java-WebSocket` in `build.gradle` for a simple embedded WebSocket server.
- Add explicit `com.google.code.gson:gson` dependency for JSON parsing and serialization so agent protocol classes do not rely on transitive exposure.

New interfaces and concrete types expected at completion:

In `src/main/java/com/runepal/agent/AgentSkillTemplate.java`, define an enum with stable values and metadata methods:

    public enum AgentSkillTemplate {
        MINE_POWER,
        MINE_BANK,
        WOODCUT_POWER,
        WOODCUT_BANK,
        FISH_POWER,
        FISH_BANK,
        COMBAT_BASIC,
        STOP_ALL;

        public static Optional<AgentSkillTemplate> fromWireName(String value);
        public String getWireName();
        public String getDescription();
    }

In `src/main/java/com/runepal/agent/AgentSkillExecutor.java`, define execution APIs:

    public final class AgentSkillExecutor {
        public AgentSkillExecutor(RunepalPlugin plugin, BotConfig config, ConfigManager configManager);
        public AgentExecutionResult execute(AgentSkillTemplate template, JsonObject params);
        public AgentExecutionResult stopActiveSkill();
        public JsonObject getStatusSnapshot();
        public JsonArray listSkillDefinitions();
    }

In `src/main/java/com/runepal/agent/AgentSnapshotBuilder.java`, define snapshot creation:

    public final class AgentSnapshotBuilder {
        public AgentSnapshotBuilder(RunepalPlugin plugin, BotConfig config, AgentSkillExecutor skillExecutor);
        public JsonObject buildSnapshot(long tickNumber);
    }

In `src/main/java/com/runepal/agent/AgentWebSocketServer.java`, define local socket wrapper:

    public final class AgentWebSocketServer extends WebSocketServer {
        public AgentWebSocketServer(InetSocketAddress bindAddress, AgentService service);
    }

In `src/main/java/com/runepal/agent/AgentService.java`, define lifecycle integration APIs:

    public final class AgentService {
        public AgentService(RunepalPlugin plugin, BotConfig config, ConfigManager configManager);
        public void onGameTick();
        public void shutdown();
        void onSocketMessage(WebSocket connection, String message);
    }

In `src/main/java/com/runepal/RunepalPlugin.java`, expose reusable startup entrypoint for template execution without duplicating switch logic:

    public synchronized boolean startBotForType(BotType botType);

This method must clear/restart task state safely and preserve existing manual controls.

Revision Notes:

- 2026-02-26: Initial ExecPlan created to cover design through implementation for the in-plugin agent host and WebSocket stream.
- 2026-02-26: Updated after implementation pass to record completed milestones, concrete command output, and the Java toolchain validation blocker.
