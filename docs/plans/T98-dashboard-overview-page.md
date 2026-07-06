# T98 — Dashboard overview page

## Goal
Replace the placeholder `frontend/admin-ui/app/pages/index.vue` with a real dashboard landing page: summary cards for registered processes/transactions/helpers and a recent executions table. Uses existing BFF endpoints so no backend work is required.

## Tier
frontend

## Files to create / modify
- Modify: `frontend/admin-ui/app/pages/index.vue`
- Create: `frontend/admin-ui/app/components/dashboard/StatCard.vue`
- Create: `frontend/admin-ui/app/components/dashboard/RecentExecutions.vue`
- Read only: `frontend/admin-ui/app/composables/useDslApi.ts`, `frontend/admin-ui/app/composables/useExecutions.ts`

## Acceptance criteria
- Dashboard fetches process/transaction/helper counts via `/api/v1/dsl/definitions` and recent executions via `/api/v1/executions`.
- Summary cards display counts with labels and link to the relevant pages.
- Recent executions table lists the last N executions with status, name, and timestamp; clicking a row navigates to `/executions/[id]`.
- Page uses Tailwind and the shared color system; no hard-coded colors.
- Existing `pnpm dev`/`pnpm build` still pass.

## Build / test commands
```bash
cd frontend
pnpm install
pnpm --filter admin-ui build
```
