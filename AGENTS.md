# AGENTS.md

Guidance for autonomous coding agents working in this repository.
Use this with `CLAUDE.md` as the primary project instructions.

## Project At A Glance
- Project name: `runepal`
- Primary language/runtime: Java 11 RuneLite plugin
- Input runtime: Native RemoteInput via JNA (Windows-focused)
- Build system: Gradle wrapper (`gradlew`, `gradlew.bat`)
- Java entry point: `com.runepal.RunepalPlugin`
- Harness launcher class: `com.runepal.RunepalTest`

## Repository Layout
- Java source: `src/main/java/com/runepal/**`
- Java tests: `src/test/java/com/runepal/**`
- Java resources: `src/main/resources/**`
- Legacy automation prototype (not source-of-truth): `automation_server/**`
- Build config: `build.gradle`, `settings.gradle`, `gradle/wrapper/**`

## Build / Lint / Test Commands
Run all commands from repository root. Use `gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

### Java Runtime Requirement (Important)
- Run Gradle with JDK 11 (`JAVA_HOME` should point to a Java 11 installation).
- IntelliJ can appear to work while terminal Gradle fails if IntelliJ uses Project SDK 11 but shell `JAVA_HOME` points to a newer JDK.
- Common wrong-JDK failure signatures include:
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
# compile main code only
./gradlew compileJava
# compile tests only
./gradlew compileTestJava
# run unit/integration tests configured in Gradle
./gradlew test
# run verification lifecycle (includes test)
./gradlew check
# full build output
./gradlew build
# clean build artifacts
./gradlew clean
# build fat jar with launcher main-class manifest
./gradlew shadowJar
```

### Running A Single Test (Important)
```bash
# single test class
./gradlew test --tests "com.runepal.ExampleTest"
# single test method
./gradlew test --tests "com.runepal.ExampleTest.testSpecificScenario"
# multiple tests via wildcard
./gradlew test --tests "com.runepal.*"
```

Notes:
- The current `RunepalTest` is a launcher-style main class, not a typical assertion-based JUnit test.
- For new tests, place files in `src/test/java` and keep them JUnit4-compatible.

### Running The Plugin Harness
```bash
# build runnable jar
./gradlew shadowJar
# run built harness artifact
java -jar build/libs/runepal-0.3-all.jar
```

### Lint / Static Analysis Status
- No dedicated Java lint plugin (Checkstyle/Spotless/PMD) is configured in `build.gradle`.
- Baseline verification is `./gradlew check` plus targeted compile/test commands.

## Code Style Guidelines
Follow existing local file style first; avoid large formatting-only diffs.

### Imports
- Prefer explicit imports in new/edited code; avoid introducing new wildcard imports.
- Keep imports grouped logically: JDK, third-party, then `com.runepal`.
- Remove unused imports when touching a file.

### Formatting
- Java target level is 11; do not use APIs requiring newer runtime.
- Preserve surrounding indentation style (the repo currently mixes tabs and spaces).
- Keep braces/wrapping consistent with the file you are editing.
- Prefer guard clauses over deep nesting when behavior is unchanged.
- Keep methods focused; split complex logic into helpers when readability improves.

### Types And Nullability
- Prefer domain types and typed collections over raw types.
- Use generics explicitly (`List<Foo>`, `Map<K, V>`).
- Use `final` for dependencies/fields that should not be reassigned.
- Validate constructor dependencies with `Objects.requireNonNull` where appropriate.

### Naming
- Classes/interfaces: `PascalCase`
- Methods/fields/locals: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Enum values: `UPPER_SNAKE_CASE`
- Common suffixes: `*Task`, `*Service`, `*Panel`, `*Overlay`

### Error Handling And Logging
- Use SLF4J logging (`@Slf4j`) for Java runtime visibility.
- Prefer placeholder logs: `log.warn("message {}", value)`.
- Fail fast on invalid programmer inputs; return safely on recoverable runtime conditions.
- Do not swallow exceptions silently; include actionable context in logs.
- If catching `InterruptedException`, call `Thread.currentThread().interrupt()`.

### Concurrency And Lifecycle
- Keep game tick/event loop paths non-blocking.
- Use thread-safe primitives for shared mutable state (`volatile`, `AtomicBoolean`, concurrent collections).
- Preserve lifecycle symmetry: subscribe in `onStart`, unsubscribe in `onStop`.
- Shut down executors/resources in owned shutdown paths.

### RuneLite Task Architecture
- Implement the `BotTask` contract fully (`onStart/onLoop/onStop/isFinished/isStarted/getTaskName`).
- Model long-running behaviors as finite-state machines with clear state enums.
- Route cross-component events through `EventService` instead of tight coupling.
- Reuse `HumanizerService` for delays/randomization rather than scattering ad hoc timing logic.
- Keep UI updates flowing through plugin-managed state instead of task/UI hard coupling.

### RemoteInput Architecture
- Treat RemoteInput as the active and supported input path.
- Route new input behavior through `ActionService`, `WindmouseService`, and `RemoteInputService`.
- Keep connection lifecycle handling in plugin startup/shutdown paths.
- Do not add new Python named-pipe dependencies for runtime input.

## Testing Expectations
- For Java logic changes: run at least `./gradlew compileJava`.
- For behavior changes: run `./gradlew test` or the narrowest `--tests` target.
- Manual in-game verification is often required due interactive RuneLite behavior.

## Change Hygiene For Agents
- Keep changes minimal and scoped to the requested task.
- Do not perform unrelated refactors while implementing requested work.
- Preserve existing behavior unless task requirements call for a change.
- When a command fails, capture the key error and propose the smallest corrective action.
- Before handing off, ensure touched files compile and references/resources resolve correctly.
