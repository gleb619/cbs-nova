---
name: executor-delegation
description: >
  Claude acts as the thinking center: analyzes requirements, writes a task spec
  to docs/tasks/{task-name}.md, then waits. The user executes the task (manually,
  via another agent, or any other means) and reports back. Claude verifies the
  output and writes a result file. Use for any implementation task that needs
  to be delegated outside of Claude's direct execution scope.
---

# Skill: Claude-Planned / Externally-Executed Task Delegation

## Roles

| Actor  | Role         | Responsibility                                                   |
|--------|--------------|------------------------------------------------------------------|
| Claude | Orchestrator | Analyze, plan, write task spec, verify report, write result file |
| User   | Executor     | Read task file, implement, run verification, paste output back   |

---

## Workflow

```
[User describes feature/task]
│
▼
[1. Claude: Analyze & plan]
│
▼
[2. Claude: Write docs/tasks/{task-name}.md directly using write_to_file]
│
▼
[3. User: Implement task, run all verification commands, paste output]
│
▼
[4. Claude: Verify output — all commands exit 0, files exist, conventions met]
│
├── PASS ──► [5a. Claude: Write docs/results/{task-name}.result.md] ──► Done ✅
└── FAIL ──► [5b. Claude: Append Retry Notes to task file, ask user to re-run]
             └── FAIL again ──► Escalate / explain blockers ❌
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Claude must:

- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Read relevant existing files to understand patterns and constraints
- Identify files to create/modify following the hexagonal structure
- Determine new packages, DB migrations, or OpenAPI changes needed
- Choose specific verification commands per module (see Step 2 template)
- Pick a `kebab-case` task name (3–6 words, e.g. `add-user-profile-api`)

**Do not write the task file until analysis is complete.**

---

## Step 2 — Write Task File

Use the `write_to_file` tool to write `docs/tasks/{task-name}.md` directly.
Then tell the user: *"Task spec written to `docs/tasks/{task-name}.md`. Implement it and paste all verification output
back here."*

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
- Java 25 + Spring Boot 4.x
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

Run ALL applicable commands. Paste full stdout/stderr + exit codes.

### Environment Setup (run once first)
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
```

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
cd frontend && pnpm lint
cd frontend && pnpm test
cd frontend && pnpm build
```

### Frontend-Plugin
```bash
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
After completing all work, write your result to:
`docs/results/{task-name}.result.md`
````

---

## Step 3 — User Executes

Claude waits. The user must:

1. Implement everything described in the task file
2. Run all applicable verification commands
3. Paste the full output (stdout/stderr + exit codes) back into the chat

---

## Step 4 — Claude Verifies

Check each of the following:

1. All verification commands exited 0
2. All listed "Files to Create" confirmed present (Claude may use `grep_search` or `list_dir` to check)
3. No regressions reported
4. Naming/layer conventions followed (ArchUnit, Biome, Checkstyle output reviewed)
5. DB migration file named correctly (`V{timestamp}__...sql`)
6. No `frontend-plugin` → `frontend` imports

**On FAIL:** Append `## Retry Notes` to the task file explaining exactly what to fix.
Ask the user to re-run. Max 2 retries, then escalate with a clear explanation.

---

## Step 5 — Write Result File

On PASS, use `write_to_file` to write `docs/results/{task-name}.result.md`.
Tell the user: *"Result written to `docs/results/{task-name}.result.md`. Task complete ✅"*

````markdown
# Result: {task-name}

## Status
PASS

## Summary
<!-- One paragraph: what was done -->

## Files Changed
| File | Action |
|------|--------|
| `path/to/file` | created / modified / deleted |

## Verification Output

### {check name}
exit code: 0
```
{pasted output}
```

## Notes
<!-- Deviations, follow-up suggestions, next steps -->
````

---

## Naming Conventions

| Artifact  | Path                                 |
|-----------|--------------------------------------|
| Task spec | `docs/tasks/{task-name}.md`          |
| Result    | `docs/results/{task-name}.result.md` |

`task-name` rules:

- `kebab-case`
- Descriptive but concise (3–6 words)
- Examples: `add-auth-middleware`, `refactor-user-service`, `fix-pagination-bug`

---

## Quick Reference Checklist

**Before handing off to user:**

- [ ] Analysis complete — all affected files/modules identified
- [ ] Task file written to `docs/tasks/{task-name}.md`
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path stated inside the task file

**After user reports back:**

- [ ] All verification commands exited 0
- [ ] All "Files to Create" exist
- [ ] Naming/layer/import conventions checked
- [ ] PASS/FAIL decision made before considering retry
- [ ] Result file written to `docs/results/{task-name}.result.md`
