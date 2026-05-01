---
name: dev-workflow
description: Orchestration skill that coordinates planner, tester, and code-reviewer agents through a 4-phase development workflow with strict abort conditions.
---

<workflow>
This skill orchestrates development across 4 sequential phases. It coordinates the planner, tester, and code-reviewer agents. User approval is required between critical phases.

**CBS-Nova Project Context:**

- Stack: Java 25 · Spring Boot · Temporal · PostgreSQL · Java dsl scripts · Vue 3 admin UI · Nuxt 3 SPA · Tailwind
  CSS v4 · piqure DI · i18next · Biome · Gradle multi-module
- Modules: backend · starter · client · frontend · frontend-plugin
- Critical: Hexagonal architecture, frontend-plugin boundary, piqure DI patterns, JWT security (local RSA + Keycloak)

PHASE 1 — PLAN

- Invoke the `planner` agent.
- Wait for the plan file to appear in `./docs/plans/<feature-name>.md`.
- Present the plan to the user and request explicit approval.
- DO NOT proceed to Phase 2 without explicit user confirmation.

PHASE 2 — IMPLEMENT

- Execute the approved plan ONE STEP AT A TIME.
- After each step: run tests using the multi-step test sequence:
    1. `./gradlew :backend:test`
    2. `./gradlew :backend:integrationTest`
    3. `cd frontend && pnpm test`
    4. `cd frontend && pnpm e2e` (if E2E tests exist)
- On test failure: STOP immediately, report the failure, DO NOT continue.
- Create a git checkpoint before starting each medium/large step:
  `git add -A && git commit -m "checkpoint: before <step description>"`

PHASE 3 — REVIEW

- Invoke the `code-reviewer` agent on all changed files.
- If verdict is NEEDS WORK: fix ALL CRITICAL and HIGH findings, then re-invoke reviewer.
- DO NOT proceed to Phase 4 if verdict remains NEEDS WORK.

PHASE 4 — TEST & COMMIT

- Invoke the `tester` agent to run the full suite and write any missing tests.
- Only if tester reports ALL GREEN: create the final commit.
- Commit message format: `<type>(<scope>): <short description>`
  Types: feat / fix / refactor / test / docs / chore
  Scopes: backend / frontend / frontend-plugin / starter / client / gradle
  </workflow>

<abort_conditions>
STOP the workflow and ask the user for guidance if:

- Any CRITICAL security finding is detected by the reviewer.
- More than 3 consecutive test failures occur on the same step.
- The planner identifies a file marked "MUST NOT be modified" as required for the task.
- Frontend-plugin boundary violations are detected.
- Hexagonal architecture layer violations occur.
- Gradle module dependency conflicts arise.
  </abort_conditions>

<usage_examples>
> Use the dev-workflow skill to implement user role management with CRUD operations
> Use the dev-workflow skill to add two-factor authentication to the login flow
> Use the dev-workflow skill to create a settings management page with form validation
> Use the dev-workflow skill to add REST API endpoints for business rule management
> Use the dev-workflow skill to add audit logging tables and corresponding entities
</usage_examples>

<phase_coordination>
After each phase, provide a status update:

## Phase X Complete - [Status]

Summary: [Brief description of what was accomplished]
Files modified: [List of files]
Tests run: [Test results]
Next: [What happens next]
</phase_coordination>

<multi_module_coordination>

- Changes often span multiple Gradle modules
- Coordinate dependencies between backend, starter, client, frontend, frontend-plugin
- Respect module boundaries and dependency direction
- Backend tests require Testcontainers with PostgreSQL
- Frontend tests require proper Nuxt 3 test setup
- Integration tests must verify cross-module interactions
  </multi_module_coordination>

<error_handling>
When encountering issues:

1. Stop the current phase immediately
2. Analyze the problem and provide clear explanation
3. Propose solutions or alternatives
4. Wait for user guidance before proceeding
5. Document the issue for future reference
   </error_handling>
