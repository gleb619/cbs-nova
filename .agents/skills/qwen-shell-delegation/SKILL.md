---
name: qwen-shell-delegation
description: >
  Delegate implementation tasks to the Qwen CLI by running it through the
  Shell tool. Spawns qwen as a background process with a long timeout,
  avoiding the ~120 s MCP tool timeout ceiling. After qwen finishes, the
  agent reads the result file and performs independent verification.
---

# Skill: Qwen Shell Delegation

## Overview

This skill delegates implementation work to the **Qwen CLI** (`qwen`)
by invoking it via the **Shell tool** with `run_in_background=true`.

**Prerequisites**
- `qwen` CLI is installed under Node 22.20.0 (managed by `nvm`).
- Task files live in `docs/tasks/{task-name}.md`.
- Result files are written by qwen to `docs/results/{task-name}.result.md`.

---

## Workflow

```
[User describes feature/task]
│
▼
[1. Agent: Analyze & Plan]
│   - Understand scope and affected modules
│   - Identify files to create / modify
│   - Choose verification commands
│   - Pick task-name (kebab-case)
│
▼
[2. Agent: Write docs/tasks/{task-name}.md]
│   (use the full template below)
│
▼
[3. Agent: Launch qwen via Shell]
│   - run_in_background: true
│   - timeout: 1800–3600 s
│   - Redirect stdout to /tmp/logs/{task-name}.log
│
▼
[4. Agent: Poll for completion]
│   - Check /tmp/logs/{task-name}.log
│   - Check docs/results/{task-name}.result.md
│
▼
[5. Agent: Verify result]
│   - Re-run verification commands
│   - Check expected outcomes
│
├── PASS ──► Done ✅
│
└── FAIL ──► [6. Append Retry Notes to task file]
                 │
                 ▼
           [7. Re-launch qwen via Shell]
                 │
                 ├── PASS ──► Done ✅
                 └── FAIL ──► Escalate to user ❌
```

---

## Step 1 — Analyze & Plan

Before writing anything:

1. **Scope:** Identify affected modules (`backend`, `starter`, `client`, `frontend`, `frontend-plugin`).
2. **Hexagonal structure:** Determine new packages, ports, adapters, routers, plugins.
3. **DB / API changes:** Migrations, OpenAPI spec changes, generated clients.
4. **Verification:** Pick the exact commands that prove correctness (tests, lint, build).
5. **Task name:** Choose `kebab-case`, 3–6 words, descriptive (e.g. `add-auth-middleware`).

---

## Step 2 — Write Task File

**Path:** `docs/tasks/{task-name}.md`

Use this exact template:

````markdown
# Task: {task-name}

## Description

<!-- One paragraph: what and why -->

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

- `.agents/` — never modify
- `.claude/` — never modify
- `.kiro/` — never modify
- `.qwen/` — never modify
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
- DI via piqure: `provide` and `inject` must come from the same `piqureWrapper` instance
- HTTP adapter uses `AxiosHttp` + Bearer token from `TokenStorage.get()`
- Biome 1.9.4: 2-space indent, single quotes, semicolons, line width 140
- New feature checklist (7 steps): domain type → port → provider → HTTP adapter → page → router → Nuxt plugin

## Verification

Run ALL applicable commands. Results must be included in the result file.

### Backend

```bash
./gradlew spotlessApply
./gradlew :backend:test
./gradlew :backend:integrationTest   # only if Docker is running
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

### Infrastructure

```bash
docker compose config
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

## Step 3 — Launch Qwen via Shell

Use the **Shell** tool with the following exact pattern.

```bash
mkdir -p /tmp/logs && source ~/.nvm/nvm.sh && nvm use v22.20.0 --silent && qwen -y \
  'Read the file docs/tasks/{task-name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/{task-name}.result.md as instructed.' \
  </dev/null 2>&1 | tee /tmp/logs/{task-name}.log
```

**Tool parameters:**
- `command`: the bash line above (replace `{task-name}`)
- `run_in_background`: `true`
- `timeout`: `1800` for normal tasks, `3600` for large refactors
- `description`: short label, e.g. `qwen-{task-name}`

**Why `</dev/null`?**
Qwen may wait for stdin if not explicitly closed. Redirecting from `/dev/null` forces one-shot (non-interactive) behaviour.

**Why `tee`?**
Live output is saved to `/tmp/logs/{task-name}.log` so you can inspect progress without waiting for the background-task notification.

---

## Step 4 — Poll for Completion

After launching, the Shell tool returns a `task_id`.

Wait for the automatic completion notification, **or** poll manually:

```bash
# Read the live log
cat /tmp/logs/{task-name}.log

# Check if the result file already exists
cat docs/results/{task-name}.result.md 2>/dev/null || echo "NOT_YET"
```

When done:
1. Verify `docs/results/{task-name}.result.md` exists.
2. Parse its **Status** section (DONE | PARTIAL | FAILED).
3. Review **Files Changed** and **Self-Assessment**.

---

## Step 5 — Independent Verification

Re-run the verification commands from the task file yourself. Do not trust the result file blindly.

Typical commands per module:

| Module          | Command                                                                                           |
|-----------------|---------------------------------------------------------------------------------------------------|
| Backend         | `./gradlew spotlessApply && ./gradlew :backend:test && ./gradlew check`                           |
| Starter         | `./gradlew :starter:test && ./gradlew :starter:check`                                             |
| Client          | `./gradlew :backend:exportOpenApi && ./gradlew generateAllClients`                                |
| Frontend        | `source ~/.nvm/nvm.sh && nvm use v22.20.0 && cd frontend && pnpm lint && pnpm test && pnpm build` |
| Frontend-Plugin | `source ~/.nvm/nvm.sh && nvm use v22.20.0 && cd frontend-plugin && pnpm lint && pnpm test`        |

Checklist:
- [ ] All commands exit code 0
- [ ] All "Files to Create" exist on disk
- [ ] No hardcoded secrets
- [ ] `frontend-plugin` has no imports from `frontend/`

---

## Step 6 — Retry Logic

- Maximum **2 total runs** (1 retry allowed).
- Before retrying, append a `## Retry Notes` section to `docs/tasks/{task-name}.md`:

```markdown
## Retry Notes (Attempt 2)

The following issues were found in attempt 1:

- Test `AuthServiceTest` failed because `JwtService` was not injected
- Lint error in `middleware.ts` line 42: missing semicolon

Please fix these specifically before re-running verification.
```

- Re-launch qwen via Shell with the same command. Qwen will read the updated task file.

---

## Step 7 — Escalation

If the task still fails after retry:

1. Do not attempt further retries.
2. Report to the user with:
   - Summary of what was tried
   - Exact failure reason from the result file
   - Suggestion for manual intervention

---

## Naming Conventions

| Artifact  | Path                                 |
|-----------|--------------------------------------|
| Task spec | `docs/tasks/{task-name}.md`          |
| Result    | `docs/results/{task-name}.result.md` |
| Live log  | `/tmp/logs/{task-name}.log`          |

`task-name` rules:
- `kebab-case`
- Descriptive but concise (3–6 words max)
- Examples: `add-auth-middleware`, `refactor-user-service`, `fix-pagination-bug`

---

## Quick Reference

**Before invoking Shell:**
- [ ] Task file written to `docs/tasks/{task-name}.md`
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Module scope checkboxes marked
- [ ] Verification section has runnable commands
- [ ] Result file path is stated in task file

**After background task completes:**
- [ ] Result file exists at `docs/results/{task-name}.result.md`
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] CBS-Nova conventions verified
- [ ] PASS/FAIL decision made before considering retry
