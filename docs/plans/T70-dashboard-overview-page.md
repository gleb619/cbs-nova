# T70 — Dashboard overview page

**Tier:** frontend
**Status:** Backlog
**Owner:** loop

## Goal

Turn the placeholder `pages/index.vue` dashboard into a minimal admin overview that surfaces key system state. Currently the dashboard is a blank welcome page; this page is the default landing route after login and should give users a useful at-a-glance summary.

## Acceptance Criteria

- [ ] Replace `frontend/admin-ui/app/pages/index.vue` with a `Dashboard` page that uses the default layout.
- [ ] Page loads counts/totals from existing BFF routes via `useDslApi()` and `useExecutionsApi()` (or new `/api/v1/executions/stats` if backend lacks aggregation).
- [ ] Display summary cards for: total DSL definitions, recent executions (last 24h), failed executions, currently running executions.
- [ ] Display a compact recent-executions table (latest 5) with entity, mode, status, and started-at columns, linking to `/executions/[id]`.
- [ ] Handle loading and error states per existing patterns.
- [ ] No new backend work required if aggregation is missing; fall back to fetching lists and computing client-side.
- [ ] `pnpm install && pnpm --filter admin-ui lint` passes.

## Files to Create / Modify

- `frontend/admin-ui/app/pages/index.vue` — rewrite.
- `frontend/admin-ui/app/components/dashboard/StatCard.vue` — new small presentational component.
- `frontend/admin-ui/app/components/dashboard/RecentExecutions.vue` — new component for the recent table.
- `frontend/admin-ui/app/types/dashboard.ts` — optional type file for stat shapes.

## Build / Test Commands

```bash
cd frontend
pnpm install
pnpm --filter admin-ui lint
pnpm --filter admin-ui build
```
