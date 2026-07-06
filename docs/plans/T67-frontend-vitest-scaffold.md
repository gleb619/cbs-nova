# T67 — Frontend Vitest test scaffold

## Goal
Add a frontend unit-test baseline to `frontend/admin-ui` using Vitest + `@vue/test-utils` + `happy-dom`. Configure `vitest.config.ts`, wire Nuxt auto-imports, add workspace test scripts, and write initial tests for composables (`useRunner`, `useDslWorkbench`, `useSidebar`) plus a smoke test for one core component. This enables all deferred frontend test and DX tasks.

## Tier
frontend

## Files to create / modify
- Create: `frontend/admin-ui/vitest.config.ts`
- Create: `frontend/admin-ui/app/composables/__tests__/useSidebar.spec.ts`
- Create: `frontend/admin-ui/app/components/__tests__/AppNavItem.spec.ts`
- Modify: `frontend/admin-ui/package.json` — add test scripts and dev dependencies
- Modify: `frontend/package.json` — add root test script

## Acceptance criteria
- `pnpm --filter admin-ui test` runs Vitest in `frontend/admin-ui`.
- `useSidebar` test verifies `collapsed` toggle and mobile open/close.
- `AppNavItem` test renders link with label and active class.
- Existing `pnpm dev`/`pnpm build` still work.
- `pnpm --filter admin-ui lint` passes (or lint command is added if missing).

## Build / test commands
```bash
cd frontend
pnpm install
pnpm --filter admin-ui test
pnpm --filter admin-ui lint
```
