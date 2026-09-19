# T605 — Runbook recipe: inspect persisted compile diagnostics

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

The append-only compile-diagnostics log is fully shipped — backend
`GET /api/dsl/diagnostics` (`DslDiagnosticsHandler`, params `limit`/`offset`/`definition`),
BFF proxy `GET /api/v1/dsl/diagnostics`, Workbench diagnostics panel — but `docs/runbook.md`
(16 `##` sections) mentions diagnostics **zero** times. Operators troubleshooting a failing
publish/reload have no documented recipe.

Add one runbook section, e.g. `## Inspect compile diagnostics`, following the existing recipe
format (see the "Schedule a definition" section for style: short prose + exact curl against
`:3000` BFF + expected envelope + pointer to the UI surface).

Content to cover:
- [ ] `curl http://localhost:3000/api/v1/dsl/diagnostics?definition=<name>&limit=20` — example
      response envelope (`PageResponse` items with `occurredAt`, `source`, `definition`,
      `file`, `severity`, `code`, `message`).
- [ ] Filter by definition; pagination via `limit`/`offset`.
- [ ] Where entries come from (compile/reload/test sources append rows — verify actual `source`
      values from `CompileDiagnosticRecord` writers before writing; don't guess).
- [ ] UI equivalent: Workbench → diagnostics panel (frontmatter of
      `frontend/admin-ui-plugin/app/pages/dsl-workbench.vue`).
- [ ] Cross-link from `docs/architecture-backend.md` where appropriate ONLY if a natural spot
      exists (optional).

## Tier

`backend` (docs-only task touching `docs/`)

## Acceptance criteria

- [ ] New runbook section with exact working curl + realistic sample envelope (verify against a
      live stack or the handler/repository tests — no invented field values).
- [ ] `source` values in the example match what writers actually persist.
- [ ] No other runbook sections disturbed.
- [ ] `make lint` green (kanban check unaffected; docs not linted, but keep markdown style
      consistent with neighboring sections).

## Out of scope

- Any backend/BFF code change.
- SSE/streaming features.

## Files to create/modify

- `docs/runbook.md` (modify — one new section)
- `docs/architecture-backend.md` (optional, one cross-link line)

## Build/test commands

```bash
# verify example envelope against a live stack if running:
curl -s http://localhost:3000/api/v1/dsl/diagnostics?limit=5 | head -40
make lint
```
