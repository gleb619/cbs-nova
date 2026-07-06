# T90 — CI frontend build

## Goal
Extend the existing GitHub Actions CI workflow to build the Nuxt admin UI so frontend breakage is caught on every push and pull request. Keeps the backend build/test steps intact and adds a parallel or sequential frontend job.

## Tier
backend / dx

## Files to create / modify
- Modify: `.github/workflows/ci.yml`
- Read only: `frontend/package.json`, `frontend/admin-ui/package.json`, `frontend/pnpm-workspace.yaml`

## Acceptance criteria
- CI installs pnpm (via `pnpm/action-setup`) with version matching `packageManager` (`pnpm@9.0.0`).
- CI runs `pnpm install` and `pnpm build` from `frontend/`.
- CI continues to run backend build/test steps.
- Workflow still triggers on `push` and `pull_request`.
- `./gradlew` backend steps remain unchanged and green.

## Build / test commands
```bash
# Local verification of the frontend build step
cd frontend
pnpm install
pnpm build
```

## Notes
- If `pnpm build` is too slow for CI, a separate `frontend` job running in parallel is acceptable.
- Do not add frontend tests until T67 (Vitest scaffold) lands; this fire is build-only.
