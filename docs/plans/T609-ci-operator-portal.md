# T609 — CI job for the operator-portal host app (`app/ui`)

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`app/ui` (@cbs/operator-portal) is the reference host that consumes `@cbs/admin-ui-plugin` via
the local-tarball registry (`pnpm pack:local`) — the closest thing to a consumer contract test
for the plugin. Nothing in CI builds or tests it: `.github/workflows/ci.yml` covers
`frontend/**` and backend builds only. A plugin API change can silently break the documented
integration path.

Add a CI job:

1. `pnpm install` at repo `frontend/` (workspace deps for components + plugin).
2. `cd app/ui && pnpm install && pnpm pack:local && pnpm install` (pack script rebuilds server
   routes and repacks both tarballs — see `app/ui/scripts/pack-local.sh`).
3. `pnpm test` (smoke spec mounts the host landing page).
4. `pnpm build`.

## Tier

`frontend`

## Acceptance criteria

- [ ] New job in `ci.yml` (own job; does not extend existing FE job — pack step is slow).
- [ ] Job green on a PR run; verify it actually fails when the plugin export surface breaks
      (temporary local experiment — e.g. rename an exported composable — then revert; document
      the experiment result in the execution notes).
- [ ] Steps use the same pnpm/node versions as the existing FE job (copy its setup steps).
- [ ] Tarball artifacts are NOT committed — `local-registry/` must already be gitignored
      (verify; if not, add the ignore entry in the same PR).
- [ ] `make lint` green.

## Notes

- `pnpm pack:local` rewrites `app/ui/package.json` to point at fresh tarballs — the job should
  restore/leave no dirty state (CI runners are ephemeral; just don't cache the mutated
  package.json).
- If the full pack+build proves too slow (>5 min), land test-only first and note build as
  follow-up — do not skip silently.

## Out of scope

- Playwright/E2E against the mounted admin UI.
- Publishing real tarballs anywhere.

## Files to create/modify

- `.github/workflows/ci.yml` (modify)
- `.gitignore` (only if `app/ui/local-registry/` is not yet ignored)

## Build/test commands

```bash
cd frontend && pnpm install
cd app/ui && pnpm install && pnpm pack:local && pnpm install
cd app/ui && pnpm test && pnpm build
make lint
```
