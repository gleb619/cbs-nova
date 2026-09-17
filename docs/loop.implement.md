# Autonomous Implementation Loop

`/loop 30m docs/loop.implement.md`

This file is the prompt for `/loop`. Each session handles one batch of tasks, keeps context small, then hands off to the
next session. This loop **only implements** — it picks `Backlog` tasks that already have a plan file and delegates,
verifies, merges. Planning — writing plan files, decomposing new task batches — is `docs/loop.plan.md`'s job. Kanban
statuses are only `Backlog` / `In Progress` / `Blocked` / `Done` — there is no `Ready`; a `Backlog` task with a plan
file is implementable. If no `Backlog` task has a plan file, stop and let `loop.plan.md` replenish it; do not plan here.

The loop covers **both backend (Java / Temporal) and frontend (Vue / Nuxt) tasks**. Pick the next ready task regardless
of tier; only the build, test, and convention checks change between backend and frontend work.

## Required Skills

Invoke before any work:

- `caveman` — compressed communication throughout all sessions
- `codegraph` — codebase exploration; never grep when `codegraph_*` tools are available
- `delegate-with-at` — delegate code writing to subagents via the `at` CLI
- `cbs-nova-kanban` — when reading or updating `docs/kanban.md`; always use `make kanban-*` commands

*Note* - a `cbs-nova-kanban` skill located at `./.agents/skills/cbs-nova-kanban/`

## Key Files

| File                           | Purpose                                                       |
|--------------------------------|---------------------------------------------------------------|
| `docs/architecture.md`         | Top-level system goal and tier overview                       |
| `docs/architecture-backend.md` | Java / Temporal backend overview and roadmap                  |
| `docs/architecture-ui.md`      | Vue / Nuxt frontend overview and build commands               |
| `docs/dsl/*.md`                | DSL constructs, authoring rules, codegen, runtime             |
| `backend/AGENTS.md`            | Backend coding conventions, module map, build commands        |
| `docs/kanban.md`               | Task board — source of truth for current loop state           |
| `Makefile`                     | Kanban CLI via `make kanban-*`                                |
| `docs/plans/`                  | Detailed plan files for each task (written by `loop.plan.md`) |
| `docs/loop.plan.md`            | Planning loop — writes plan files for `Backlog` tasks         |

`make kanban-add` is owned by `docs/loop.plan.md`; this loop only updates status. When rows are added, `DESCRIPTION` must be the final argument and is constrained to 128 characters — longer text is accepted, trimmed to 125, and suffixed with `...` (a warning is logged when trimming).

## Entry Point — Determine Current State

Read `docs/kanban.md`. Map kanban state to loop state:

| Kanban condition                                          | Go to state                                       |
|-------------------------------------------------------------|--------------------------------------------------|
| Any task `In Progress`                                       | VERIFY (session interrupted — resume)             |
| Any `Backlog` task has a plan file under `docs/plans/`       | SCAN → SELECT                                     |
| `Backlog` tasks exist but none have a plan file               | STOP — `loop.plan.md` must write one first        |
| No `Backlog` tasks (all `Done` / `Blocked`)                   | STOP — backlog empty; `loop.plan.md` must replenish |

---

## State: SCAN

Run once per new batch before SELECT:

1. `codegraph_status` — confirm index is healthy.
2. `codegraph_context` — survey current codebase state.
3. Transition to SELECT.

---

## State: SELECT

1. Pick next `Backlog` task that has a plan file at `docs/plans/<ID>-short-title.md` (lowest ID first).
2. Read the plan file (written by `loop.plan.md`). If missing or incomplete
   (no goal/acceptance criteria/tier/files/build commands), STOP and flag it — do not author the plan here.
3. Use `codegraph_context` + one `codegraph_explore` call to understand affected code areas from the plan's file list.
4. Confirm task tier from the plan:
   - **Backend task** — code under `backend/` or `dsl-examples/`, build with Gradle.
   - **Frontend task** — code under `frontend/`, build with pnpm.
5. Update kanban: mark next task `In Progress`:
   ```bash
   make kanban-start
   ```
   Or for a specific task: `make kanban-status ID=<id> STATUS="In Progress"`.
6. Transition to DELEGATE.

---

## State: DELEGATE

1. Create a git worktree for the task:
   ```bash
   make task-start TASK_ID=<task-id>
   ```
   Equivalent:
   ```bash
   git worktree add ../cbs-nova-<task-id> -b feat/<task-id>
   ```
2. Invoke `delegate-with-at` skill. Pass:
   - Plan file path: `docs/plans/<ID>-short-title.md`
   - Worktree path: `../cbs-nova-<task-id>`
   - Constraint: work entirely inside the worktree
   - Constraint: plan file must require the subagent to load and use skills `caveman` and `codegraph` before any 
     code work, if missing add one in instructions
   - Constraint: plan file must include this exact line for subagent self-direction:
     ```
     You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
     ```, if missing add one in instructions
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
   make task-merge TASK_ID=<task-id>
   ```
   Equivalent:
   ```bash
   git merge feat/<task-id>
   git worktree remove ../cbs-nova-<task-id>
   git branch -d feat/<task-id>
   ```
   Set task status to `Done`:
   ```bash
   make kanban-status ID=<task-id> STATUS="Done"
   ```
   Transition to SCAN (pick next implementable `Backlog` task, if any).
4. **Fail, attempt < 2** — increment attempt count. Append failure context (error output, stack trace) to the plan file
   under a `## Retry Notes` section. Transition to DELEGATE.
5. **Fail, attempt = 2** — set task status to `Blocked`:
   ```bash
   make kanban-status ID=<task-id> STATUS="Blocked"
   ```
   Append failure summary to plan file. Remove worktree:
   ```bash
   make task-abort TASK_ID=<task-id>
   ```
   Equivalent:
   ```bash
   git worktree remove --force ../cbs-nova-<task-id>
   git branch -d feat/<task-id>
   ```
   Transition to SCAN (next implementable `Backlog` task). Do not stop the loop.

---

## State: CLEANUP

Triggered when all tasks in the current batch are `Done` or `Blocked`:

1. Remove all `Done` task rows from the kanban table. Keep `Blocked` rows for human review.
2. Commit the cleaned kanban:
   ```
   feat(kanban): clean completed tasks from batch
   ```
3. STOP — no more implementable `Backlog` tasks. `loop.plan.md` decomposes the next batch and writes plan files;
   this loop resumes at Entry Point once it does.

---

## Hard Constraints

- **Implement only — never plan** — no writing/editing plan files, no decomposing new task batches. That's
  `docs/loop.plan.md`'s job. If no `Backlog` task has a plan file, STOP.
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
