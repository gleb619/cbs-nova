---
name: executor-delegation
description: >
  Kiro acts as thinking center: analyzes requirements, writes a task spec, then
  waits. The user executes the task manually and reports back. Kiro verifies the
  report and writes the result file. Use for any backend/frontend/build/infra
  implementation task where the user is the executor.
---

# Skill: Kiro-Planned / Human-Executed Task Delegation

## Roles

| Actor | Role         | Responsibility                                              |
|-------|--------------|-------------------------------------------------------------|
| Kiro  | Orchestrator | Analyze, plan, write task spec, verify report, write result |
| User  | Executor     | Read task file, implement, run verification, report output  |

---

## Workflow

```
[User describes feature/task]
│
▼
[1. Kiro: Analyze & plan]
│
▼
[2. Kiro: Write docs/tasks/{task-name}.md — print it for user]
│
▼
[3. User: Implement task, run all verification commands, paste output]
│
▼
[4. Kiro: Verify output — all commands exit 0, files exist, conventions met]
│
├── PASS ──► [5a. Kiro: Write docs/results/{task-name}.result.md] ──► Done ✅
└── FAIL ──► [5b. Kiro: Append Retry Notes to task file, ask user to re-run]
             └── FAIL again ──► Escalate / explain blockers ❌
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Kiro must:

- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Identify files to create/modify following the hexagonal structure
- Determine new packages, DB migrations, or OpenAPI changes needed
- Choose verification commands per module (see Step 2 template)
- Pick a `kebab-case` task name

**Do not write the task file until analysis is complete.**

---

## Step 2 — Task File

Print the following filled-out file. Tell the user: *"Save this to `docs/tasks/{task-name}.md`, then implement it and
paste all verification output back here."*

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
- `.kiro/` — never modify
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

Run ALL applicable commands. Paste full stdout/stderr + exit codes.

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
````

---

## Step 3 — User Executes

Kiro waits. User must:

1. Implement everything described in the task file
2. Run all applicable verification commands
3. Paste the full output (stdout/stderr + exit codes) back into the chat

---

## Step 4 — Kiro Verifies

Check:

1. All verification commands exited 0
2. All listed "Files to Create" confirmed present
3. No regressions reported
4. Naming/layer conventions followed (ArchUnit, Biome, Checkstyle)
5. DB migration file named correctly (`V{timestamp}__...sql`)
6. No `frontend-plugin` → `frontend` imports

**FAIL:** Append `## Retry Notes` to the task file explaining what to fix. Ask user to re-run. Max 2 retries, then
escalate.

---

## Step 5 — Result File

On PASS, print the result file. Tell the user: *"Save this to `docs/results/{task-name}.result.md`."*

````markdown
# Result: {task-name}

## Status
PASS

## Summary
<!-- One paragraph: what was done -->

## Files Changed
- `path/to/file` — created / modified / deleted

## Verification Output

### {check name}
```
{pasted output}
exit code: 0
```

## Notes
<!-- Deviations, follow-up suggestions, next steps -->
````
