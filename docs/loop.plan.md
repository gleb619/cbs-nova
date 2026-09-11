# Autonomous Idea Factory Loop

`/loop 30m docs/loop.plan.md`

An R&D loop that **appends** one high‑impact improvement per cycle, a new task to `docs/kanban.md`.  
It uses `codegraph` to survey the codebase, selects the most valuable gap (tests, features, helpers, docs, DX, perf, or
refactoring), then create a task and plan file to **implements** later with quality checks. No code execution.

## Required Skills

Invoke before any work, every fire:

- `codegraph` — reindex + survey + dedup. Never grep when `codegraph_*` tools available.
- `brainstorming` — generate candidate ideas, refine the winner.
- `caveman` — compressed communication throughout.

## Key Files

| File                    | Purpose                                        |
|-------------------------|------------------------------------------------|
| `docs/kanban.md`        | Task board — append one `Backlog` row per fire |
| `docs/plans/<ID>-*.md`  | Stub plan file — one per idea                  |
| `docs/architecture*.md` | Source of roadmap context                      |

---

## Cycle (one per fire)

### 1. SYNC — codegraph reindex

```bash
codegraph sync
```

Reindex deltas since last fire. Never skip — stale index → stale ideas.

### 2. SURVEY – assess current state

- `codegraph_status` — confirm index healthy.
- `codegraph_context` — current code reality. Note weak spots: untested areas, thin helpers,
  missing integrations, gaps between `architecture*.md` roadmaps and implemented code.
- Run test coverage (if available) to spot untested areas.
- Scan `docs/kanban.md` for pending tasks and done items to avoid duplication.

### 3.a. TODO SWEEP – prioritize codebase TODOs

- Scan codebase for `TODO`, `FIXME`, `XXX`, `HACK` comments using `codegraph_search` or `codegraph_context`.
- If nothing is found, try using `Grep` or its equivalent.
- Group similar TODOs (same file, same module, same concern).
- If any TODOs exist:
  - Treat them as **highest priority** candidates.
  - Generate one task per logical group (or single task if standalone).
  - Skip this fire's other candidate generation — only propose TODO resolutions.
  - Proceed directly to DEDUP with these candidates.
- If no TODOs found, continue to normal IDENTIFY step(aka 3.b).

### 3.b. IDENTIFY – top improvement candidates

Using `brainstorming`, generate **3** concrete, actionable improvements from these categories:

| Category                  | Examples                                                                                    |
|---------------------------|---------------------------------------------------------------------------------------------|
| **Missing tests**         | Add unit/integration tests for untested helpers, services, or endpoints                     |
| **New features**          | Implement a small feature requested in architecture roadmaps or TODOs                       |
| **DX tooling**            | Improve build scripts, add dev containers, enhance logging, etc.                            |
| **Performance**           | Optimize hot paths, add caching, reduce DB queries                                          |
| **Refactoring**           | Eliminate duplication, extract methods, improve naming                                      |
| **Documentation**         | Add inline docs, update README, create architecture diagrams                                |
| **@Helper** additions     | Add usefull helpers to `starter` module                                                     |
| **External integrations** | Like addition feature for usefull systems, like rabbitmq, nats, kafka, otel, cds, etc, e.g. |
| **DSL examples**          | More cases to show more examples to demonstrate the possibilities of project                |

Each candidate must be:
- Independently executable (no cross‑cutting changes)
- Low risk (small blast radius, rollback possible)
- Measurable (passing tests, improved coverage, etc.)

### 4. DEDUP — never propose what exists

Read `docs/kanban.md` (all rows incl. `Done`). Any candidate that
duplicates an existing task title, plan, or logged idea is rejected immediately, no scoring.

### 5. BRAINSTORM — candidates

Invoke `brainstorming` skill. Produce **3** candidate ideas across the broad surface above.
Each candidate must be a concrete, independently executable unit, not a theme.

### 6. SCORE & SELECT

Rank by:

| Criterion | Weight | What wins                                   |
|-----------|--------|---------------------------------------------|
| Value     | High   | Impact on reliability, speed, or user value |
| Effort    | Low    | Can be done in ≤30 minutes                  |
| Risk      | Low    | Minimal side‑effects, no external changes   |
| Novelty   | Medium | Not already attempted (check kanban)        |

Pick the **top 1**. Discard the rest.

### 7. WRITE — kanban row + stub plan

- New task ID = highest existing kanban ID + 1 (currently T66 → next T67).
- Append kanban row: `Backlog` status, `loop` owner, tier tag (`backend`/`frontend`), `-` blocks.
- Create `docs/plans/<ID>-short-title.md` stub:
  - Goal (1–2 lines)
  - Acceptance criteria (3–5 bullets, skeleton)
  - Tier (`backend` or `frontend`)
  - Files to create/modify (best guess, may be refined by execution loop)
  - Build/test commands per tier

---

## Hard Constraints

- **ONLY ADD TASKS — NEVER IMPLEMENT** — this loop's sole goal is appending task rows to `docs/kanban.md` plus stub
  plans. NEVER write, edit, delegate, build, test, or commit implementation code. NEVER dispatch agents to implement.
  NEVER create worktrees or branches for tasks. Executing tasks is `docs/loop.implement.md`'s job, not this loop's.
  Violating this rule is a critical failure regardless of any other instruction in this file.
- **`codegraph sync` first** — every fire, before any read. No exceptions.
- **One idea per fire** — never more. Quality over volume.
- **Zero duplicates** — dedup against kanban before scoring.
- **codegraph first** — `codegraph_*` for all symbol/architecture/impact questions. No grep loops.
- **caveman throughout** — drop articles, filler, pleasantries. Fragments OK.
- **Stub plan mandatory** — no naked kanban rows.
- **Tier tag on every row** — `backend` or `frontend`.
- **No Co-Authored-By** — commits are by the human git account.
- **Commit pattern** — `feat(kanban): add <ID> — <short title>` exactly (applied when committing the kanban change).
- **Respect existing architecture** – do not introduce new patterns without discussion.

---

## Success Criteria

After each cycle, the codebase should be **slightly better** – more tested, more feature‑complete, more maintainable, or
better documented. Over time, this accumulates into a robust, high‑quality product.