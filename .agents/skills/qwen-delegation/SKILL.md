---
name: qwen-delegation
description: >
  Use when delegating implementation work to Qwen as an executor agent within
  Google Antigravity IDE. Gemini plans, decomposes, and writes a task spec;
  Qwen executes it (backend and/or frontend); Gemini verifies the result.
  Triggers when a task is well-scoped and can be handed off for implementation,
  including Gradle backend tasks, Nuxt/Vue frontend tasks, or full-stack features.
---

# Skill: Orchestrated Task Delegation to Qwen (Antigravity / Gradle + Nuxt)

## Overview

**Gemini** acts as the **thinking center**: it analyzes requirements, plans
the implementation across modules, writes an unambiguous task specification,
delegates execution to Qwen, then verifies the result.

**Qwen** acts as the **executor**: it reads the task file, performs all
implementation work (backend, frontend, or both), runs verification commands,
and writes a result file.

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
├── docs/
│   ├── tasks/        # ← Gemini writes task specs here
│   └── results/      # ← Qwen writes result files here
└── docker/           # Keycloak, PostgreSQL compose files
```

**Version catalog:** `gradle/libs.versions.toml`
**Frontend package manager:** `pnpm` (workspace-aware)
**Frontend linter/formatter:** Biome (`biome.json` per module)
**Frontend test runner:** Vitest (`vitest.config.ts` per module)
**Node version manager:** nvm — always activate before running node/pnpm commands

---

## Roles

| Agent    | Role         | Responsibility                                             |
|----------|--------------|------------------------------------------------------------|
| `gemini` | Orchestrator | Analyze, plan, write task spec, invoke Qwen, verify result |
| `qwen`   | Executor     | Read task file, implement, run verification, write result  |

---

## Workflow

```
[User / Mission Control Request]
      │
      ▼
[1. Gemini: Analyze & Plan]
      │
      ▼
[2. Gemini: Write docs/tasks/{task-name}.md]
      │
      ▼
[3. Gemini: Invoke Qwen via CLI]
      │   source ~/.nvm/nvm.sh && nvm use v22.20.0 && \
      │   qwen -y "Read docs/tasks/{task-name}.md and follow all instructions
      │            inside. Write your result to docs/results/{task-name}.result.md"
      │            --output-format text
      ▼
[4. Qwen: Execute task, write result file]
      │
      ▼
[5. Gemini: Verify result file + re-run verification commands]
      │
      ├── PASS ──► Done ✅
      │
      └── FAIL ──► [6. Gemini: Amend task file with Retry Notes, re-invoke Qwen]
                         │
                         ▼
                   [7. Gemini: Verify again]
                         │
                         ├── PASS ──► Done ✅
                         └── FAIL ──► Escalate to user ❌
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Gemini must:

- Understand scope: backend-only / frontend-only / full-stack
- Identify affected modules: `backend`, `client`, `starter`, `frontend`, `frontend-plugin`
- Note relevant existing files, patterns, and constraints from `docs/` and `AGENTS.md`
- Decide on verification strategy per module (see Step 2 → Verification)
- Choose a `task-name` in `kebab-case` (e.g., `add-user-profile-api`, `implement-login-page`)

---

## Step 2 — Task File Format

**File path:** `docs/tasks/{task-name}.md`

````markdown
# Task: {task-name}

## Description
<!-- One paragraph: what this task is and why it's needed -->

## Context
<!-- Relevant architecture notes, existing patterns, constraints.
     Reference specific files in the project where helpful. -->

## Module Scope
<!-- Which modules are involved -->
- [ ] backend
- [ ] client
- [ ] starter
- [ ] frontend
- [ ] frontend-plugin

## Requirements
<!-- Numbered, concrete, unambiguous. Split by module if needed. -->

### Backend (if applicable)
1. ...
2. ...

### Frontend (if applicable)
1. ...
2. ...

## Scope

### Files to Create
- `backend/src/main/java/...`
- `frontend/src/...`

### Files to Modify
- `backend/src/main/java/...` — reason
- `frontend/src/...` — reason

### Files to Leave Untouched
- `docker/` — infrastructure, do not modify
- `gradle/libs.versions.toml` — only modify if a new dependency is strictly required

## Implementation Notes
<!-- Patterns to follow, libraries already in the version catalog,
     Biome formatting rules, Nuxt conventions, Spring conventions, etc. -->

## Verification

> Qwen must run ALL commands below after finishing work.
> Full stdout/stderr and exit codes must be included in the result file.

### Environment Setup (run once before anything else)
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
```

### Backend Verification (if backend was modified)
```bash
./gradlew :backend:test
./gradlew :backend:checkstyleMain
./gradlew :backend:build -x test
```

### Frontend Verification (if frontend was modified)
```bash
cd frontend && pnpm run lint
cd frontend && pnpm run test:run
cd frontend && pnpm run build
```

### Frontend-Plugin Verification (if frontend-plugin was modified)
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
- Files listed under "Files to Create" exist
- No `console.log` left in frontend production code
- No raw SQL or hardcoded secrets introduced

## Result File
After completing all work, write your result to:
`docs/results/{task-name}.result.md`

Follow the result file format exactly as specified.