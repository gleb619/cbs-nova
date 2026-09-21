# T639 — Runbook recipe: VHS tape lifecycle (record → list → scrub → replay)

## Goal

Add a runbook section documenting the operator-facing VHS flow: enabling recording, listing
tapes, inspecting a tape's events, scrubbing sensitive fields, and running a replay — with
verified curl round-trips against a live stack, same style as the existing
"Schedule a definition" recipe.

## Why

Epic 7 shipped VHS record/replay (35 classes: recorder, sink, scrubber, management endpoints,
replay engine + `docs/vhs-tape-format.md`, `docs/vhs-manifest.md`) but `docs/runbook.md` has no
VHS recipe. Operators have format docs, no procedure.

## Acceptance criteria

- [ ] Recipe covers: enable recording (config/props), record a run, list tapes, fetch one
      tape's summary, scrub rules application, run a replay, read replay result.
- [ ] Every curl command verified against a running stack (backend + BFF); exact request/response
      envelopes shown as in sibling recipes.
- [ ] Cross-links to `docs/vhs-tape-format.md` and `docs/vhs-manifest.md`.
- [ ] Failure modes covered: tape not found, replay against drifted manifest.
- [ ] `make lint` passes (kanban check unaffected).

## Tier

`backend`

## Files to create/modify

- Modify: `docs/runbook.md`
- Read first: `docs/vhs-tape-format.md`, `docs/vhs-manifest.md`, VHS management endpoints
      (`VhsManagementHandler`), existing recipe style in runbook.

## Build/test commands

```bash
make up && make backend &  # stack for verification
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Verified envelopes only — no copied-from-code guesses.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
