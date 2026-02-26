# Implement an in-plugin AI gameplay agent with LLM planning and script execution

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document is maintained in accordance with `.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the plugin will be able to run a full in-plugin AI control loop: observe game state, store a goal, call an OpenAI-compatible LLM for planning, generate a safe JSON script, validate it, and execute it through existing task/action infrastructure. A local client will be able to set a goal and watch the agent choose either a predefined template skill or a generated script that performs game actions.

The user-visible behavior is: set an LLM provider and API key in plugin config, connect to the local WebSocket, send `set_goal` and `plan_now`, then observe `decision` events and active bot/script behavior in snapshot messages.

## Progress

- [x] (2026-02-26 17:04Z) Wrote this implementation ExecPlan file and anchored it to repository paths.
- [x] (2026-02-26 17:18Z) Added LLM provider config fields and API-key auth config in `src/main/java/com/runepal/BotConfig.java`.
- [x] (2026-02-26 17:18Z) Implemented LLM module under `src/main/java/com/runepal/llm/**` (`LlmProviderType`, `LlmMessage`, `LlmRequestOptions`, `LlmResult`, `LlmClient`).
- [x] (2026-02-26 17:18Z) Implemented script DSL, parser, validator, repository, metadata, and runtime `ScriptTask` under `src/main/java/com/runepal/agent/script/**`.
- [x] (2026-02-26 17:18Z) Extended agent runtime with goal store, orchestrator, script executor, expanded WebSocket commands, and decision broadcasting.
- [x] (2026-02-26 17:18Z) Integrated script start lifecycle into `src/main/java/com/runepal/RunepalPlugin.java` via `startScriptSpec(ScriptSpec)`.
- [x] (2026-02-26 17:18Z) Added parser/validator tests under `src/test/java/com/runepal/agent/script/**` and retained template normalization test.
- [x] (2026-02-26 17:26Z) Fixed logging-classpath regression by aligning WebSocket dependency with SLF4J 1.7 (`Java-WebSocket` 1.5.3) in `build.gradle`.
- [x] (2026-02-26 17:40Z) Improved heuristic combat planning so goals like "kill cows" set `combatNpcNames=Cow` on fallback, and accepted `npcNames` alias in template params.
- [x] (2026-02-26 17:40Z) Added an in-plugin Agent UI panel via `BotType.AGENT_MODE` and `src/main/java/com/runepal/AgentBotPanel.java` so users can set goals and trigger planning without a separate WebSocket client.
- [x] (2026-02-26 17:40Z) Improved combat target selection to require on-screen clickbox and added periodic info logs when no targets are found.
- [x] (2026-02-26 18:02Z) Added pending-script approval flow: persist pending scripts, expose pending script snapshot, and add an Agent panel button to approve/run the pending script.
- [x] (2026-02-26 18:02Z) Fixed `stopBot()` to be safe off the client thread (defer task cleanup to next GameTick) to prevent varbit/thread assertions when stopping from Swing.
- [x] (2026-02-26 18:02Z) Added LLM compatibility retry for providers/models that require `max_completion_tokens` instead of `max_tokens`.
- [x] (2026-02-26 18:02Z) Fixed Agent panel checkbox layout (stacked vertical) to avoid overlap/clipping.
- [ ] (2026-02-26 17:18Z) Validation partially complete (completed: attempted `./gradlew compileJava` and focused test command; remaining: rerun in JDK 11 environment with `JAVA_HOME` configured).

## Surprises & Discoveries

- Observation: Build/test validation is blocked in this container because no Java runtime is configured.
  Evidence:

      $ ./gradlew compileJava
      ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

      $ ./gradlew test --tests "com.runepal.agent.script.ScriptValidatorTest"
      ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

- Observation: Logging stopped in runtime due SLF4J major-version mismatch after introducing `Java-WebSocket` 1.5.6 (pulls `slf4j-api` 2.0.x while runtime binder is 1.7.x).
  Evidence:

      SLF4J: No SLF4J providers were found.
      SLF4J: Class path contains SLF4J bindings targeting slf4j-api versions 1.7.x or earlier.
      SLF4J: Ignoring binding found at [...]/logback-classic/1.2.9/.../StaticLoggerBinder.class

- Observation: In manual testing, "kill cows" could start `CombatTask` but produce no in-game actions when LLM planning failed and the heuristic fallback targeted `Goblin`, which may not exist nearby; `CombatTask` only logged this at debug level.
  Evidence:

      [agent-orchestrator] WARN  com.runepal.llm.LlmClient - LLM request failed: An existing connection was forcibly closed by the remote host
      [Client] INFO  com.runepal.CombatTask - Starting Combat Task.

- Observation: Stopping automation from Swing UI could crash with `AssertionError: must be called on client thread` because task `onStop()` hooks can call client varbit APIs.
  Evidence:

      java.lang.AssertionError: must be called on client thread
      at client.getVarbitValue(...)
      at com.runepal.PrayerService.deactivateAllPrayers(...)
      at com.runepal.CombatTask.onStop(...)
      at com.runepal.TaskManager.clearTasks(...)
      at com.runepal.RunepalPlugin.stopBot(...)

- Observation: Some OpenAI-compatible providers/models reject `max_tokens` and require `max_completion_tokens`, causing LLM planning to fall back to heuristics.
  Evidence:

      Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.

## Decision Log

- Decision: Implement script generation as JSON DSL, not runtime Java code generation.
  Rationale: JSON DSL is safer, easier to validate, and avoids dynamic compilation risk while still enabling behavior synthesis.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Keep all mutating gameplay actions on the game tick execution path by queueing commands.
  Rationale: This preserves thread safety and consistent lifecycle with the existing task architecture.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Add script execution as a dedicated `ScriptTask` implementing `BotTask` instead of introducing a parallel executor path.
  Rationale: Reusing `TaskManager` preserves lifecycle symmetry and keeps automation behavior consistent with existing bots.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Persist scripts to `~/.runelite/runepal/scripts` with sanitized filenames and JSON source retention.
  Rationale: This provides inspectable and reusable artifacts while keeping storage local and user-controlled.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Gate automatic execution of LLM-generated scripts behind `llmRequireScriptApproval`.
  Rationale: This adds a safety control for generated behavior without blocking template-skill planning.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Downgrade `org.java-websocket:Java-WebSocket` from `1.5.6` to `1.5.3`.
  Rationale: Version 1.5.6 pulls `slf4j-api` 2.x and breaks RuneLite runtime logging that currently binds to SLF4J 1.7.x; 1.5.3 uses 1.7.x-compatible SLF4J.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Add `BotType.AGENT_MODE` and a dedicated `AgentBotPanel` so the agent can be controlled entirely from within the plugin UI.
  Rationale: Users should not need a separate WebSocket client to set goals, trigger planning, or stop automation; WebSocket becomes optional for external tooling.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Improve heuristic planning for combat goals by extracting the target NPC from the goal text.
  Rationale: When LLM calls fail (network/provider), the fallback must still produce a locally actionable plan.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: When `llmRequireScriptApproval` is enabled, store a pending script and require explicit user approval before execution.
  Rationale: Generated scripts should not auto-run without user control, but users must have an obvious “approve/run” workflow.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Make `RunepalPlugin.stopBot()` safe off the client thread by deferring task cleanup to the next GameTick.
  Rationale: Swing actions run on the EDT and must not invoke client-only APIs indirectly via task stop hooks.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Implement automatic retry for `max_completion_tokens` when the provider/model rejects `max_tokens`.
  Rationale: This improves portability across OpenAI-compatible providers and newer model families.
  Date/Author: 2026-02-26 / OpenCode.

## Outcomes & Retrospective

The implementation now includes end-to-end architecture for goal-driven planning and script execution: LLM config, OpenAI-compatible HTTP client, script DSL parsing/validation/execution, persistent script repository, expanded agent WebSocket APIs (`set_goal`, `get_goal`, `plan_now`, `list_scripts`, `save_script`, `run_script`, `stop_script`, `llm_ping`) in addition to existing skill commands, and an in-plugin Agent UI panel (`BotType.AGENT_MODE`) to control planning without external clients.

What remains is runtime validation in an environment with JDK 11 and manual RuneLite gameplay verification. The main lesson from this pass is that keeping decisions asynchronous while queueing all execution mutations onto tick processing keeps the design simple and thread-safe.

## Context and Orientation

The plugin now has local WebSocket support in `src/main/java/com/runepal/agent/AgentService.java`, template skill execution via `AgentSkillExecutor`, script execution via `AgentScriptExecutor`, goal/planning orchestration via `AgentOrchestrator`, and snapshot streaming via `AgentSnapshotBuilder`. Bot execution still runs through `TaskManager` and `BotTask` in `src/main/java/com/runepal/`, with generated scripts executed by `src/main/java/com/runepal/agent/script/ScriptTask.java`.

LLM provider configuration exists in `src/main/java/com/runepal/BotConfig.java` and OpenAI-compatible calling logic exists in `src/main/java/com/runepal/llm/LlmClient.java`. Script parsing, validation, and persistence are under `src/main/java/com/runepal/agent/script/`.

## Plan of Work

First, extend `BotConfig` with a dedicated LLM section and provider enum so users can configure provider, base URL, model, timeouts, and API key. Then add a small LLM module using Java 11 `HttpClient` and `gson` to call OpenAI-compatible chat completions.

Next, implement a script subsystem under `src/main/java/com/runepal/agent/script/` that can parse script JSON, validate legal actions/conditions, persist scripts under `~/.runelite/runepal/scripts`, and execute scripts as `BotTask`.

Then, extend `AgentService` to manage goals and planning via a new orchestrator that can call the LLM asynchronously and produce either template-skill runs or script runs. Add WebSocket commands for goal management, planning, and script operations.

Finally, integrate `RunepalPlugin` with a reusable script-start entrypoint and add focused tests around parser/validator normalization and command-facing behavior.

## Concrete Steps

All commands are run from `/home/ubuntu/runelite-plugin`.

1. Implement config and enums in Java source.
2. Implement LLM classes and orchestrator classes.
3. Implement script DSL parser/validator/runtime/repository.
4. Wire `AgentService` and `RunepalPlugin` integrations.
5. Add tests.
6. Run:

    ./gradlew compileJava
    ./gradlew test --tests "com.runepal.agent.*"

If the environment lacks Java 11, rerun the same commands on a machine with `JAVA_HOME` configured to JDK 11.

## Validation and Acceptance

Acceptance criteria:

1. Plugin config includes LLM settings including API key field.
2. WebSocket supports `set_goal`, `get_goal`, `plan_now`, `list_scripts`, `save_script`, `run_script`, `stop_script`, and `llm_ping`.
3. Agent can create at least one validated script from goal/planning path and execute it through `TaskManager`.
4. Invalid scripts are rejected with structured errors and do not execute.
5. Existing template skill commands continue to work.

Manual verification scenario:

Option A (in-plugin UI):

1. In RuneLite, select `Agent` from the bot type dropdown.
2. Enable "Enable Local Agent" and "Enable LLM Planning" (and set API key in plugin config).
3. Enter a goal (e.g. "kill cows") and click "Set Goal" then "Plan Now".
4. Observe status/decision updates and verify in-game actions begin.
5. Click "Stop Automation".

Option B (WebSocket):

1. Enable local agent and LLM config.
2. Connect to WebSocket and call `llm_ping`.
3. Call `set_goal` and `plan_now`.
4. Observe `decision` messages and either template run or script run.
5. Call `stop_script` (or `stop_skill`) and verify bot stops.

## Idempotence and Recovery

The changes are additive and safe to re-run. Script save/load is idempotent by name (overwrite-on-save). If LLM calls fail due credentials/network, the agent should emit an error decision and remain stable. If a script fails validation, it should be rejected without affecting active tasks.

## Artifacts and Notes

Validation command evidence from this environment:

    $ ./gradlew compileJava
    ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

    $ ./gradlew test --tests "com.runepal.agent.script.ScriptValidatorTest"
    ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Implemented command protocol additions in `src/main/java/com/runepal/agent/AgentService.java`:

    ping
    state_request
    list_skills
    run_skill
    stop_skill
    set_goal
    get_goal
    plan_now
    list_scripts
    save_script
    run_script
    stop_script
    llm_ping

## Interfaces and Dependencies

Implemented modules and key APIs:

- `src/main/java/com/runepal/llm/LlmProviderType.java`
- `src/main/java/com/runepal/llm/LlmClient.java`
- `src/main/java/com/runepal/llm/LlmResult.java`
- `src/main/java/com/runepal/agent/AgentOrchestrator.java`
- `src/main/java/com/runepal/agent/AgentGoalStore.java`
- `src/main/java/com/runepal/agent/AgentDecisionRecord.java`
- `src/main/java/com/runepal/agent/script/ScriptParser.java`
- `src/main/java/com/runepal/agent/script/ScriptValidator.java`
- `src/main/java/com/runepal/agent/script/ScriptRepository.java`
- `src/main/java/com/runepal/agent/script/ScriptTask.java`

All HTTP calls use Java 11 `java.net.http.HttpClient`. All JSON serialization uses `gson` already present in the project.

Revision Notes:

- 2026-02-26: Initial implementation ExecPlan created for full AI gameplay agent vision.
- 2026-02-26: Updated after implementation pass to reflect completed milestones, recorded environment build blocker, and capture implemented command/API surface.
