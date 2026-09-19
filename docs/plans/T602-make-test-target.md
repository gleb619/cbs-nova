# T602 — `make test` aggregate test target

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep cbs-nova kanban rules in mind. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Add a single `make test` target that runs the full test surface (backend + frontend) in one
command. Today the Makefile has `lint`, `typecheck`, `doctor`, `seed`, per-suite gradle
invocations scattered through docs, but no aggregate test entry point — a newcomer (or CI, or
the implement loop) must know three different commands.

Suites to aggregate:

| Tier | Command |
|---|---|
| Backend platform | `backend/dsl-platform/gradlew -p backend/dsl-platform test` |
| Backend starter | `backend/dsl-starter/gradlew -p backend/dsl-starter test` |
| Frontend | `cd frontend && pnpm test` (already fans out to admin-ui-plugin + components, `--no-bail`) |

## Tier

`backend` (Makefile lives at repo root; recipe spans tiers)

## Acceptance criteria

- [ ] `make test` target exists, documented in `make help` output, placed near `lint` in the
      Makefile.
- [ ] Runs the three suites above in order (fail-fast per suite is fine; report which suite
      failed in the recipe output).
- [ ] `make test FE=0` (or `BE=0`) toggles let the user run one tier — match the existing
      Makefile variable style (check how `dev`/`lint` do toggles; if none exists, plain
      `ifeq` guards are fine).
- [ ] Exit code non-zero when any suite fails.
- [ ] `make lint` still green (kanban check untouched; Makefile has no formatter gate).
- [ ] Mention the target in the "Quick end-to-end check" area of `CLAUDE.md` only if it does
      not contradict the existing per-tier instructions (optional, one line).

## Out of scope

- Integration tests (`:starter:integrationTest`, Testcontainers) — keep out of the default
  aggregate; a `make test-integration` follow-up can be filed separately if wanted.
- CI workflow files.

## Files to create/modify

- `Makefile` (modify)
- `CLAUDE.md` (optional, one line)

## Build/test commands

```bash
make -n test          # dry-run: verify recipe expands correctly
make test BE=0        # frontend-only path
make lint
```
