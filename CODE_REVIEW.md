# Code Review Report

Date: 2026-02-08  
Repository: `runelite-plugin`  
Scope: Full Java plugin codebase + legacy automation folder

## Review Method

- Reviewed all Java sources in `src/main/java/com/runepal/**` and test harness in `src/test/java/com/runepal/**`.
- Reviewed build/config files and legacy automation scripts under `automation_server/**`.
- Ran validation commands with JDK 11:
  - `JAVA_HOME="C:/Users/steven/.jdks/ms-11.0.30" ./gradlew compileJava`
  - `JAVA_HOME="C:/Users/steven/.jdks/ms-11.0.30" ./gradlew test`
- Both commands succeeded.

## Executive Summary

The project has strong domain progress, but there are several runtime-critical correctness issues, especially around task lifecycle, click semantics, and incomplete banking flows.

There is also substantial duplication (especially mining/woodcutting and interaction logic), plus dead/inactive code.

Verdict: A full rewrite is not needed, but a full refactor of the bot runtime/task architecture is needed.

---

## Critical Findings

| ID | Severity | Finding | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|---|
| CR-01 | Critical | `TaskManager.clearTasks()` only stops top task then clears stack | Leaks subscriptions/schedulers/resources from paused parent tasks | `src/main/java/com/runepal/TaskManager.java:62` | Pop all tasks and call `onStop()` for each in LIFO order |
| CR-02 | Critical | `sendClickRequest(point, false)` ignores `point` and clicks current mouse position | Eating/potions/prayers can click wrong target and fail silently | `src/main/java/com/runepal/ActionService.java:749`, `src/main/java/com/runepal/CombatTask.java:366`, `src/main/java/com/runepal/PotionService.java:167`, `src/main/java/com/runepal/PrayerService.java:187` | Split API into `clickCurrent()` vs `clickAt(point)`; update call sites |
| CR-03 | Critical | Menu bounds null risk in right-click flow | Potential NPE during interaction pipeline | `src/main/java/com/runepal/ActionService.java:599`, `src/main/java/com/runepal/ActionService.java:618`, `src/main/java/com/runepal/ActionService.java:687`, `src/main/java/com/runepal/ActionService.java:706` | Guard `menuBounds` before generating click point; fail gracefully |
| CR-04 | Critical | Sand crab banking plan built but never executed | Banking loop cannot satisfy required supplies; behavior stalls/repeats | `src/main/java/com/runepal/SandCrabTask.java:851`, `src/main/java/com/runepal/SandCrabTask.java:870`, `src/main/java/com/runepal/BankTask.java:100` | Make `BankTask` consume withdrawal plan; wire from `SandCrabTask` |
| CR-05 | Critical | `PrimitiveIntHashMap.rehash()` may return early while rehashing | Risk of dropped map entries and incorrect pathing behavior | `src/main/java/com/runepal/shortestpath/PrimitiveIntHashMap.java:209` | Remove premature `return`; complete full rehash pass |
| CR-06 | Critical | Mining bank config serialization inconsistent | Wrong bank value persistence and possible null/invalid bank target | `src/main/java/com/runepal/MiningBotPanel.java:93`, `src/main/java/com/runepal/MiningBotPanel.java:110`, `src/main/java/com/runepal/RunepalPlugin.java:414` | Store explicit `enum.name()` consistently |

---

## Major Findings

| ID | Severity | Finding | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|---|
| MJ-01 | Major | Unsubscribe identity bug where method refs are re-created | Event handlers can remain subscribed after stop | `src/main/java/com/runepal/FishingTask.java:94`, `src/main/java/com/runepal/FishingTask.java:103`, `src/main/java/com/runepal/BankTask.java:67`, `src/main/java/com/runepal/BankTask.java:181` | Store handler refs in fields and reuse for unsubscribe |
| MJ-02 | Major | `ActionService` lifecycle is unmanaged (scheduler + subscriptions) | Thread/resource leaks across plugin lifecycle | `src/main/java/com/runepal/ActionService.java:57`, `src/main/java/com/runepal/ActionService.java:66` | Add `shutdown()` and call it in plugin shutdown |
| MJ-03 | Major | `WalkTask` transport lists accumulate across recalculations; index safety issue | Incorrect transport decisions, potential OOB in edge case | `src/main/java/com/runepal/WalkTask.java:49`, `src/main/java/com/runepal/WalkTask.java:694`, `src/main/java/com/runepal/WalkTask.java:706` | Clear caches before rebuild and bound-check `i + 1` |
| MJ-04 | Major | Null-safety gaps in game state methods | Intermittent NPEs around login transitions/convex hull nulls | `src/main/java/com/runepal/services/GameStateService.java:60`, `src/main/java/com/runepal/services/GameStateService.java:252` | Add null guards for local player and hulls |
| MJ-05 | Major | `WorldHopTask` is placeholder-level implementation | Behavior appears complete but is not production-ready | `src/main/java/com/runepal/WorldHopTask.java:199`, `src/main/java/com/runepal/WorldHopTask.java:225`, `src/main/java/com/runepal/WorldHopTask.java:269` | Mark experimental or complete implementation |
| MJ-06 | Major | Overlay lifecycle mismatch: add but not remove `menuDebugOverlay` | UI artifact/resource leak on plugin shutdown | `src/main/java/com/runepal/RunepalPlugin.java:132`, `src/main/java/com/runepal/RunepalPlugin.java:171` | Remove all added overlays symmetrically |
| MJ-07 | Major | Dependency version uses `latest.release` | Non-reproducible builds and brittle CI | `build.gradle:16` | Pin explicit dependency versions |

---

## Duplication Findings

| Area | Duplication | Evidence | Refactor Direction |
|---|---|---|---|
| Gathering tasks | Mining and woodcutting are near-clones | `src/main/java/com/runepal/MiningTask.java`, `src/main/java/com/runepal/WoodcuttingTask.java` | Create shared `GatheringTask` base + strategy hooks |
| Gathering UI panels | Mining and woodcutting panel/event wiring duplicated | `src/main/java/com/runepal/MiningBotPanel.java`, `src/main/java/com/runepal/WoodcuttingBotPanel.java` | Build common configurable panel components |
| Interaction logic | NPC/gameobject menu interaction duplicated | `src/main/java/com/runepal/ActionService.java:557`, `src/main/java/com/runepal/ActionService.java:642` | Consolidate to one polymorphic interaction path |
| Transport checks | `TransportVarbit` and `TransportVarPlayer` are near-identical | `src/main/java/com/runepal/shortestpath/TransportVarbit.java`, `src/main/java/com/runepal/shortestpath/TransportVarPlayer.java` | Unify under shared requirement evaluator |

---

## Dead Code / Inactive Paths

| Type | Finding | Evidence | Action |
|---|---|---|---|
| Unused enum | `FishingSpots` is never referenced | `src/main/java/com/runepal/FishingSpots.java:3` | Remove |
| Unused class | `PendingTask` has no references | `src/main/java/com/runepal/shortestpath/PendingTask.java:3` | Remove |
| Unused dependency | `SupplyManager` injected into sand crab task but unused | `src/main/java/com/runepal/SandCrabTask.java:44`, `src/main/java/com/runepal/SandCrabTask.java:257` | Integrate or remove |
| Unused service usage | `PrayerService` is instantiated but not consumed by bot task logic | `src/main/java/com/runepal/RunepalPlugin.java:159` | Integrate into combat/sand crab FSM or remove |
| Unreachable state path | `BankState.WITHDRAWING` exists without transition | `src/main/java/com/runepal/BankTask.java:26`, `src/main/java/com/runepal/BankTask.java:86` | Add transitions or delete state |
| Inactive metrics | Session XP/runtime fields are never initialized | `src/main/java/com/runepal/RunepalPlugin.java:78`, `src/main/java/com/runepal/RunepalPlugin.java:445` | Initialize on bot start or remove feature |

---

## Quality and Testability Notes

- Current tests are harness-only and not assertion-based behavior tests: `src/test/java/com/runepal/RunepalTest.java:6`.
- Highest-value missing tests:
  - Task lifecycle teardown semantics
  - Click semantics (`clickCurrent` vs `clickAt`)
  - Banking withdrawal plan execution
  - `PrimitiveIntHashMap` rehash integrity

---

## Recommendation: Is Full Refactor Needed?

Yes, for the runtime/task layer. Not needed for a full codebase rewrite and not needed for a shortest-path rewrite.

Refactor scope should focus on:

1. Task lifecycle correctness and event ownership
2. Input/interaction API correctness
3. Banking and supply orchestration
4. Duplication removal in gathering bots and panels

Keep shortest-path modules, apply targeted bug fixes, and harden with tests.
