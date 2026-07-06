# T76 — Fix mobile drawer DSL Workbench nav link

**Tier:** frontend
**Status:** Backlog
**Owner:** loop

## Goal

`AppMobileDrawer.vue` links to `/workbench` while `AppSidebar.vue` links to `/dsl-workbench`. The actual Nuxt page is `/dsl-workbench`. On mobile, tapping DSL Workbench leads to a 404. Fix the link and extract a single source of truth for nav items to prevent future drift.

## Acceptance Criteria

- [ ] `AppMobileDrawer.vue` nav item `to` updated from `/workbench` to `/dsl-workbench`.
- [ ] Create `frontend/admin-ui/app/config/navigation.ts` with a shared `NAV_ITEMS` array.
- [ ] Update `AppSidebar.vue` and `AppMobileDrawer.vue` to import and use `NAV_ITEMS`.
- [ ] `pnpm --filter admin-ui lint` and `pnpm --filter admin-ui build` pass.
- [ ] (Optional) Add a type for nav item `{ to: string, label: string, icon?: string }`.

## Files to Create / Modify

- `frontend/admin-ui/app/config/navigation.ts` — new.
- `frontend/admin-ui/app/components/AppSidebar.vue` — replace inline navItems.
- `frontend/admin-ui/app/components/AppMobileDrawer.vue` — replace inline navItems and fix link.

## Build / Test Commands

```bash
cd frontend
pnpm install
pnpm --filter admin-ui lint
pnpm --filter admin-ui build
```
