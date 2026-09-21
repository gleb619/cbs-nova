# T641 — PreviewHistoryPanel component spec

## Goal

Add spec for `frontend/components/src/components/dsl/PreviewHistoryPanel.vue` — workbench panel
listing preview/dry-run history. Cover rendering of history entries, selection callback, empty
state.

## Why

Last logic-bearing component in the package without a spec (82/96 covered; remaining uncovered
after this = App* layout chrome + skeleton). Preview history drives re-running and diffing
dry-runs from the workbench — selection regressions break that flow.

## Acceptance criteria

- [ ] Renders history entries with their fields (timestamp, status, input summary — whatever the
      component shows) from props.
- [ ] Entry selection emits the component's selection event with correct payload.
- [ ] Empty state covered.
- [ ] Style follows sibling panel specs in `frontend/components/src/components/__tests__/`.
- [ ] `cd frontend && pnpm typecheck && pnpm test` green; `make lint` passes.

## Tier

`frontend`

## Files to create/modify

- Create: `frontend/components/src/components/__tests__/PreviewHistoryPanel.spec.ts`
- Read first: `PreviewHistoryPanel.vue`, sibling panel spec for style (e.g. history-panel specs).

## Build/test commands

```bash
cd frontend && pnpm typecheck && pnpm test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no component changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
