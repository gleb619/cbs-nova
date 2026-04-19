---
name: opencode-delegation
description: >
  The agent acts as thinking center: analyzes requirements, writes a task spec,
  then delegates execution to a secondary agent (e.g. OpenCode). The secondary
  agent reads the task file, performs the work, and writes a result file. The
  orchestrating agent verifies the result. Use for any backend/frontend/build/infra
  implementation task where a secondary agent is the executor.
---

# Skill: Orchestrated Task Delegation

## Roles

| Actor          | Role         | Responsibility                                            |
|----------------|--------------|-----------------------------------------------------------|
| Agent          | Orchestrator | Analyze, plan, write task spec, invoke executor, verify   |
| Executor Agent | Executor     | Read task file, implement, run verification, write result |

---

## Workflow

```
[User describes feature/task]
│
▼
[1. Agent: Analyze & plan]
│
▼
[2. Agent: Write docs/tasks/{task-name}.md]
│
▼
[3. Agent: Delegate to executor agent]
│   Instruct executor to read docs/tasks/{task-name}.md,
│   follow all instructions, and write result to
│   docs/results/{task-name}.result.md
▼
[4. Executor: Implement task, run verification, write result file]
│
▼
[5. Agent: Verify result file + re-run verification commands]
│
├── PASS ──► Done ✅
│
└── FAIL ──► [6. Agent: Append Retry Notes to task file, re-delegate]
                 │
                 ▼
           [7. Agent: Verify again]
                 │
                 ├── PASS ──► Done ✅
                 └── FAIL ──► Escalate to user ❌ (max retries reached)
```

---

## Step 1 — Analyze & Plan

Before writing the task file, the agent must:

- Understand the full scope of the request
- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Identify files to create/modify following the hexagonal structure
- Determine new packages, DB migrations, or OpenAPI changes needed
- Decide on the exact verification method (tests, lint, build, grep)
- Choose a `task-name` in `kebab-case` (e.g., `add-auth-middleware`)

**Do not write the task file until analysis is complete.**

---

## Step 2 — Task File

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
- DI via piqure: `provide` and `inject` must come from the **same** `piqureWrapper` instance
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

## Step 3 — Delegate to Executor

After writing the task file, the agent delegates execution to the secondary agent.

The delegation prompt must include:

> Read the file `docs/tasks/{task-name}.md` carefully and follow all instructions
> inside it exactly. After completing all work, write your result summary to
> `docs/results/{task-name}.result.md` as instructed.

The exact invocation mechanism depends on the executor agent's tooling (MCP tool,
CLI command, or manual handoff). The orchestrating agent must adapt accordingly.

---

## Step 4 — Result File Format (Executor must produce this)

File path: `docs/results/{task-name}.result.md`

````markdown
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

### Command: `pnpm test`

exit code: 0

```
<stdout here>
```

## Issues Encountered

<!-- Any blockers, assumptions made, deviations from spec -->

## Self-Assessment

<!-- PASS or FAIL, and why -->
````

---

## Step 5 — Verification by Agent

After the executor finishes, the agent must:

1. **Check the result file exists** at `docs/results/{task-name}.result.md`
2. **Read the Self-Assessment** section — note if executor flagged issues
3. **Re-run verification commands independently** to confirm exit codes
4. **Check expected outcomes** from the task file one by one
5. **Check CBS-Nova conventions:**
    - All verification commands exited 0
    - All listed "Files to Create" confirmed present
    - Naming/layer conventions followed (ArchUnit, Biome, Checkstyle)
    - DB migration file named correctly (`V{timestamp}__...sql`)
    - No `frontend-plugin` → `frontend` imports
6. **Make a final PASS/FAIL decision**

---

## Step 6 — Retry Logic

- Maximum **2 total runs** (1 retry allowed)
- Before retrying, the agent **must amend the task file**:
    - Append a `## Retry Notes` section at the bottom
    - Describe exactly what failed and what to do differently

```markdown
## Retry Notes (Attempt 2)

The following issues were found in attempt 1:

- Test `auth.test.ts` failed because `JwtService` was not injected
- Lint error in `middleware.ts` line 42: missing semicolon

Please fix these specifically before re-running verification.
```

- Then re-delegate with an updated prompt referencing the Retry Notes section.

---

## Step 7 — Escalation

If the task still fails after retry #1, the agent must:

1. **Not attempt further retries**
2. Report to the user with:
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

## Quick Reference Checklist (Agent internal)

Before delegating to executor:

- [ ] Task file written to correct path
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Module scope checkboxes marked
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path is stated inside the task file

After executor finishes:

- [ ] Result file exists
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] CBS-Nova conventions verified
- [ ] PASS/FAIL decision made before considering retry
