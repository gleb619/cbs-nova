---
name: opencode-delegation
description: Use when delegating implementation work to OpenCode as an executor agent — Claude plans and writes a task spec, OpenCode executes it via bash, Claude verifies the result. Triggers when user asks to "use opencode", "delegate to opencode", "let opencode implement this", or when a task is well-scoped and can be handed off for execution.
---

# Skill: Orchestrated Task Delegation to OpenCode

## Overview

Claude acts as the **thinking center**: plans, writes a task spec, runs OpenCode
via `Bash`, then verifies the result.
OpenCode acts as the **executor**: reads the task file and performs the work.

---

## Roles

| Agent      | Role         | Responsibility                                           |
|------------|--------------|----------------------------------------------------------|
| `claude`   | Orchestrator | Plan, write task spec, run OpenCode, verify result       |
| `opencode` | Executor     | Read task file, perform work, write result file          |

---

## Workflow

```
[User Request]
      │
      ▼
[1. Claude: Analyze & Plan]
      │
      ▼
[2. Claude: Write docs/tasks/{task-name}.md]
      │
      ▼
[3. Claude: Run OpenCode via Bash]
      │   bash: ~/.opencode/bin/opencode run "Read docs/tasks/{task-name}.md..."
      │   → OpenCode writes result to docs/results/{task-name}.result.md
      ▼
[4. OpenCode: Execute task, write result file]
      │
      ▼
[5. Claude: Read result file + re-run verification commands]
      │
      ├── PASS ──► Done ✅
      │
      └── FAIL ──► [6. Claude: Amend task file, re-run OpenCode (retry #1)]
                         │
                         ▼
                   [7. Claude: Verify again]
                         │
                         ├── PASS ──► Done ✅
                         └── FAIL ──► Escalate to user ❌
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Claude must:

- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Identify files to create/modify following the hexagonal structure
- Determine new packages, DB migrations, or OpenAPI changes needed
- Decide on the exact verification method (tests, lint, grep, manual check)
- Choose a `task-name` in `kebab-case` (e.g., `add-auth-middleware`)

**Do not write the task file until analysis is complete.**

---

## Step 2 — Task File Format

File path: `docs/tasks/{task-name}.md`

````markdown
# Task: {task-name}

## Description
<!-- What and why, one paragraph -->

## Context
<!-- Relevant patterns, constraints, reference files from docs/ -->

## Module Scope
- [ ] backend
- [ ] starter
- [ ] client
- [ ] frontend
- [ ] frontend-plugin

## Requirements

### Backend / Starter (if applicable)
1. ...

### Client (if applicable)
1. ...

### Frontend / Frontend-Plugin (if applicable)
1. ...

## Scope

### Files to Create
- `path/to/new/file`

### Files to Modify
- `path/to/existing/file` — reason

### Files to Leave Untouched
- `docs/` — reference only unless task explicitly targets docs

## Implementation Notes

### Backend / Starter
- Java 25 + Kotlin mixed; Spring Boot 4.x
- Naming: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Mapper`, `*Dto`
- Layer rule: Controller → Service → Repository → Entity (no skipping)
- Use `@MockitoBean` (NOT `@MockBean`)
- Test methods: `shouldXxxWhenYyy` + `@DisplayName` required
- DB migrations: `starter/src/main/resources/db/migration/V{timestamp}__{description}.sql`
- Format before commit: `./gradlew spotlessApply`

### Frontend / Frontend-Plugin
- Hexagonal split: domain/ports in `frontend-plugin/`, adapters/pages in `frontend/`
- `frontend-plugin` must NEVER import from `frontend/`
- DI via piqure: `provide` and `inject` must come from the **same** `piqureWrapper` instance
- HTTP adapter uses `AxiosHttp` + Bearer token from `TokenStorage.get()`
- Biome 1.9.4: 2-space indent, single quotes, semicolons, line width 140

## Verification

OpenCode must run ALL applicable commands and include full output in the result file.

### Backend
```bash
./gradlew spotlessApply
./gradlew :backend:test
./gradlew check
```

### Starter
```bash
./gradlew :starter:test
./gradlew :starter:check
```

### Client (after OpenAPI changes)
```bash
./gradlew :backend:exportOpenApi
./gradlew generateAllClients
```

### Frontend
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend && pnpm lint
cd frontend && pnpm test
cd frontend && pnpm build
```

### Frontend-Plugin
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend-plugin && pnpm lint
cd frontend-plugin && pnpm test
```

### Expected Outcomes
- All commands exit code 0
- All "Files to Create" exist on disk
- No hardcoded secrets or credentials
- Naming conventions followed (ArchUnit will catch violations)
- `frontend-plugin` has no imports from `frontend/`

## Result File

After completing the task, write a result file to:
`docs/results/{task-name}.result.md`
````

---

## Step 3 — Running OpenCode

After writing the task file, Claude runs OpenCode using the `Bash` tool with a **long timeout** (600000 ms minimum):

```bash
~/.opencode/bin/opencode run --dangerously-skip-permissions "Read the file docs/tasks/{task-name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/{task-name}.result.md as instructed."
```

**Critical rules for this step:**
- Use the `Bash` tool — NOT any MCP tool
- Set `timeout` to at least `600000` (10 minutes) — OpenCode takes time
- The command must be a single bash string
- If opencode binary is not at `~/.opencode/bin/opencode`, locate it first:

```bash
which opencode 2>/dev/null || ls ~/.opencode/bin/ 2>/dev/null || ls ~/bin/opencode 2>/dev/null
```

Pass `--continue` to resume OpenCode's last session (useful for retries):
```bash
~/.opencode/bin/opencode run --dangerously-skip-permissions --continue "Read the updated docs/tasks/{task-name}.md..."
```

---

## Step 4 — Result File Format (OpenCode must produce this)

File path: `docs/results/{task-name}.result.md`

```markdown
# Result: {task-name}

## Status
<!-- DONE | PARTIAL | FAILED -->

## Summary
<!-- What was done, briefly -->

## Files Changed

| File | Action |
|------|--------|
| `path/to/file` | created / modified / deleted |

## Verification Output

### Command: `./gradlew check`

exit code: 0
```
<stdout here>
```

## Issues Encountered
<!-- Any blockers, assumptions made, deviations from spec -->

## Self-Assessment
<!-- PASS or FAIL, and why -->
```

---

## Step 5 — Verification by Claude

After OpenCode finishes, Claude must:

1. **Read the result file** at `docs/results/{task-name}.result.md`
2. **Check Self-Assessment** — note if OpenCode flagged issues
3. **Re-run verification commands independently** via `Bash`
4. **Check expected outcomes** from the task file one by one
5. **Make a final PASS/FAIL decision**

---

## Step 6 — Retry Logic

- Maximum **2 total runs** (1 retry allowed)
- Before retrying, Claude **must amend the task file** — append a `## Retry Notes` section:

```markdown
## Retry Notes (Attempt 2)
The following issues were found in attempt 1:
- Test `SomeTest` failed because `SomeService` was not injected
- Checkstyle error in `SomeController.java` line 42

Please fix these specifically before re-running verification.
```

Then re-run with `--continue`:
```bash
~/.opencode/bin/opencode run --dangerously-skip-permissions --continue "Read the updated docs/tasks/{task-name}.md. Pay attention to the Retry Notes section. Fix the listed issues and update docs/results/{task-name}.result.md."
```

---

## Step 7 — Escalation

If still failing after retry #1:

1. Do not attempt further retries
2. Report to user:
   - Summary of what was tried
   - Exact failure reason
   - Suggestion for how to proceed manually

---

## Naming Conventions

| Artifact  | Path                                 |
|-----------|--------------------------------------|
| Task spec | `docs/tasks/{task-name}.md`          |
| Result    | `docs/results/{task-name}.result.md` |

`task-name` rules:
- `kebab-case`
- Descriptive but concise (3–6 words max)
- Examples: `add-auth-middleware`, `refactor-user-service`, `fix-pagination-bug`

---

## Quick Reference Checklist (Claude internal)

Before invoking OpenCode:

- [ ] Task file written to correct path
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path is stated inside the task file

After OpenCode finishes:

- [ ] Result file exists
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] PASS/FAIL decision made before considering retry
