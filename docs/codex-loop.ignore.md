# Autonomous Codex Improvement Loop

`/goal 30m docs/codex-loop.ignore.md`

Continuously identify small, high-value improvements across backend (Java / Temporal / DSL) and frontend (Vue / Nuxt /
BFF), then feed them into `docs/kanban.md` as concrete `Backlog` tasks. No code execution here — this loop only proposes
tasks.

## Required Skills

Invoke before any work, every fire:

- `caveman` — compressed communication throughout.
- `brainstorming` — generate candidate ideas, refine the winner.
- `codegraph` — reindex + survey + dedup. Never grep when `codegraph_*` tools available.

## Key Files

| File                           | Purpose                                                        |
|--------------------------------|----------------------------------------------------------------|
| `docs/kanban.md`               | Task board — append one `Backlog` row per fire                 |
| `docs/plans/<ID>-*.md`         | Stub plan file — one per idea                                  |
| `docs/codex-ideas.ignore.md`   | Codex-only ideas log (chosen + rejected)                       |
| `docs/glm-loop.ignore.md`      | Reference loop — read-only, never edit                         |
| `docs/ideas.ignore.md`         | Reference ideas log — read-only, never edit                    |
| `docs/architecture*.md`        | Source of roadmap context                                      |
| `docs/loop.md`                 | Execution loop — read-only here, never edit                    |
| `backend/AGENTS.md`            | Backend conventions and module map                             |
| `frontend/AGENTS.md`           | Frontend conventions                                           |

## Candidate Surface

Loop may propose concrete, independently executable tasks across:

- `helper` — new `@Helper` classes in `backend/starter` or utility helpers.
- `integration` — new external integrations (REST clients, Keycloak, observability, storage, etc.).
- `test` — WireMock, Testcontainers, JUnit, integration, chaos, or frontend tests.
- `docs` — DSL examples, architecture docs, API guides, README improvements.
- `perf` — runtime, build, or frontend performance improvements.
- `dx` — developer experience, tooling, scripts, lint, CI.
- `example` — new DSL examples or runnable demos.
- `refactor` — small codegraph-confirmed cleanups with low blast radius.
- `ui` — Nuxt pages, components, composables, Pinia stores, BFF routes, layout polish.

## Cycle (one per fire)

### 1. SYNC — `codegraph sync`

```bash
codegraph sync
```

Never skip. Stale index → stale ideas.

### 2. CONTEXT — codebase survey

- `codegraph_status` — confirm index healthy.
- `codegraph_context` or `codegraph query` — current code reality. Note weak spots:
  - Untested areas
  - Thin helper library
  - Missing integrations
  - Gaps between `architecture*.md` roadmaps and implemented code
  - Frontend TODOs, unfinished pages, missing BFF routes

### 3. DEDUP — never propose what exists

Read `docs/kanban.md` (all rows incl. `Done`) and `docs/codex-ideas.ignore.md`. Any candidate that duplicates an existing task title, plan, or logged idea is rejected immediately, no scoring.

### 4. MINE — maybe a past reject is now the best idea

Re-read `docs/codex-ideas.ignore.md` `Rejected` rows. For each, check whether its rejection reason still holds against current `codegraph_context`:

- Reason was risk/novelty and the blocker is now gone → eligible. Revive it.
- Reason was dup → never revive (it is still a dup).

If a revived reject beats this cycle's fresh candidates on value/risk/novelty, pick it. Update its log row `Outcome` → `Chosen` and append `(revived <date>)` to `Reason`. Otherwise leave the log untouched and brainstorm fresh.

### 5. BRAINSTORM — candidates

Invoke `brainstorming` skill. Produce 3–5 candidate ideas across backend and frontend surfaces. Each candidate must be a concrete, independently executable unit, not a theme.

### 6. SCORE — pick one

Rank candidates together regardless of tier:

| Criterion | What wins                                       |
|-----------|-------------------------------------------------|
| Value     | Higher user/agent impact                        |
| Risk      | Lower wins (low blast radius, no infra)         |
| Novelty   | codegraph-confirmed gap, not in ideas log       |

Keep top 1. Discard rest. Tie-break: lowest risk. If tie persists, prefer frontend if last chosen task was backend, and vice versa.

### 7. LOG — record all candidates

Append every candidate to `docs/codex-ideas.ignore.md` table:

| ID-proposed | Title | Category | Outcome | Reason |
|-------------|-------|----------|---------|--------|

- `Outcome`: `Chosen` or `Rejected`.
- `Reason`: one line — value/risk/novelty verdict, or dup source if rejected in step 3.

### 8. WRITE — kanban row + stub plan

- New task ID = highest existing kanban ID + 1.
- Append kanban row: `Backlog` status, `loop` owner, tier tag (`backend`/`frontend`), `-` blocks.
- Create `docs/plans/<ID>-short-title.md` stub with:
  - Goal (1–2 lines)
  - Acceptance criteria (3–5 bullets, skeleton)
  - Tier (`backend` or `frontend`)
  - Files to create/modify (best guess)
  - Build/test commands per tier:
    - Backend: `./gradlew spotlessApply && ./gradlew build test` from `backend/`
    - Frontend: `pnpm install && pnpm --filter admin-ui lint && pnpm --filter admin-ui test` from `frontend/`

### 9. COMMIT

```bash
git add docs/kanban.md docs/plans/<ID>-*.md docs/codex-ideas.ignore.md
git commit -m "feat(kanban): add <ID> — <short title>"
```

One commit. No `Co-Authored-By`. Loop ends — next fire in 30 min.

## Hard Constraints

- `codegraph sync` first — every fire, before any read. No exceptions.
- One idea per fire — never more.
- Zero duplicates — dedup against kanban + `docs/codex-ideas.ignore.md` before scoring.
- codegraph first — `codegraph_*` for all symbol/architecture/impact questions. No grep loops.
- caveman throughout — drop articles, filler, pleasantries. Fragments OK.
- Stub plan mandatory — no naked kanban rows.
- Tier tag on every row — `backend` or `frontend`.
- No `Co-Authored-By` — commits are by the human git account.
- Commit pattern — `feat(kanban): add <ID> — <short title>` exactly.
- Never edit `docs/loop.md`, `docs/glm-loop.ignore.md`, or `docs/ideas.ignore.md` — those files belong to other loops.
- Cross-tier scoring — all candidates compete in one pool; no alternating tier quotas.
