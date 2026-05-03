---
description: Orchestration workflow that coordinates planner, tester, and code-reviewer agents through a 4-phase development flow with strict abort conditions.
---

## Overview

This workflow orchestrates development across 4 sequential phases. It coordinates the `planner`, `tester`, and
`code-reviewer` agents. User approval is required between critical phases.

**CBS-Nova Project Context:**

- Stack: Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt 3 SPA · Tailwind
  CSS v4 · piqure DI · i18next · Biome · Gradle multi-module
- Modules: `backend` · `starter` · `client` · `frontend` · `frontend-plugin`
- Critical: Hexagonal architecture, frontend-plugin boundary, piqure DI patterns, JWT security (local RSA + Keycloak)

## Workflow

### Phase 1 — Plan

- Invoke the `planner` agent (`.agents/agents/planner/AGENT.md`).
- Wait for the plan file to appear in `./docs/tasks/<feature-name>.md`.
- Present the plan to the user and request explicit approval.
- **DO NOT proceed to Phase 2 without explicit user confirmation.**

### Phase 2 — Implement

- Execute the approved plan **ONE STEP AT A TIME**.
- After each step, run tests using the multi-step test sequence:
    1. `./gradlew :backend:test`
    2. `./gradlew :backend:integrationTest`
    3. `cd frontend && pnpm test`
    4. `cd frontend && pnpm e2e` (if E2E tests exist)
- On test failure: **STOP immediately**, report the failure, DO NOT continue.
- Create a git checkpoint before starting each medium/large step:
  ```bash
  git add -A && git commit -m "checkpoint: before <step description>"
  ```

### Phase 3 — Review

- Invoke the `code-reviewer` agent (`.agents/agents/code-reviewer/AGENT.md`) on all changed files.
- If verdict is **NEEDS WORK**: fix ALL CRITICAL and HIGH findings, then re-invoke the reviewer.
- **DO NOT proceed to Phase 4 if verdict remains NEEDS WORK.**

### Phase 4 — Test & Commit

- Invoke the `tester` agent (`.agents/agents/tester/AGENT.md`) to run the full suite and write any missing tests.
- Only if tester reports **ALL GREEN**: create the final commit.
- Commit message format: `<type>(<scope>): <short description>`
    - Types: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`
    - Scopes: `backend` / `frontend` / `frontend-plugin` / `starter` / `client` / `gradle`

## Abort Conditions

STOP the workflow and ask the user for guidance if:

- Any CRITICAL security finding is detected by the reviewer.
- More than 3 consecutive test failures occur on the same step.
- The planner identifies a file marked "MUST NOT be modified" as required for the task.
- Frontend-plugin boundary violations are detected.
- Hexagonal architecture layer violations occur.
- Gradle module dependency conflicts arise.

## Phase Status Updates

After each phase, provide a status update using this format:

```
## Phase X Complete — [Status]

Summary: [Brief description of what was accomplished]
Files modified: [List of files]
Tests run: [Test results]
Next: [What happens next]
```

## Multi-Module Coordination

- Changes often span multiple Gradle modules.
- Coordinate dependencies between `backend`, `starter`, `client`, `frontend`, `frontend-plugin`.
- Respect module boundaries and dependency direction.
- Backend tests require Testcontainers with PostgreSQL.
- Frontend tests require proper Nuxt 3 test setup.
- Integration tests must verify cross-module interactions.

## Error Handling

When encountering issues:

1. Stop the current phase immediately.
2. Analyze the problem and provide a clear explanation.
3. Propose solutions or alternatives.
4. Wait for user guidance before proceeding.
5. Document the issue for future reference.

## Usage Examples

> Use the dev-workflow skill to implement user role management with CRUD operations
> Use the dev-workflow skill to add two-factor authentication to the login flow
> Use the dev-workflow skill to create a settings management page with form validation
> Use the dev-workflow skill to add REST API endpoints for business rule management
> Use the dev-workflow skill to add audit logging tables and corresponding entities
