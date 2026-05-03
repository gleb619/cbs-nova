---
name: qwen-delegation
description: >
  Use when delegating implementation work to Qwen as an executor agent within
  Kiro IDE. Kiro plans, decomposes, and writes a task specification; Qwen
  executes it (backend and/or frontend); Kiro verifies the result.
  Activates when a task is well-scoped and ready for autonomous handoff,
  including Gradle backend tasks, Nuxt/Vue frontend tasks, or full-stack
  features. Triggered automatically by the delegate-on-task-save hook when
  a file is saved to docs/tasks/*.md; manual invocation is the fallback.
---

# Skill: Orchestrated Task Delegation to Qwen (Kiro / Gradle + Nuxt)

## Overview

**Kiro** acts as the **thinking center**: it analyzes requirements using its
full codebase context, plans the implementation across modules, writes an
unambiguous task specification, delegates execution to Qwen via CLI, then
verifies the result — potentially creating a pull request on success.

**Qwen** acts as the **executor**: it reads the task file, performs all
implementation work (backend, frontend, or both), runs verification commands,
and writes a result file.

This skill is designed to run **asynchronously** inside Kiro's sandbox
environment. Kiro does not need to supervise Qwen line-by-line — it
delegates, waits for the result file, then verifies.

---

## Project Layout Reference

```
root/
├── backend/          # Java/Gradle — Spring Boot backend
├── client/           # Java/Gradle — client module
├── starter/          # Java/Gradle — starter module
├── frontend/         # Nuxt 3 + Vue — main frontend app
│   ├── src/
│   ├── nuxt.config.ts
│   ├── vitest.config.ts
│   └── biome.json
├── frontend-plugin/  # Nuxt layer / shared plugin module
│   ├── composables/
│   ├── plugins/
│   └── infrastructure/
├── gradle/           # Shared Gradle config, version catalog
│   └── libs.versions.toml
├── docs/
│   ├── tasks/        # ← Kiro writes task specs here
│   └── results/      # ← Qwen writes result files here
└── docker/           # Keycloak, PostgreSQL compose files
```

**Version catalog:** `gradle/libs.versions.toml`
**Frontend package manager:** `pnpm` (workspace-aware)
**Frontend linter/formatter:** Biome (`biome.json` per module)
**Frontend test runner:** Vitest (`vitest.config.ts` per module)
**Node version manager:** nvm — always activate before any node/pnpm commands

---

## Roles

| Agent  | Role         | Responsibility                                                 |
|--------|--------------|----------------------------------------------------------------|
| `kiro` | Orchestrator | Analyze, plan, write task spec, invoke Qwen, verify, PR/report |
| `qwen` | Executor     | Read task file, implement, run verification, write result file |

---

## Hook Integration

This skill is triggered automatically by the **`delegate-on-task-save`** hook whenever a file is saved under
`docs/tasks/*.md`. This is the **primary and expected way** to activate the workflow — manual invocation is the
fallback.

**Hook:** `.kiro/hooks/delegate-on-task-save.json`

- Event: `fileEdited` on `docs/tasks/*.md`
- Action: `askAgent` — Kiro activates this skill, completes Step 1, then runs the full workflow

> Kiro must still complete Step 1 (plan review) before proceeding to invocation.
> Auto-delegation without planning is not permitted.

---

## Workflow

```
[User Prompt / Hook Trigger / Mission Task]
      │
      ▼
[1. Kiro: Analyze codebase context & plan]
      │
      ▼
[2. Kiro: Write docs/tasks/{task-name}.md]
      │
      ▼
[3. Kiro: Invoke Qwen via Kiro CLI]
      │   source ~/.nvm/nvm.sh && nvm use v22.20.0 && \
      │   qwen -y "Read docs/tasks/{task-name}.md and follow all instructions
      │            inside. Write your result to docs/results/{task-name}.result.md"
      │            --output-format text
      ▼
[4. Qwen: Execute task, write result file]  ← runs asynchronously
      │
      ▼
[5. Kiro: Verify result file + re-run verification commands]
      │
      ├── PASS ──► [6a. Kiro: Optionally open PR] ──► Done ✅
      │
      └── FAIL ──► [6b. Kiro: Amend task file with Retry Notes, re-invoke Qwen]
                         │
                         ▼
                   [7. Kiro: Verify again]
                         │
                         ├── PASS ──► [Optionally open PR] ──► Done ✅
                         └── FAIL ──► Escalate to user ❌ (max retries reached)
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Kiro must leverage its **full codebase context**:

- Load and review `AGENTS.md` for project-level conventions
- Check `docs/` for prior feature specs and architecture notes
- Identify affected modules: `backend`, `client`, `starter`, `frontend`, `frontend-plugin`
- Identify affected files and likely change surface
- Determine if new Gradle dependencies are needed (`gradle/libs.versions.toml`)
- Determine if new pnpm packages are needed (`frontend/package.json`)
- Choose verification strategy per module (see Step 2 → Verification)
- Choose a `task-name` in `kebab-case` (e.g., `add-user-profile-api`, `implement-login-page`)

> Kiro must **not** start writing the task file until this analysis is complete.
> Use codebase search and file reads — do not assume structure.

---

## Step 2 — Task File Format

**File path:** `docs/tasks/{task-name}.md`

````markdown
# Task: {task-name}

## Description
<!-- One paragraph: what this task is and why it is needed -->

## Context
<!-- Relevant architecture notes, existing patterns, constraints.
     Reference specific files in the project where helpful.
     Kiro: populate this from actual codebase analysis. -->

## Module Scope
<!-- Check all modules involved in this task -->
- [ ] backend
- [ ] client
- [ ] starter
- [ ] frontend
- [ ] frontend-plugin

## Requirements

<!-- Numbered, concrete, unambiguous. Split by module. -->

### Backend (if applicable)
1. ...
2. ...

### Frontend (if applicable)
1. ...
2. ...

## Scope

### Files to Create
- `backend/src/main/java/...`
- `frontend/src/components/...`

### Files to Modify
- `backend/src/main/java/...` — reason
- `frontend/src/composables/...` — reason

### Files to Leave Untouched
- `docker/` — infrastructure, do not modify
- `gradle/libs.versions.toml` — only modify if a new dependency is strictly required
- `.kiro/` — Kiro configuration, never modify

## Implementation Notes
<!-- Patterns to follow, libraries already in version catalog,
     Biome formatting rules, Nuxt/Spring conventions,
     existing composables or services to reuse -->

## Verification

> Qwen must run ALL applicable commands after finishing work.
> Full stdout/stderr and exit codes must appear in the result file.

### Environment Setup (always run first)
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
```

### Backend Verification (if backend/client/starter modified)
```bash
./gradlew :backend:test
./gradlew :backend:checkstyleMain
./gradlew :backend:build -x test
```

### Frontend Verification (if frontend modified)
```bash
cd frontend && pnpm run lint
cd frontend && pnpm run test:run
cd frontend && pnpm run build
```

### Frontend-Plugin Verification (if frontend-plugin modified)
```bash
cd frontend-plugin && pnpm run lint
cd frontend-plugin && pnpm run test:run
```

### Full Build (always run last)
```bash
./gradlew build -x test
```

### Expected Outcomes
- All commands exit with code 0
- All files listed under "Files to Create" exist on disk
- No `console.log` left in frontend production code
- No hardcoded secrets or raw SQL introduced
- Biome reports zero errors in all touched frontend files

## Result File
After completing all work, write your result to:
`docs/results/{task-name}.result.md`

Follow the result file format exactly as specified in the skill.