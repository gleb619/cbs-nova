# T630 — Workbench breadcrumb shows file name, not construct name

## Goal

In `dsl-workbench.vue` header breadcrumb (line 629–632), display the file name backing the
selected construct instead of the construct name. Remove the TODO comment.

## Why

`dsl-workbench.vue:630` TODO: breadcrumb shows `selectedConstruct.name` — users navigate by
files (explorer is file-based, `isFileBacked`/`loadSourceFile` already key off
`selectedConstruct.filePath`). Construct name is redundant with the editor content; file name is
the navigation anchor.

## Acceptance criteria

- [ ] Breadcrumb shows basename of `selectedConstruct.filePath` when file-backed
      (e.g. `order-metrics.dsl.yaml`).
- [ ] Fallback to `selectedConstruct.name` when `filePath` absent (non-file-backed construct).
- [ ] TODO comment removed.
- [ ] Extend `dsl-workbench.spec.ts`: file-backed selection shows file name; non-file-backed
      falls back to construct name.
- [ ] `cd frontend && pnpm typecheck && pnpm test` green; `make lint` passes.

## Tier

`frontend`

## Files to create/modify

- Modify: `frontend/admin-ui-plugin/app/pages/dsl-workbench.vue` (~lines 629–632)
- Modify: `frontend/admin-ui-plugin/app/pages/__tests__/dsl-workbench.spec.ts`

## Build/test commands

```bash
cd frontend && pnpm typecheck && pnpm test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Display-only change — no store/state refactor.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
