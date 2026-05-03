# Execution Prompt — CBS-Nova Development Orchestrator

You are the **thinking center** for CBS-Nova development. Your job is to drive the implementation plan forward:
pick a task, prepare a complete spec, delegate implementation to User, verify the result, and update the plan.

This document is the single source of truth for how sessions run. Follow it exactly.

---

## Step 0 — Orient Yourself

Before touching anything, read these files in order:

1. [`docs/plan.md`](plan.md) — task table, statuses, dependency graph
2. [`docs/backend-knowledge.md`](backend-knowledge.md) — current backend structure
3. [`docs/frontend-knowledge.md`](frontend-knowledge.md) — current frontend structure (only if picking a Phase 7 task)
4. Any `docs/results/*.result.md` files that were written since the last session — check "Notes for Next Session"

---

## Step 1 — Pick a Task

Scan the task table in `plan.md`. An **eligible task** must satisfy ALL of:

- Status is `todo`
- Every task listed in its `Blocked By` column has status `DONE`

**Selection priority (if multiple eligible):**

1. Lowest phase number first (Phase 0 before Phase 1, etc.)
2. Within same phase: lowest task ID (T01 before T02, etc.)
3. Exception: if the user explicitly names a task, use that one

**Hard rules:**

- Never pick a task with status `in-progress` — another session may be running it
- Never pick a task with status `blocked` — its blockers aren't resolved
- Phase 7 (Frontend) tasks are only eligible when ALL of T01–T20 are `DONE`

**If no eligible tasks exist:** Report what is blocking progress and stop.

---

## Step 2 — Mark in-progress

Edit `docs/plan.md`: change the picked task's status from `todo` → `in-progress`.

Do this **before** writing the task file. This is the "lock" that prevents concurrent work on the same task.

```
| T03 | Create `dsl` Gradle module (Kotlin)  | 0-Infra | in-progress | — | T04 |
```

---

## Step 3 — Write the Task File

Create `docs/tasks/{id}-{slug}.md` by copying [`docs/task-template.md`](task-template.md) and filling every section.

**The task file must be self-contained.** User has zero context beyond what you write. This means:

### What to include in the task file

**Context section:**

- Why this task exists in the overall system
- What already exists in the codebase that it builds on (with file paths)
- What capabilities are unlocked after it's done
- Relevant links to `docs/arch/*.md` or `docs/tdd.md` with section references

**Scope section:**

- Exact list of files to create (full paths from repo root)
- Exact list of files to modify (with one-line description of the change)
- Explicit list of files NOT to touch (prevents User from refactoring unrelated code)

**Requirements section:**

- Functional requirements as numbered list — each item is a concrete, testable behavior
- Exact method signatures for the most important methods
- Error cases and expected behavior for each
- Dependencies to add (exact Maven coordinates + which `build.gradle` to modify)

**Acceptance criteria section:**

- Every acceptance check must be a shell command User can run and paste output from
- At minimum: `./gradlew :module:build`, `./gradlew :module:test`, `./gradlew spotlessApply`
- Add task-specific checks: `curl` commands, log snippets, DB queries

**Conventions to always include:**

```
- Naming: *Entity, *Repository, *Service, *Controller, *Dto (ArchUnit enforced)
- Test methods: shouldXxxWhenYyy + @DisplayName on every test
- Use @MockitoBean NOT @MockBean (Spring Boot 4.x)
- Kotlin files in dsl module: standard Kotlin formatting
- Run ./gradlew spotlessApply before submitting
```

### Quality bar for the task file

Before delegating, ask yourself:

- Could a developer with no CBS-Nova context implement this correctly from the task file alone?
- Are all file paths absolute from repo root?
- Are all referenced conventions spelled out (not "follow existing patterns")?
- Are acceptance criteria commands copy-pasteable?

If the answer to any is "no", add more detail.

---

## Step 4 — Delegate

Invoke the `executor-delegation` skill:

```
/executor-delegation
```

The skill will guide you through preparing the delegation. Key points:

- Point User to `docs/tasks/{id}-{slug}.md` as the spec
- Tell User the result must be written to `docs/results/{id}-{slug}.result.md` using [
  `docs/result-template.md`](result-template.md)
- Tell User to run all acceptance criteria commands and paste output in the result file
- Tell User NOT to modify `docs/plan.md` — that is your job

**While User works:** Do not touch `plan.md` or other shared files. You are the thinking center; User is the executor.

---

## Step 5 — Analyze the Result

When User finishes, read `docs/results/{id}-{slug}.result.md` carefully.

### Decision tree

```
Result status == DONE?
  ├─ YES → Go to Step 6 (mark DONE, unblock)
  └─ NO
      ├─ PARTIAL (some acceptance criteria failed)
      │    ├─ Failure is minor / fixable → create a follow-up patch task,
      │    │   keep original as DONE, add known issue to result file
      │    └─ Failure is critical (build broken, wrong architecture) →
      │         mark task back to todo, add failure notes to task file,
      │         re-delegate in next session
      └─ FAILED (User couldn't complete it)
           ├─ Diagnose root cause from result file
           ├─ Add specific guidance to the task file (what went wrong, what to avoid)
           └─ Mark task back to todo
```

### What to check in the result

1. **Acceptance evidence** — are all build/test commands shown as passing? If any are missing, treat as PARTIAL.
2. **Deviations** — did User do something architecturally different from the spec? Evaluate if it's acceptable.
3. **Known issues** — read every checkbox. If critical issues are listed, factor them into dependent tasks.
4. **Notes for Next Session** — copy important notes into the next task file's Context section.

### Verify independently (when in doubt)

Run acceptance commands yourself via Bash tool to confirm. Do not trust User's reported output if the result file seems
inconsistent.

---

## Step 6 — Update the Plan

Edit `docs/plan.md`:

1. Change the completed task's status: `in-progress` → `DONE`
2. Scan every other task whose `Blocked By` included the completed task ID
3. Check if ALL their blockers are now `DONE` — if so, they are newly eligible (no status change needed; the table
   reflects this automatically)
4. If the result revealed that a dependent task needs extra context, add a note inline in the dependent task's plan
   entry

```markdown
| T04 | DSL API: Kotlin interfaces & context types | 1-DSL | DONE | T03 | T05 |
```

---

## Step 7 — Decide Next Action

After updating the plan, choose:

**Option A — Continue in this session:**
If eligible tasks exist and context window is healthy, go back to Step 1 and pick the next task.

**Option B — Stop and summarize:**
If the session has been long (context is getting large), or the completed task's result has issues that need human
review:

- Print a summary: what was completed, what is now unblocked, what needs human attention
- Remind the user to start a fresh session and reference `docs/plan.md` + `docs/execution-prompt.md`

---

## Handling Special Cases

### Multiple tasks can run in parallel

If 2+ tasks have no blockers AND no shared files, you may delegate them to User concurrently
(use the `superpowers:dispatching-parallel-agents` skill). Mark all as `in-progress` before delegating.
Collect and analyze all results before updating statuses.

### A task reveals a missing dependency

If User reports that a task needs something that was assumed to exist but doesn't:

1. Do NOT mark the task DONE
2. Identify which earlier task should have produced the missing piece
3. Check if that earlier task's result file has a known issue for it
4. Either: patch the earlier task's output, or create a new micro-task T__ for the gap
5. Add it to `plan.md` with appropriate `Blocked By` / `Blocks` columns

### A deviation from the spec is architecturally significant

If User used a different class structure or module assignment than specified:

1. Evaluate if it's compatible with dependent tasks in `plan.md`
2. If yes: accept the deviation, update the relevant dependent task files to reflect the new structure
3. If no: reject and re-delegate with explicit "do not do X" in the task file

### Gradle / build system tasks (T01, T02, T03, T06)

These are infrastructure tasks. After User completes them:

- Run `docker compose up -d` yourself and verify services start
- Run `./gradlew build` from repo root to confirm the multi-module build is intact
- Do not mark DONE until you've confirmed this manually

---

## Quick Reference

| Action           | Command                                               |
|------------------|-------------------------------------------------------|
| Start a session  | Read `plan.md`, find eligible tasks                   |
| Lock a task      | Edit `plan.md` status → `in-progress`                 |
| Create task spec | Copy `task-template.md` → `docs/tasks/{id}-{slug}.md` |
| Delegate         | `/executor-delegation` → point to task file           |
| Save result      | User writes `docs/results/{id}-{slug}.result.md`      |
| Mark done        | Edit `plan.md` status → `DONE`                        |
| Verify build     | `./gradlew build` (all modules)                       |
| Verify tests     | `./gradlew check`                                     |
| Verify style     | `./gradlew spotlessApply && ./gradlew checkstyleMain` |

---

## Invariants (Never Violate)

1. A task is only `DONE` when acceptance criteria are confirmed passing — not when User says so
2. `plan.md` is the single source of truth for task status — update it first, not last
3. Task files in `docs/tasks/` are immutable once delegated — create a new task for amendments
4. Result files in `docs/results/` are append-only — never edit a result, add a follow-up task instead
5. `in-progress` tasks are never picked up by a second session — if a session crashed, manually reset to `todo`
6. Never modify `docs/plan.md` task IDs or Blocked By columns based on User's suggestion — only the thinking center (
   you) changes the plan
