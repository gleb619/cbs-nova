# Task: {task-id}-{slug}

> Copy this file to `docs/tasks/{task-id}-{slug}.md` and fill in all sections.
> This file is the spec handed to Qwen for implementation. Be exhaustive — Qwen has no other context.

---

## Identity

| Field      | Value                                                                |
|------------|----------------------------------------------------------------------|
| Task ID    | T__                                                                  |
| Title      |                                                                      |
| Phase      | 0-Infra / 1-DSL / 2-DB / 3-Engine / 4-MassOp / 5-BPMN / 6-Dev / 7-FE |
| Blocked By | T__, T__ (must be DONE before starting)                              |
| Modules    | backend / starter / dsl / frontend / frontend-plugin / infra         |

---

## Context

> 2–4 sentences explaining WHY this task exists in the system. Link to the relevant TDD sub-doc.

See: [docs/tdd.md](../tdd.md) §__, [docs/arch/__.md](../arch/__.md)

**Current state:** What already exists that this task builds on.

**After this task:** What capabilities are unlocked.

---

## Scope

### Files to Create

```
path/to/NewFile.java
path/to/AnotherFile.kt
```

### Files to Modify

```
path/to/ExistingFile.java   — what changes (one line description)
path/to/build.gradle        — add dependency X
```

### Files NOT to Touch

```
# List any files that might seem relevant but must not be changed
```

---

## Requirements

### Functional Requirements

> Numbered list. Each item is a concrete behavior that must be true after the task.

1.
2.
3.

### Non-Functional Requirements

- **Testing:** What tests are required (unit / integration / `@WebMvcTest` / `TestWorkflowEnvironment`)
- **Code style:** Follow Google Java Format (run `./gradlew spotlessApply`); Kotlin: standard formatting
- **No new external deps** unless explicitly listed below

### Dependencies to Add

> If new libraries are needed, list exact coordinates and where to add them.

```toml
# gradle/libs.versions.toml
new-lib = { module = "group:artifact", version = "x.y.z" }
```

```groovy
// which module's build.gradle
implementation libs.new.lib
```

---

## Implementation Notes

> Specific implementation details, design decisions, tricky parts. Be precise.

### Key Classes / Interfaces

| Class | Package | Extends / Implements | Notes |
|-------|---------|----------------------|-------|
|       |         |                      |       |

### Method Signatures (critical ones)

```java
// Paste exact method signatures for the most important methods
public ExecutionResult execute(EventExecutionRequest request);
```

### Error Cases

| Condition | Expected behavior |
|-----------|-------------------|
|           |                   |

### Conventions to Follow

- **Naming:** `*Entity`, `*Repository`, `*Service`, `*Controller`, `*Dto` (per ArchUnit rules in `MainConventions.java`)
- **Test method naming:** `shouldXxxWhenYyy` + `@DisplayName` required
- **MockitoBean:** use `@MockitoBean` NOT `@MockBean` (Spring Boot 4.x)
- **Entities:** detected via `NovaAutoConfiguration` `@EntityScan("cbs.nova.entity")` in starter module
- **Controllers in starter:** auto-detected via `@ComponentScan("cbs.nova.controller")`

---

## Acceptance Criteria

> Qwen MUST verify each of these before marking done. Paste terminal output as evidence.

- [ ] `./gradlew :backend:build` passes (no compile errors)
- [ ] `./gradlew :backend:test` passes (all unit tests green)
- [ ] `./gradlew spotlessApply && ./gradlew checkstyleMain` passes
- [ ] _(task-specific)_ `curl` or test output demonstrating the feature works
- [ ] _(task-specific)_ Add any additional acceptance checks here

---

## Out of Scope

> What this task explicitly does NOT include (to prevent scope creep).

- Frontend changes (Phase 7)
- (list other exclusions)
