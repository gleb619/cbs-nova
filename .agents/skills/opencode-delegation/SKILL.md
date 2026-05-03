---
name: opencode-delegation
description: >
  Delegate implementation tasks to OpenCode via MCP tool. The agent acts as
  orchestrator: analyzes requirements, writes a task spec, invokes the opencode_run
  MCP tool, then verifies the result. Use for any backend/frontend/build/infra
  implementation task.
---

# Skill: OpenCode Delegation via MCP

## Overview

This skill delegates implementation tasks to OpenCode through the `opencode_run` MCP tool.

**MCP Tool:** `opencode_run`  
**Parameters:**
- `task_name` (required): Task identifier (kebab-case, without .md extension)
- `timeout_sec` (optional): Timeout in seconds (default: 300)
- `args` (optional): Additional CLI arguments (e.g., `['-c']` to continue session)
- `prompt` (optional): Override prompt (rarely needed)

---

## Workflow

```
[User describes feature/task]
│
▼
[1. Agent: Analyze & Plan]
│   - Identify affected modules
│   - Determine verification commands
│   - Choose task-name (kebab-case)
│
▼
[2. Agent: Write docs/tasks/{task-name}.md]
│
▼
[3. Agent: Call MCP tool opencode_run]
│   Tool: opencode_run
│   Args: { task_name: "{task-name}", timeout_sec: 300 }
│
▼
[4. OpenCode: Reads task, implements, writes result file]
│   Output: docs/results/{task-name}.result.md
│
▼
[5. Agent: Verify result]
│   - Check result file exists
│   - Re-run verification commands
│   - Validate conventions
│
├── PASS ──► Done ✅
│
└── FAIL ──► [6. Agent: Add Retry Notes to task file]
                 │
                 ▼
           [7. Re-invoke opencode_run with same task_name]
                 │
                 ├── PASS ──► Done ✅
                 └── FAIL ──► Escalate to user ❌
```

---

## Step 1 — Analyze & Plan

Before writing the task file:

- Understand the full scope of the request
- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Identify files to create/modify following the hexagonal structure
- Determine new packages, DB migrations, or OpenAPI changes needed
- Decide on verification methods (tests, lint, build)
- Choose `task-name` in `kebab-case` (e.g., `add-auth-middleware`)

---

## Step 2 — Write Task File

**Path:** `docs/tasks/{task-name}.md`

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

## Step 3 — Invoke MCP Tool

After writing the task file, invoke the `opencode_run` MCP tool:

**Tool:** `opencode_run`  
**Parameters:**
```json
{
  "task_name": "{task-name}",
  "timeout_sec": 300
}
```

The tool will:
1. Read `docs/tasks/{task-name}.md`
2. Execute OpenCode with the task instructions
3. Wait for completion
4. Return the result output

**Default prompt behavior:** The tool automatically constructs the prompt:
> "Read the file docs/tasks/{task-name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/{task-name}.result.md as instructed."

---

## Step 4 — Verify Result

After the MCP tool returns:

1. **Check result file exists:** `docs/results/{task-name}.result.md`
2. **Parse the result file:**
   - Read the **Status** section (DONE | PARTIAL | FAILED)
   - Read the **Self-Assessment** section
   - Review **Files Changed** table
3. **Re-run verification commands independently** to confirm
4. **Check expected outcomes** from task file:
   - All verification commands exit 0
   - All "Files to Create" exist on disk
   - No `frontend-plugin` → `frontend` imports
   - Naming conventions followed
5. **Make PASS/FAIL decision**

---

## Step 5 — Retry Logic

- Maximum **2 total runs** (1 retry allowed)
- Before retrying:
  - Append `## Retry Notes` section to `docs/tasks/{task-name}.md`
  - Describe exactly what failed and what to fix

```markdown
## Retry Notes (Attempt 2)

The following issues were found in attempt 1:

- Test `auth.test.ts` failed because `JwtService` was not injected
- Lint error in `middleware.ts` line 42: missing semicolon

Please fix these specifically before re-running verification.
```

- Re-invoke `opencode_run` with the same `task_name` — OpenCode will read the updated task file

---

## Step 6 — Escalation

If the task still fails after retry #1:

1. **Do not attempt further retries**
2. Report to user with:
   - Summary of what was tried
   - Exact failure reason from result file
   - Suggestion for manual intervention

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

## Quick Reference

**Before invoking MCP tool:**
- [ ] Task file written to `docs/tasks/{task-name}.md`
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Module scope checkboxes marked
- [ ] Verification section has runnable commands
- [ ] Result file path is stated in task file

**After MCP tool returns:**
- [ ] Result file exists at `docs/results/{task-name}.result.md`
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] CBS-Nova conventions verified
- [ ] PASS/FAIL decision made before considering retry
