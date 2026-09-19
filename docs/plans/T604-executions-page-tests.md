# T604 — FE tests for Executions list + detail pages

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`frontend/admin-ui-plugin/app/pages/executions/index.vue` (234 lines: pagination, CSV export
URL, cancel-with-confirm-modal) and `executions/[id].vue` (373 lines: run detail) are the only
core pages without specs — `__tests__/` covers dsl-workbench, index, runner, schedules, webhooks
but not executions. Add specs following the existing page-test pattern (see
`pages/__tests__/schedules.spec.ts` for mock/store conventions).

Coverage targets:

**executions/index.spec.ts**
- [ ] Renders run rows from mocked API list response.
- [ ] Pagination controls drive `limit`/`offset` params (page 2 request).
- [ ] CSV export URL built from current filters/page.
- [ ] Cancel flow: row cancel button opens modal, confirm calls cancel API, error path surfaces
      `cancelError`.

**executions/[id].spec.ts**
- [ ] Renders detail fields from mocked run response (status, definition, timings).
- [ ] Missing/404 run renders error state, not crash.
- [ ] Any refresh/poll behavior present in the page is asserted once (not deeply).

## Tier

`frontend`

## Acceptance criteria

- [ ] Both spec files created under `frontend/admin-ui-plugin/app/pages/__tests__/`.
- [ ] All assertions above covered; no network calls leak (mock the composable/API layer the way
      sibling specs do).
- [ ] `cd frontend && pnpm test` green (existing suites unaffected).
- [ ] `make lint` (Biome) green.

## Out of scope

- `activity.vue` test (tiny feed page — separate follow-up if wanted).
- Any change to the pages themselves unless a real bug surfaces (then note it, don't fix silently).

## Files to create/modify

- `frontend/admin-ui-plugin/app/pages/__tests__/executions.spec.ts` (create)
- `frontend/admin-ui-plugin/app/pages/__tests__/executions-detail.spec.ts` (create — or one
  nested dir matching page path if the test runner convention requires it; follow sibling layout)

## Build/test commands

```bash
cd frontend && pnpm --filter @cbs/admin-ui-plugin test
make lint
```
