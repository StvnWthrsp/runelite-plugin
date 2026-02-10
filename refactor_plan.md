# Refactor Plan

Date: 2026-02-08  
Repository: `runelite-plugin`  
Objective: Refactor runtime/task architecture for correctness, maintainability, and extensibility without a risky full rewrite.

## 1) Goals

- Eliminate critical runtime correctness issues (task teardown, click semantics, banking flow, map rehash safety).
- Reduce duplication in bot tasks and panel code.
- Improve lifecycle ownership of threads, schedulers, and event subscriptions.
- Make behavior testable with focused unit/integration tests.
- Preserve working shortest-path architecture and evolve it safely.

## 2) Non-Goals

- No full rewrite of shortest-path/pathfinder stack.
- No redesign of RuneLite UI style patterns.
- No migration to a different runtime language/framework.

## 3) Target Architecture (Incremental)

Proposed package-oriented target (gradual migration, not big-bang):

- `com.runepal.runtime`
  - `TaskRuntime`, `TaskStack`, `TaskLifecycle`
  - `SubscriptionBag` (central event subscription ownership/disposal)
- `com.runepal.interaction`
  - `InteractionService` (menu resolution, hover verification, click policies)
  - `InputService` (explicit `clickCurrent`, `clickAt`, `rightClickAt`)
- `com.runepal.banking`
  - `BankingService`, `BankPlan`, `WithdrawalPlan`
- `com.runepal.bots.gathering`
  - `GatheringTaskBase` + strategies (`MiningStrategy`, `WoodcuttingStrategy`, optional `FishingStrategy`)
- `com.runepal.bots.combat`
  - Hardened `CombatTask`, `SandCrabTask`, shared combat utilities
- `com.runepal.ui`
  - Common bot panel components and reusable form bindings

## 4) Execution Strategy

Use strangler/branch-by-abstraction:

- Introduce new interfaces and adapters first.
- Migrate one bot/task path at a time.
- Keep old paths working until replaced.
- Add tests before or alongside each migration slice.

---

## Phase 0 - Critical Stabilization (Immediate, 2-4 days)

### Deliverables

- Fix `TaskManager.clearTasks()` to stop all stacked tasks.
- Split click APIs (`clickCurrent` vs `clickAt`) and update all critical call sites.
- Null-guard menu bounds in interaction pipeline.
- Fix `PrimitiveIntHashMap.rehash()` early return bug.
- Fix mining bank config serialization consistency.
- Ensure overlay add/remove symmetry.

### Exit Criteria

- No known critical correctness defect remains from review CR-01..CR-06.
- `compileJava` and `test` pass.
- Manual smoke tests: mining, woodcutting, combat eating/potioning, panel start/stop.

---

## Phase 1 - Lifecycle Ownership and Event Safety (3-5 days)

### Deliverables

- Introduce `SubscriptionBag` utility for deterministic subscribe/unsubscribe.
- Refactor tasks still using ephemeral method refs (e.g., fishing, bank task).
- Add `shutdown()` in `ActionService` to stop scheduler and unsubscribe handlers.
- Standardize lifecycle: every service/task owns and releases exactly what it creates.

### Exit Criteria

- No task/service subscribes without deterministic unsubscribe path.
- No scheduler thread remains after plugin shutdown.
- Add lifecycle unit tests for start/stop idempotency.

---

## Phase 2 - Banking and Supply Orchestration (4-6 days)

### Deliverables

- Add `BankPlan` (`depositAll`, `withdraw(itemId, qty)`) and `BankingService`.
- Refactor `BankTask` to execute withdrawal plans (not just deposit).
- Wire `SandCrabTask` and `FishingTask` to pass explicit plans.
- Remove unreachable/unused banking states and branches.

### Exit Criteria

- Sand crab and fishing can bank + re-supply deterministically.
- `BankState.WITHDRAWING` path is either fully used or removed.
- Add integration tests around inventory low -> bank -> return to activity.

---

## Phase 3 - Deduplicate Gathering Bots (5-8 days)

### Deliverables

- Introduce `GatheringTaskBase` with common FSM mechanics:
  - find target
  - interact
  - wait for animation/xp signal
  - inventory policy (drop/bank)
  - subtask pause/resume
- Implement mining and woodcutting as strategy specializations.
- Extract common utility for next-target hover + target validity checks.

### Exit Criteria

- Mining and woodcutting task duplication reduced by at least 50%.
- Behavior parity confirmed via manual flows.
- Defect fixes land once in shared code, not twice.

---

## Phase 4 - UI Consolidation and Config Normalization (3-5 days)

### Deliverables

- Build reusable panel sections (mode selector, bank selector, status/control footer).
- Standardize enum/string config serialization for all bank/location settings.
- Remove orphan config keys and dead fields.
- Keep existing UX and config semantics stable.

### Exit Criteria

- Mining and woodcutting panel duplication significantly reduced.
- No config key uses mixed serialization conventions.
- Config migration backward-compatible.

---

## Phase 5 - Dead Code Cleanup and Placeholder Hardening (2-4 days)

### Deliverables

- Remove confirmed dead artifacts (`FishingSpots`, `PendingTask`, unreachable states).
- Decide explicit policy for placeholder tasks (e.g., `WorldHopTask`):
  - fully implement, or
  - gate behind experimental flag and mark non-production.
- Revisit unused services (`PrayerService`, `SupplyManager`) and either integrate or remove.

### Exit Criteria

- No dead code from review remains without owner/decision.
- Placeholder behavior is explicit and intentional.

---

## Phase 6 - Testing, Telemetry, and Reliability Gate (4-6 days)

### Deliverables

- Add focused tests for:
  - task stack teardown semantics
  - interaction click semantics
  - banking plan execution
  - rehash integrity for custom map
- Add lightweight operational telemetry:
  - per-task state transitions
  - interaction failures by reason
  - task stop cause metrics
- Pin dependency versions for reproducible builds.

### Exit Criteria

- CI includes meaningful behavior tests (not harness-only).
- Refactor release candidate has stable runtime behavior in smoke scenarios.

---

## 6) Prioritized Backlog (Top 12)

1. Fix `TaskManager.clearTasks` full stack stop.
2. Refactor click API semantics and update consumers.
3. Null-guard menu bounds before click point generation.
4. Repair `PrimitiveIntHashMap.rehash`.
5. Fix mining bank config serialization.
6. Add `ActionService.shutdown` and call it in plugin shutdown.
7. Fix event unsubscribe identity in fishing/bank tasks.
8. Implement `BankPlan` and wire withdrawals.
9. Clear/rebuild `WalkTask` transport caches safely.
10. Add null guards in `GameStateService` around player/hulls.
11. Remove dead classes/enums/states.
12. Add targeted unit/integration tests.

## 7) Risk Management

- Risk: Behavior regressions in active bots  
  Mitigation: Migrate one bot at a time with smoke checklist and feature flags.

- Risk: Refactor stalls due to broad scope  
  Mitigation: Phase gates with hard exit criteria and no cross-phase creep.

- Risk: Event/thread lifecycle regressions  
  Mitigation: Lifecycle tests + centralized ownership abstractions.

## 8) High-Level Effort Estimate

- Total: about 4-7 weeks (single engineer), depending on test depth and gameplay verification cycles.
- Critical stabilization can ship in week 1.

## 9) Definition of Done

Refactor is complete when:

- Critical correctness issues are closed and verified.
- Lifecycle ownership is deterministic for all tasks/services.
- Banking/supply behavior is declarative and reliable.
- Major duplication (gathering tasks/panels) is materially reduced.
- Dead code/placeholders are removed or explicitly gated.
- CI has behavior tests covering core bot runtime paths.
