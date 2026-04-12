---
name: kiro-task-planner
description: Use when the user wants to plan, spec, or scaffold a new feature or task for CBS-Nova. Invokes the kiro CLI to scan the codebase and generate a structured task spec at docs/tasks/{T##-slug}.md using the project's canonical task template.
---

# Skill: Kiro Task Planner

## Overview

Claude invokes `~/.local/bin/kiro-cli` to scan the CBS-Nova codebase and produce a
fully-populated task spec file following the project's canonical template.
The result is saved to `docs/tasks/{T##-slug}.md` and is ready to hand off
to Qwen (via `qwen-delegation` claude skill) for implementation.

---

## When to Use

- User says "plan feature X", "create a task for X", "spec out X", "scaffold X"
- User wants a task file that follows the CBS-Nova task template format
- User wants to use kiro to generate a codebase-aware task spec

---

## Step 1 — Determine Task ID and Slug

1. List existing files in `docs/tasks/` and find the highest `T##` prefix.
2. Increment by 1 to get the next task ID (e.g. `T09` if highest is `T08`).
3. Slugify the feature name: lowercase, hyphens, no spaces (e.g. `temporal-config`).
4. Target file path: `docs/tasks/{T##}-{slug}.md` (e.g. `docs/tasks/T09-temporal-config.md`)

If `docs/tasks/` is empty or has no `T##` files, start at `T01`.

---

## Step 2 — Check for Conflicts

If the target file already exists, ask the user:
> "File `docs/tasks/{T##-slug}.md` already exists. Overwrite, append, or create `{T##-slug}-v2.md`?"

Do not proceed until the user confirms.

---

## Step 3 — Invoke Kiro CLI

Run the following command (replace all `{…}` placeholders with real values):

```bash
~/.local/bin/kiro-cli chat --no-interactive --trust-all-tools "You are generating a CBS-Nova task specification.

Target file: docs/tasks/{T##-slug}.md

Scan the entire project codebase before writing. Use real file paths — do not hallucinate paths that do not exist. Mark files that will be created as [NEW].

The project uses a Gradle multi-module structure:
- backend/         — Spring Boot app (Java 25, Lombok, MapStruct)
- starter/         — Library JAR (auto-configured, JPA entities, Flyway migrations)
- client/          — Generated Feign + TypeScript clients
- frontend/        — Nuxt 3 SPA (Vue 3, Tailwind v4, piqure DI)
- frontend-plugin/ — Nuxt layer (shared domain types, ports, presentational components)
- dsl/             — Kotlin Script DSL module

Read the existing task template at docs/task-template.md.
Read 2–3 existing task files in docs/tasks/ to learn the format and naming conventions.
Then write the spec for:

Feature: {Feature Name}

The output markdown file MUST follow this exact structure:

# Task: {T##} — {Feature Name}

> This file is the spec for implementation. Be exhaustive — you have no other context beyond this file.

---

## Identity

| Field      | Value                        |
|------------|------------------------------|
| Task ID    | {T##}                        |
| Title      | {Feature Name}               |
| Phase      | 0-Infra / 1-DSL / 2-DB / 3-Engine / 4-MassOp / 5-BPMN / 6-Dev / 7-FE |
| Blocked By | T__, T__ (must be DONE before starting) |
| Modules    | backend / starter / dsl / frontend / frontend-plugin / infra |

---

## Context

2–4 sentences explaining WHY this task exists. Reference any relevant existing task IDs.

**Current state:** What already exists that this task builds on.

**After this task:** What capabilities are unlocked.

---

## Scope

### Files to Create

\`\`\`
path/to/NewFile.java   — purpose [NEW]
\`\`\`

### Files to Modify

\`\`\`
path/to/ExistingFile.java   — what changes (one line description)
\`\`\`

### Files NOT to Touch

\`\`\`
# List sensitive files that must not be changed
\`\`\`

---

## Requirements

### Functional Requirements

1. (concrete, verifiable behavior)
2.
3.

### Non-Functional Requirements

- **Testing:** Unit tests in backend/src/test/ using @WebMvcTest or @SpringBootTest
- **Code style:** Google Java Format (./gradlew spotlessApply); Kotlin: standard formatting
- **No new external deps** unless listed below

### Dependencies to Add

\`\`\`toml
# gradle/libs.versions.toml — only if new libraries are needed
\`\`\`

---

## Implementation Notes

### Key Classes / Interfaces

| Class | Package | Extends / Implements | Notes |
|-------|---------|----------------------|-------|

### Method Signatures (critical ones)

\`\`\`java
// Exact signatures for the most important methods
\`\`\`

### Error Cases

| Condition | Expected behavior |
|-----------|-------------------|

### Conventions to Follow

- Naming: *Entity, *Repository, *Service, *Controller, *Dto (ArchUnit rules in MainConventions.java)
- Test method naming: shouldXxxWhenYyy + @DisplayName required
- Use @MockitoBean NOT @MockBean (Spring Boot 4.x)
- Entities detected via NovaAutoConfiguration @EntityScan(\"cbs.nova.entity\")

---

## Acceptance Criteria

- [ ] ./gradlew :backend:build passes (no compile errors)
- [ ] ./gradlew :backend:test passes (all unit tests green)
- [ ] ./gradlew spotlessApply && ./gradlew checkstyleMain passes
- [ ] (task-specific) curl or test output demonstrating the feature works

---

## Out of Scope

- (list what this task explicitly does NOT include)

Output ONLY the markdown file at docs/tasks/{T##-slug}.md. No preamble, no explanation, no extra text."
```

---

## Step 4 — Verify Output

After kiro writes the file:

- [ ] File exists at `docs/tasks/{T##-slug}.md`
- [ ] All listed file paths exist in the repo OR are explicitly marked `[NEW]`
- [ ] Task ID in the Identity table matches the filename prefix
- [ ] Acceptance criteria are testable, not vague
- [ ] No unfilled placeholder text remains (`[TBD]`, `...`, empty table rows)

If any check fails:

| Problem                   | Action                                                                    |
|---------------------------|---------------------------------------------------------------------------|
| Hallucinated paths        | Note the bad paths, re-invoke kiro with those paths called out explicitly |
| Missing sections          | Manually edit the file using `docs/task-template.md` as reference         |
| Kiro returns empty output | Re-invoke with `--verbose` flag and report output to user                 |

---

## Step 5 — Announce Result

Tell the user:
> "Task spec created at `docs/tasks/{T##-slug}.md`. Ready to delegate to Qwen with the `qwen-delegation` skill."

---

## Error Handling

| Condition                         | Action                                                                          |
|-----------------------------------|---------------------------------------------------------------------------------|
| `~/.local/bin/kiro-cli` not found | Tell user: check that kiro-cli is installed at `~/.local/bin/kiro-cli`          |
| File already exists               | Ask user: overwrite / append / versioned copy (`-v2`)                           |
| Kiro returns empty output         | Re-run with `--verbose`, report output to user                                  |
| Paths in spec do not exist        | Flag them, ask user whether they are `[NEW]` or hallucinated                    |
| Kiro times out                    | Report timeout; offer to write task spec manually using `docs/task-template.md` |
