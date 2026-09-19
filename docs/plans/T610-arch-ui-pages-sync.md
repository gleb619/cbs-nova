# T610 — Sync architecture-ui.md with shipped page surface

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`docs/architecture-ui.md` drifted from the shipped admin UI:

1. Line ~55 claims the module "registers pages (Dashboard, Runner, DSL Workbench, Executions)
   via `extendPages`" — actual registration in `frontend/admin-ui-plugin/module.ts:324+` is
   **9 pages**: Dashboard, Runner, Schedules, Activity, Webhooks, Notifications, DSL Workbench,
   Executions list, Executions detail.
2. Doc has **zero** mentions of Notifications; no sections for the Schedules / Webhooks /
   Activity / Notifications pages (sections exist for Executions, Runner, Workbench, helper
   search, introspection).

Task: update the registration list and add one compact section per undocumented page
(Schedules, Webhooks, Activity, Notifications) — what each shows, which BFF routes back it,
composable(s) used. Match the existing per-page section style (see "Executions page" section).
Verify each page's actual data sources from its `.vue` file + composables before writing — do
not infer.

## Tier

`frontend` (docs-only)

## Acceptance criteria

- [ ] Registration list at ~line 55 names all 9 pages, matches `module.ts` extendPages block.
- [ ] Four new sections (Schedules, Webhooks, Activity, Notifications), each: purpose, key
      components, BFF routes consumed (exact paths), composable names — all verified against
      source.
- [ ] No stale references introduced; leave existing sections untouched unless factually wrong
      (fix wrong ones, list them in execution notes).
- [ ] `make lint` green.

## Out of scope

- Backend docs (T591/T592 own arch-backend.md).
- `docs/frontend/dsl-workbench.md` (separate file, untouched).

## Files to create/modify

- `docs/architecture-ui.md` (modify)

## Build/test commands

```bash
make lint
```
