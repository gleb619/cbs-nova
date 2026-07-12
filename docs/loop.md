# Autonomous Development Loop

This file is the prompt for `/loop`. Each session handles one batch of tasks, keeps context small, then hands off to the
next session. The loop is the thinking center — it plans and verifies; `at` agents write the code.

The loop covers **both backend (Java / Temporal) and frontend (Vue / Nuxt) tasks**. Pick the next ready task regardless
of tier; only the build, test, and convention checks change between backend and frontend work.

## Required Skills

Invoke before any work:

- `caveman` — compressed communication throughout all sessions
- `codegraph` — codebase exploration; never grep when `codegraph_*` tools are available
- `delegate-with-at` — delegate code writing to subagents via the `at` CLI

## Key Files

| File                   | Purpose                                             |
|------------------------|-----------------------------------------------------|
| `docs/architecture.md` | Top-level system goal and tier overview               |
| `docs/architecture-backend.md` | Java / Temporal backend overview and roadmap          |
| `docs/architecture-ui.md` | Vue / Nuxt frontend overview and build commands       |
| `docs/dsl/*.md`        | DSL constructs, authoring rules, codegen, runtime   |
| `backend/AGENTS.md`    | Backend coding conventions, module map, build commands|
| `docs/kanban.md`       | Task board — source of truth for current loop state |
| `docs/plans/`          | Detailed plan files for each task                     |

## Entry Point — Determine Current State

Read `docs/kanban.md`. Map kanban state to loop state:

| Kanban condition                     | Go to state                                    |
|--------------------------------------|------------------------------------------------|
| Any task `In Progress`               | VERIFY (session interrupted — resume)          |
| Any task `Ready`                     | PLAN                                           |
| Only `Backlog` tasks present         | PLAN (promote lowest-ID task to `Ready` first) |
| All `Done` / `Blocked`, no `Backlog` | NEXT_BATCH                                     |

---

## State: SCAN

Run once per new batch before PLAN:

1. `codegraph_status` — confirm index is healthy.
2. `codegraph_context` — survey current codebase state.
3. Read `docs/architecture.md` and the relevant tier roadmap (`architecture-backend.md` or `architecture-ui.md`) —
   identify which phases are complete.
4. Transition to PLAN.

---

## State: PLAN

1. Pick next `Ready` task from kanban (lowest ID first). If none, promote lowest-ID `Backlog` task to `Ready`.
2. Use `codegraph_context` + one `codegraph_explore` call to understand affected code areas.
3. Determine task tier:
   - **Backend task** — code under `backend/` or `dsl-examples/`, build with Gradle.
   - **Frontend task** — code under `frontend/`, build with pnpm.
4. Write or update plan file at `docs/plans/<ID>-short-title.md`. Plan must include:
   - Goal and acceptance criteria
   - Tier (`backend` or `frontend`)
   - Files to create or modify (with full paths)
   - Build and test commands to verify success:
     - Backend: `./gradlew spotlessApply && ./gradlew build test` from `backend/`
     - Frontend: `pnpm install && pnpm --filter @cbs/admin-ui-plugin lint && pnpm --filter @cbs/admin-ui-plugin test` from `frontend/`
   - Relevant constraints from `backend/AGENTS.md` (backend) or `architecture-ui.md` / component library rules (frontend)
5. Update kanban: set task status to `In Progress`.
6. Transition to DELEGATE.

---

## State: DELEGATE

1. Create a git worktree for the task:
   ```bash
   git worktree add ../cbs-nova-<task-id> -b feat/<task-id>
   ```
2. Invoke `delegate-with-at` skill. Pass:
   - Plan file path: `docs/plans/<ID>-short-title.md`
   - Worktree path: `../cbs-nova-<task-id>`
   - Constraint: work entirely inside the worktree
   - Constraint: run tier-specific verification before committing:
     - Backend: `./gradlew spotlessApply && ./gradlew build test` in `backend/`
     - Frontend: `pnpm install && pnpm --filter @cbs/admin-ui-plugin lint && pnpm --filter @cbs/admin-ui-plugin test` in `frontend/`
   - Constraint: commit with message `feat(<task-id>): <short description>` — no `Co-Authored-By` line
3. Track attempt count (starts at 1).
4. Transition to VERIFY.

---

## State: VERIFY

1. Read `at` agent result and any output.
2. Run tier-specific verification in the worktree:
   - Backend: `./gradlew build test` in `backend/`
   - Frontend: `pnpm --filter @cbs/admin-ui-plugin lint && pnpm --filter @cbs/admin-ui-plugin test` in `frontend/`
3. **Pass** — merge worktree branch, remove worktree, update kanban:
   ```bash
   git merge feat/<task-id>
   git worktree remove ../cbs-nova-<task-id>
   git branch -d feat/<task-id>
   ```
   Set task status to `Done`. Transition to PLAN (pick next task).
4. **Fail, attempt < 2** — increment attempt count. Append failure context (error output, stack trace) to the plan file
   under a `## Retry Notes` section. Transition to DELEGATE.
5. **Fail, attempt = 2** — set task status to `Blocked`. Append failure summary to plan file. Remove worktree:
   ```bash
   git worktree remove --force ../cbs-nova-<task-id>
   git branch -d feat/<task-id>
   ```
   Transition to PLAN (next task). Do not stop the loop.

---

## State: CLEANUP

Triggered when all tasks in the current batch are `Done` or `Blocked`:

1. Remove all `Done` task rows from the kanban table. Keep `Blocked` rows for human review.
2. Commit the cleaned kanban:
   ```
   feat(kanban): clean completed tasks from batch
   ```
3. Transition to NEXT_BATCH.

---

## State: NEXT_BATCH

1. Read `docs/architecture.md` and both roadmaps (`architecture-backend.md` and `architecture-ui.md`).
2. Use `codegraph_context` to confirm what is implemented vs. pending in both tiers.
3. Identify the next logical implementation phase across backend **and** frontend roadmaps. Prefer whichever tier has
   the lowest-risk, highest-value next step; do not let one tier starve the other.
4. Decompose into 3–5 concrete, independently executable tasks. For each task:
   - Add a row to kanban with status `Backlog`
   - Assign an ID continuing from the highest existing ID
   - Tag the tier (`backend` or `frontend`)
   - Create a stub plan file at `docs/plans/<ID>-short-title.md`
5. Commit kanban + stub plan files:
   ```
   feat(kanban): add next batch — <phase name>
   ```
6. Transition to SCAN (new session picks up from here).

---

## Hard Constraints

- **codegraph first** — use `codegraph_*` for all symbol, architecture, and impact questions. No grep loops.
- **caveman throughout** — drop articles, filler, pleasantries. Fragments OK. Keep responses compressed.
- **Never edit generated classes** — fix the generator (`dsl-codegen/`) or the DSL source, not the output.
- **No Co-Authored-By** — every commit is by the human git account configured in the repo.
- **Commit pattern** — `feat(<task-id>): short description` exactly. Example: `feat(T4): add ProcessRegistry`.
- **Style gate**
  - Backend: always run `./gradlew spotlessApply` before committing; `spotlessCheck` must pass.
  - Frontend: `pnpm --filter @cbs/admin-ui-plugin lint` must pass before committing.
- **One worktree per task** — clean up on Done or Blocked. Never leave stale worktrees.
- **Retry cap** — 2 attempts per task. On second failure, mark `Blocked` and continue.
- **Preserve layer contract (backend)** — Registry → Runner → Manager → GlobalManager facade. Generated code only calls `GlobalManager`.
- **Browser never calls Spring Boot directly (frontend)** — all backend traffic goes through the Nuxt BFF in `admin-ui-plugin/server/`.
- **Single-source theme (frontend)** — color changes belong in `frontend/components`; `admin-ui-plugin` consumes the preset from `@cbs/components`.
