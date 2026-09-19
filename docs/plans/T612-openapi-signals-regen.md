# T612 — Regenerate docs/openapi.json (signals routes missing) + fix regen ordering

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`docs/openapi.json` (last regenerated in T568 commit `850b0bf9`) contains the change-requests
routes but is **missing the T567 signals routes** — `DslSignalsRouterConfiguration` is an
unconditional `@Configuration` with `@RouterOperation` annotations, so a live
`/v3/api-docs` today exposes `/api/dsl/signals/**` while the committed contract does not.
`make openapi-check` drift-guard therefore fails against a current stack (or worse, hides the
gap when run against a stack booted from an older checkout).

Task:
1. Bring the stack up (Postgres required; Temporal per `make openapi` header note), confirm
   `curl :8090/v3/api-docs | grep signals` shows the routes.
2. `make openapi` to regenerate `docs/openapi.json`.
3. Diff the result — expect only additive signals entries; anything else unexpected must be
   explained in execution notes before committing.
4. `make openapi-check` green.
5. Root-cause note: why did T568's regen miss signals (regen ran against a boot without T567
   merged, or before rebase)? Record the answer in the plan execution notes so the workflow
   avoids it (likely: always regen openapi as the LAST step after merging main).

## Tier

`backend`

## Acceptance criteria

- [ ] `docs/openapi.json` contains `/api/dsl/signals/**` operations with their
      `@RouterOperation` metadata (operationId, params).
- [ ] `make openapi-check` exits 0 (run against freshly booted stack).
- [ ] Diff is signals-only (or other diffs individually explained).
- [ ] Root-cause note written; if the fix is procedural, add one line to the Makefile
      `openapi` target help text or `CLAUDE.md` caveats only if genuinely actionable.
- [ ] `make lint` green.

## Out of scope

- New endpoints or annotation changes.
- routeCoverage spec (already covers signals BFF paths).

## Files to create/modify

- `docs/openapi.json` (regenerate)

## Build/test commands

```bash
docker compose -f app/docker-compose.yml up -d postgres
SERVER_PORT=8090 backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun -x test &
make openapi
make openapi-check
make lint
```
