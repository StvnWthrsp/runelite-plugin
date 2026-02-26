# Build OSRS Wiki tools, game-data tools, observability, and memory for the in-plugin AI harness

This ExecPlan is a living document. The sections `Progress`, `Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must be kept up to date as work proceeds.

This document must be maintained in accordance with `.agents/PLANS.md`.

## Purpose / Big Picture

After this change, the in-plugin agent can do three things it cannot do today:

First, it can fetch and read OSRS Wiki pages at runtime (for example the Legends' Quest page) and use that information while planning. This is visible because the Agent panel will show a short tool log that includes the wiki URLs fetched and a compact summary of the extracted facts.

Second, it can query structured game data “on demand” through an internal tool interface (skills, inventory, widgets, nearby entities, current automation state) and optionally capture a screenshot when the agent needs visual context. This is visible because the agent will be able to answer a goal like “Get the skill requirements for Legends' Quest” by (a) fetching the requirements from the wiki, (b) reading the player’s current skills from the client, and (c) displaying the missing requirements inside the Agent panel.

Third, it gains observability and learning: every plan/tool call/execution step is traced into a local ring buffer and written to disk, and the agent can write “memory” entries describing what worked and what failed. This is visible because the Agent panel can display a recent trace and because a local memory file will accumulate entries that the agent can later retrieve and use to improve future scripts.

The overall outcome is a more Playwright-like harness around RuneLite: the LLM is responsible for figuring out what to do, and the plugin provides safe, explicit tools to fetch wiki knowledge, inspect game state, act in game, and learn from results.

## Progress

- [x] (2026-02-26 19:56Z) Authored this ExecPlan.
- [x] (2026-02-26 21:08Z) Added structured trace primitives (`AgentTraceService`, `TraceEvent`) and integrated trace events for plan requests, tool calls/results, decisions, and script lifecycle transitions.
- [x] (2026-02-26 21:16Z) Added runtime OSRS Wiki client with strict allowlist enforcement, disk caching, TTL, and rate limiting in `OsrsWikiClient`.
- [x] (2026-02-26 21:24Z) Added first-class tool system (`AgentTool`, registry, dispatcher, context, result) and registered core tools: `wiki.search`, `wiki.fetch`, `game.snapshot`, `vision.capture`, `trace.get_recent`, `memory.search`, `memory.add`, `bot.run_template`, `bot.run_script`, `bot.stop`.
- [x] (2026-02-26 21:31Z) Added local JSONL memory store (`AgentMemoryStore`) and wired memory tools plus UI surfacing of latest memory title.
- [x] (2026-02-26 21:39Z) Implemented LLM tool-loop runner (`AgentToolLoopRunner`) and integrated into `AgentOrchestrator` with heuristic fallback.
- [x] (2026-02-26 21:48Z) Extended Agent UI with answer area, tool/trace log, pending script JSON viewer, approval controls, and dev buttons for wiki/snapshot/capture tool checks.
- [x] (2026-02-26 21:56Z) Added tests for wiki allowlist/caching, tool dispatch, memory search, and trace ring buffer.
- [x] (2026-02-26 20:49Z) Added automatic stall detection + self-debug planning trigger in `AgentService`, plus debug evidence packaging (snapshot/trace/optional screenshot) and memory writes for stall/debug outcomes.
- [x] (2026-02-26 20:49Z) Added orchestrator debug-planning path (`requestDebugPlan`) and debug-mode tool-loop execution (`AgentToolLoopRunner.runDebug`) with non-heuristic parse behavior.
- [x] (2026-02-26 20:49Z) Added Agent UI `Debug Now` control and surfaced last debug reason in status.
- [x] (2026-02-26 22:18Z) Improved Agent panel usability by wrapping the full panel in a scroll view and rendering trace output as readable event lines instead of one-line JSON blobs.
- [x] (2026-02-26 22:22Z) Hardened tool-loop/wiki parsing paths: safe bundled rules loading in `AgentToolLoopRunner` and tolerant optional integer parsing for `wiki.fetch` section argument.
- [ ] Run Gradle compile/tests in a JDK 11 environment and record evidence (blocked in this environment: no `JAVA_HOME`/`java`).
- [ ] Manual RuneLite acceptance: “Get Legends' Quest requirements” end-to-end, plus one self-debug loop that records memory.

## Surprises & Discoveries

- Observation: OpenAI-compatible providers and model families differ on parameter names (e.g., `max_tokens` vs `max_completion_tokens`).
  Evidence: runtime log excerpt from earlier testing: “Unsupported parameter: 'max_tokens' ... Use 'max_completion_tokens' instead.”

- Observation: Anything that indirectly reads client-only APIs (varbits, widgets) must run on the RuneLite client thread; stopping automation from Swing can crash if it triggers task `onStop()` off-thread.
  Evidence: prior crash: `java.lang.AssertionError: must be called on client thread` from prayer deactivation during stop.

This section will be expanded during implementation with concrete evidence from tests, tool outputs, and manual in-client runs.

- Observation: The execution environment does not currently expose a JDK runtime, so Gradle compile/test commands fail before dependency resolution.
  Evidence: `./gradlew compileJava` and `./gradlew test --tests "com.runepal.agent.*"` both fail with `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`

- Observation: Tool-loop integration can reuse the existing orchestrator action sink without changing execution plumbing by mapping `final` payloads back into `PlannedAction`.
  Evidence: `AgentOrchestrator.planInternal(...)` now calls `AgentToolLoopRunner` and maps `{type:"final",mode:"execute"...}` through `parseToolLoopFinalDecision(...)`.

- Observation: Stall detection based only on `currentState` is too coarse; many loops keep the same state while still making progress.
  Evidence: Added composite progress fingerprint in `AgentService` using state + player position/animation/interacting + total XP + inventory hash.

## Decision Log

- Decision: Use OSRS Wiki’s MediaWiki API (`https://oldschool.runescape.wiki/api.php`) for search and page fetch.
  Rationale: It is stable, structured JSON, and supports fetching specific page sections; it also avoids brittle HTML scraping.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Implement tool calling using a simple “JSON-in-text” protocol (the assistant returns a single JSON object representing either a tool call or a final answer), rather than relying on provider-specific OpenAI function-calling fields.
  Rationale: This maximizes compatibility across OpenAI-compatible providers and makes failures debuggable via plain logs.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Enforce a strict domain allowlist for network fetches: only `oldschool.runescape.wiki` (and its canonical HTTPS form) are permitted.
  Rationale: This is a harness; it must not become a general web client. Restricting domains reduces security risk and prompt-injection surface.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Store agent memory as append-only JSONL in the RuneLite user directory (`~/.runelite/runepal/agent/memory.jsonl`), and implement simple retrieval (substring / token match) before adding heavier indexing.
  Rationale: JSONL is human-auditable, resilient to partial writes, and easy to append. A simple search is enough to prove the loop.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Treat screenshots as an on-demand tool, not continuous streaming, and keep them compressed and downscaled.
  Rationale: Images are large and can dominate tokens/cost; only capture when the LLM explicitly requests visual context.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Keep tool calling provider-agnostic by enforcing JSON-only assistant output and explicitly routing tool calls through `AgentToolDispatcher`.
  Rationale: This preserves compatibility with OpenAI-compatible providers that vary in function-calling capabilities and naming.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Surface tool/debug capabilities directly in Agent panel via small dev buttons (`Wiki Test`, `Tool: Snapshot`, `Tool: Capture`) in addition to automatic planning usage.
  Rationale: This enables milestone-by-milestone manual verification without requiring a fully functioning LLM loop for each step.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Trigger automatic self-debug only when bot is running, no progress exceeds `agentStallTicks`, planner is idle, and cooldown has elapsed.
  Rationale: Prevents noisy debug loops and avoids fighting with active planning/execution.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Capture rich debug evidence (snapshot, recent trace, optional screenshot) and pass it as explicit context into a dedicated debug-mode tool loop.
  Rationale: The model needs concrete runtime evidence to produce targeted fixes rather than generic advice.
  Date/Author: 2026-02-26 / OpenCode.

- Decision: Write memory entries at both stall detection time and debug outcome time.
  Rationale: This creates durable learning artifacts even if the model forgets to call `memory.add` itself.
  Date/Author: 2026-02-26 / OpenCode.

## Outcomes & Retrospective

Core infrastructure shipped across milestones 1-6: structured trace ring buffer, wiki runtime client with allowlist/cache/rate-limit, memory JSONL store, tool registry/dispatcher with required core tools, LLM JSON tool loop integration in orchestrator, and Agent panel observability surfaces (answer + trace/tool + pending script JSON + approval controls + dev tool smoke buttons).

Automated unit tests were added for the requested risk areas (wiki allowlist/caching, tool dispatch, memory search, trace ring buffer), but this environment cannot run Gradle because no Java runtime is available. Manual RuneLite acceptance remains pending and must be run on a machine with RuneLite + JDK 11.

Remaining gaps versus full plan: (1) no completed in-client acceptance transcript captured yet, and (2) debug-repair quality tuning is still pending real gameplay data (the loop exists, but prompts/tooling may need iteration after manual runs).

## Context and Orientation

This repository is a Java 11 RuneLite plugin. The entry point is `src/main/java/com/runepal/RunepalPlugin.java`. Automation runs on the game-tick loop: `RunepalPlugin.onGameTick(...)` calls `src/main/java/com/runepal/TaskManager.java:onLoop()` when `startBot=true`, which runs a stack of `src/main/java/com/runepal/BotTask.java` implementations.

An “agent” already exists:

- `src/main/java/com/runepal/agent/AgentService.java` runs each tick when `agentEnable=true`. It streams snapshots over a local WebSocket (optional), manages goals, and can trigger planning.
- `src/main/java/com/runepal/agent/AgentOrchestrator.java` performs planning off-thread. Today it makes a single LLM request (if enabled) and otherwise falls back to heuristic planning.
- `src/main/java/com/runepal/llm/LlmClient.java` calls an OpenAI-compatible chat completion endpoint.
- `src/main/java/com/runepal/agent/script/ScriptTask.java` interprets a small JSON script DSL.
- `src/main/java/com/runepal/AgentBotPanel.java` (selected via `BotType.AGENT_MODE`) provides in-plugin control.

Definitions used in this plan:

- A “tool” is a named capability the agent can request, such as fetching a wiki page, reading the player’s skill levels, capturing a screenshot, stopping the bot, or writing a memory entry.
- A “tool loop” is an iterative process where the LLM requests a tool, the harness executes it, the result is fed back, and the loop continues until the LLM returns a final answer or a final execution decision.
- “Observability” means we can see what the agent attempted, what happened, and why, via a structured trace and UI/log output.
- “Memory” means durable local notes written by the agent (and retrievable later) describing what works/doesn’t work.

This plan adds a tool system, a wiki fetcher, a trace system, and memory storage. It also upgrades the agent from one-shot planning to a tool loop, enabling complex tasks like reading the OSRS Wiki at runtime.

## Plan of Work

This work is intentionally milestone-based. Each milestone ends with something you can run and observe in RuneLite without needing to complete later milestones.

### Milestone 1: Observability primitives (trace service)

Goal: create a structured trace that records what the agent and scripts do.

Work:

Create a new package `src/main/java/com/runepal/agent/trace/` that contains:

- `AgentTraceService` holding a bounded ring buffer of `TraceEvent` objects.
- `TraceEvent` as a small immutable record (timestamp, category, message, JSON payload).

Wire `AgentTraceService` into `src/main/java/com/runepal/agent/AgentService.java` and add trace calls for:

- “plan_requested” (goal, request source)
- “decision” (final decision JSON)
- “tool_call” and “tool_result” (once tools exist)

Instrument `src/main/java/com/runepal/agent/script/ScriptTask.java` to record step transitions and failures. Do not rely on log parsing; record structured events.

Acceptance:

Run any existing template (e.g., combat) and verify the Agent panel can display the last N trace events (even if minimal at first). The trace must show a decision and at least one execution start event.

### Milestone 2: OSRS Wiki fetcher + wiki tools

Goal: allow runtime wiki access with strong safety properties.

Work:

Add `src/main/java/com/runepal/agent/wiki/OsrsWikiClient.java` using `java.net.http.HttpClient` with:

- strict allowlist (only `https://oldschool.runescape.wiki/...`)
- rate limiting (e.g., 1 request / second configurable)
- response size caps (configurable, default a few MB)
- disk cache under `~/.runelite/runepal/wiki_cache/` keyed by URL hash and including a TTL

Implement two API calls:

- Search: `action=query&list=search&srsearch=<query>&format=json`
- Fetch: `action=parse&page=<title>&prop=wikitext|text|sections&format=json`

Create tools (names are part of the contract and must be stable):

- `wiki.search` (args: `query`, optional `limit`)
- `wiki.fetch` (args: `title`, optional `section`, optional `format`)

Each tool result must include canonical URL, fetched-at timestamp, and a truncated content preview with total length so the LLM can decide whether to fetch more.

Acceptance:

In the Agent panel, add a “Wiki Test” button that runs `wiki.search("Legends' Quest")` then `wiki.fetch` for the top result and displays the title and a snippet. This milestone proves wiki fetch works before wiring the LLM into it.

### Milestone 3: Memory store + memory tools

Goal: durable, retrievable agent learning.

Work:

Add `src/main/java/com/runepal/agent/memory/` containing:

- `AgentMemoryStore` (append-only JSONL writer; safe flush)
- `AgentMemoryEntry` (timestamp, tags, title, content, evidence pointers)
- `AgentMemorySearch` (simple search over recent entries, then optionally full scan)

Add tools:

- `memory.add` (args: title, tags[], content, evidence{})
- `memory.search` (args: query, optional limit, optional tags[])

Acceptance:

After a successful “kill cows” run, the agent can write a memory entry like “Combat works when cows are on screen; requires clickbox; retry attack verification if needed.” The Agent panel shows the most recent memory entry title.

### Milestone 4: Tool system (registry + dispatcher)

Goal: unify wiki/game/vision/memory/trace/bot actions under a single tool interface.

Work:

Create `src/main/java/com/runepal/agent/tools/` with:

- `AgentTool` interface (name, description, JSON schema-ish argument hints, execute(args, context))
- `AgentToolRegistry` to register tools by name
- `AgentToolDispatcher` to execute a tool call with error handling and return a structured result
- `AgentToolContext` exposing safe dependencies (wiki client, memory store, trace service, latest snapshot, accessors to start/stop bots)

Add core tools:

- `game.snapshot` (returns a compact facts snapshot; do not include huge arrays by default)
- `vision.capture` (returns a downscaled JPEG base64 plus dimensions; store to disk optionally)
- `trace.get_recent`
- `bot.run_template`, `bot.run_script`, `bot.stop`

Acceptance:

From the Agent panel, add a simple “Run Tool…” dev dropdown (or a few buttons) that exercises `game.snapshot` and `vision.capture` and shows results in a text area. This verifies tool wiring without involving the LLM.

### Milestone 5: LLM tool loop runner

Goal: offload more reasoning to the LLM by letting it request tools.

Work:

Add `src/main/java/com/runepal/agent/llm/AgentToolLoopRunner.java` (or similar) that:

- builds a system prompt from a new rules file (see Milestone 6)
- includes a tool manifest listing tool names + descriptions + argument hints
- starts with the user goal and an initial `game.snapshot`
- loops up to `agentMaxPlanTurns` times:
  - call LLM via `LlmClient`
  - parse the assistant content as a single JSON object
  - if it is a tool call, execute and append a tool result message
  - if it is a final answer/decision, stop

Define the wire protocol the LLM must emit (this is critical for debugging and provider compatibility):

- Tool call:
  - `{ "type": "tool", "name": "wiki.search", "arguments": { ... }, "callId": "..." }`
- Final answer (information):
  - `{ "type": "final", "mode": "answer", "answer": "...", "citations": [ ... ] }`
- Final decision (execution):
  - `{ "type": "final", "mode": "execute", "decisionType": "template"|"script"|"idle", ... }`

Update `src/main/java/com/runepal/agent/AgentOrchestrator.java` so `plan_now` and Agent UI planning uses the tool loop runner when `llmEnable=true`. Keep the heuristic fallback for offline use.

Acceptance:

In the Agent panel, set goal “Get the skill requirements for Legends' Quest” and click “Plan Now”. The agent must:

- call `wiki.search` and `wiki.fetch` via tools
- call `game.snapshot` (or `game.skills`) via tools
- render a human-readable answer in the Agent panel including at least one citation URL

No packaged quest database is permitted; the answer must originate from runtime wiki fetch.

### Milestone 6: Agent rules + UI observability + self-debug loop

Goal: make agent behavior predictable and debuggable, and allow it to learn.

Work:

Add a rules file that is shipped with the plugin:

- `src/main/resources/agent/RULES.md`

It should be short and explicit: tool protocol, safety constraints (local-only, allowlisted wiki), how to treat wiki content as untrusted, and a requirement to write memory after notable successes/failures.

Allow local override:

- `~/.runelite/runepal/agent/rules_override.md` if present.

Update the Agent UI (`src/main/java/com/runepal/AgentBotPanel.java`) to include:

- a scrollable “Tool/Trace Log” area (recent trace events)
- a “Pending Script” viewer (pretty JSON) plus “Approve & Run”
- an “Answer” area for information goals (Legends requirements)

Add a minimal self-debug loop:

- When execution stalls (no progress markers for N ticks, or repeated interaction failures), capture evidence (recent trace + latest snapshot, and optionally a screenshot via tool), call the LLM with a “debug” instruction, and ask it to propose either new template params or a patched script.
- Record the diagnosis and fix in memory via `memory.add`.
- Require approval before running patched scripts if `llmRequireScriptApproval=true`.

Acceptance:

Create a reproducible failure (e.g., set goal to attack an NPC that is not visible) and verify:

- the trace shows repeated “no target” / “interaction failed”
- a debug plan is generated
- a memory entry is written describing the fix

## Concrete Steps

All commands are run from repository root.

1. Build and compile (requires Java 11):

   - JAVA_HOME=/path/to/jdk11 ./gradlew clean compileJava

2. Run tests (add these as milestones land):

   - JAVA_HOME=/path/to/jdk11 ./gradlew test
   - JAVA_HOME=/path/to/jdk11 ./gradlew test --tests "com.runepal.agent.*"

3. Manual RuneLite verification (Windows recommended due RemoteInput):

   - Start RuneLite with plugin enabled.
   - Select “Agent” bot type.
   - Enable Local Agent + Enable LLM Planning and set API key.
   - Goal: “Get the skill requirements for Legends' Quest” -> Plan Now -> verify wiki fetch + answer.
   - Goal: “Kill cows” -> Plan Now -> verify combat + trace.
   - Induce a failure -> verify debug + memory.

## Validation and Acceptance

The feature is accepted when a novice can follow the manual steps and observe all of the following:

1. The agent fetches OSRS Wiki pages at runtime (observable via tool log and cache files).
2. The agent can answer an information goal (Legends requirements) in the Agent panel with citations.
3. The agent can start an execution goal (kill cows) and show a live trace of what it’s doing.
4. When the bot stalls, the agent produces a debug proposal and writes a memory entry.
5. No network fetches occur outside the allowlisted OSRS Wiki domain.

## Idempotence and Recovery

Wiki caching and memory files are additive. Re-running the same goal should not corrupt state; it should reuse cache entries within TTL and append new memory entries.

If the wiki fetch fails (no network), the agent should degrade gracefully: it should emit a decision explaining the failure and avoid starting unsafe automation.

If the LLM fails, the agent should fall back to heuristic planning for simple goals and should surface the failure reason in the UI.

## Artifacts and Notes

During implementation, keep these short evidence snippets in this plan:

- a sample tool call JSON and matching tool result
- a sample answer payload for Legends requirements (with citation)
- an example memory JSONL entry
- a trace excerpt showing a stall + debug + fix

Implementation evidence snippets (current state):

    Tool call JSON:
    {"type":"tool","name":"wiki.search","arguments":{"query":"Legends' Quest","limit":1},"callId":"c1"}

    Tool result shape:
    {"ok":true,"message":"ok","payload":{"canonicalUrl":"https://oldschool.runescape.wiki/w/Special:Search?...","fetchedAt":"...","items":[{"title":"Legends' Quest","snippet":"..."}]}}

    Memory JSONL entry shape:
    {"timestamp":"2026-02-26T21:00:00Z","title":"Cow combat","tags":["combat","cow"],"content":"Worked when cows were on-screen","evidence":{}}

    Trace event shape:
    {"timestamp":"...","category":"tool_call","message":"tool called","payload":{"tool":"wiki.fetch","callId":"...","arguments":{"title":"Legends' Quest"}}}

## Interfaces and Dependencies

Do not add heavy dependencies. Prefer Java 11 standard library (`java.net.http.HttpClient`) and existing `gson`.

At the end of the plan, the following stable interfaces should exist.

In `src/main/java/com/runepal/agent/tools/AgentTool.java`, define:

    public interface AgentTool {
        String getName();
        String getDescription();
        JsonArray getArgumentHints();
        AgentToolResult execute(JsonObject arguments, AgentToolContext context);
    }

In `src/main/java/com/runepal/agent/tools/AgentToolResult.java`, define a structured result:

    public final class AgentToolResult {
        public static AgentToolResult ok(JsonObject payload);
        public static AgentToolResult error(String message, JsonObject payload);
        public boolean isOk();
        public String getMessage();
        public JsonObject getPayload();
    }

In `src/main/java/com/runepal/agent/wiki/OsrsWikiClient.java`, define:

    public final class OsrsWikiClient {
        public WikiSearchResult search(String query, int limit);
        public WikiFetchResult fetch(String title, Integer section, WikiFormat format);
    }

In `src/main/java/com/runepal/agent/memory/AgentMemoryStore.java`, define:

    public final class AgentMemoryStore {
        public void append(AgentMemoryEntry entry);
        public List<AgentMemoryEntry> search(String query, int limit, List<String> tags);
    }

In `src/main/java/com/runepal/agent/llm/AgentToolLoopRunner.java`, define:

    public final class AgentToolLoopRunner {
        public AgentToolLoopRunner(LlmClient llmClient, AgentToolDispatcher dispatcher, AgentTraceService traceService);
        public AgentLoopOutcome run(String goal, JsonObject initialSnapshot);
    }

Also ensure `src/main/java/com/runepal/AgentBotPanel.java` has a visible “Answer” area and “Tool/Trace Log” area.

Revision Notes:

- 2026-02-26: Initial ExecPlan created for runtime OSRS Wiki tools, tool-loop planning, observability, and memory.
- 2026-02-26: Updated after implementing automatic stall detection and debug loop scaffolding, including debug evidence capture and memory writes.
- 2026-02-26: Updated after implementation pass to reflect shipped milestones (trace/wiki/tools/memory/tool-loop/UI/tests), recorded Java runtime blocker, and documented remaining self-debug/manual acceptance gaps.
