# T642 — Roadmap sync: mark Epic 1–7 status + next-horizon sketch

## Goal

Update `docs/roadmap.md`: add per-epic status markers using the declared vocabulary, record
which workstreams of each epic shipped vs deferred (cross-checked against kanban history and
architecture docs), and add a short "Next horizon" sketch section listing candidate post-Epic-7
directions.

## Why

Roadmap declares kanban-style status vocabulary but no epic carries a status. Epics 1–7 all
shipped (kanban history T4xx–T56x) yet the doc reads as all-pending — every reader (human or
agent loop) must reconstruct status from git archaeology. Next-horizon sketch seeds future loop
cycles.

## Acceptance criteria

- [ ] Each Epic 1–7 heading or intro carries status (mostly `Done`; any partial epic lists the
      deferred workstreams explicitly — e.g. Epic 7 "CI perf gate" is not shipped).
- [ ] Per-epic one-line summary of what actually shipped, linking the key kanban IDs /
      architecture sections.
- [ ] "Next horizon" section: 3–5 candidate directions, each 2–3 lines, no fake detail.
- [ ] Sequencing notes reviewed — strike items now stale.
- [ ] Fact-check every status claim against kanban/architecture docs; no guesses.
- [ ] `make lint` passes.

## Tier

`backend`

## Files to create/modify

- Modify: `docs/roadmap.md`
- Read first: `docs/kanban.md` (history incl. Done rows), `docs/architecture-backend.md`,
      `docs/architecture-ui.md`, `docs/vhs-*.md`.

## Build/test commands

```bash
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Doc-only; verified claims only — if an epic's shipped scope is unclear, mark `PARTIAL` with
      the open questions rather than inventing status.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
