# T646 — CI: run starter integrationTest with service containers

## Goal

Add a CI job that runs `backend/dsl-platform/gradlew -p backend/dsl-starter
:starter:integrationTest` against real Postgres + Temporal service containers (GitHub Actions
`services:` block, mirroring `app/docker-compose.yml`).

## Why

CI runs unit suites only (`:starter:test`, dsl-platform, dsl-plugins). The entire integration
layer — `starter/src/integrationTest/` (PreviewDryRun, BatchProcessing, HttpResilience,
UnreliableApi, SignalProbe, DslVersioning, example execution) — never runs in CI. CI green does
not mean examples/E2E work. Ports and env vars these tests expect: read
`starter/src/integrationTest/resources/application.yml` and wire the services accordingly.

## Acceptance criteria

- [ ] New job (or extended backend job) with `postgres` + `temporal` (+ temporal's own DB)
      service containers; health-check `wait` on each.
- [ ] Env/ports match what integration tests expect (SERVER_PORT, DB URL, Temporal target) —
      verified by reading the integrationTest application.yml, not guessed.
- [ ] Gradle invocation publishes platform first (same chain as existing jobs:
      dsl-platform test → publishToMavenLocal → integrationTest).
- [ ] Runs green on a branch pushed to CI; failure mode readable (test reports uploaded on
      failure via `actions/upload-artifact`).
- [ ] Flakiness budget: if some tests are environment-sensitive, exclude them explicitly with a
      comment naming why — no silent skips.
- [ ] `make lint` unaffected.

## Tier

`backend`

## Files to create/modify

- Modify: `.github/workflows/ci.yml`

## Build/test commands

```bash
# local approximation before pushing:
docker compose -f app/docker-compose.yml up -d postgres
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:integrationTest
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- CI config only — no test code changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
