# Admin UI Layout

This document describes the responsive admin-shell layout of the cbs-nova Vue/Nuxt frontend as implemented today: `app/layouts/admin.vue` renders the `AppShell` component from `@cbs/components` (`frontend/components/src/components/AppShell.vue`), which composes `AppSidebar`, `AppTopBar`, `AppMobileDrawer`, and `AppFooter`. It builds on the workspace structure in `docs/architecture-ui.md` and the Tailwind color system in `docs/colors.md`.

## Overview

The admin interface follows a sidebar + main-content pattern. The shell is a single `flex h-screen` container. On desktop (`md` and up), a collapsible sidebar sits beside the content. On mobile, the sidebar is hidden and becomes a slide-over drawer triggered from the top bar.

The layout relies on Tailwind CSS utility classes and standard breakpoints (`sm`, `md`, `lg`, `xl`). No custom JavaScript layout engine is required; visibility state is managed by the `useSidebar` composable (`components/src/composables/`).

## Desktop Layout

- The shell is a horizontal flex container filling the viewport height (`AppShell`: `flex h-screen`).
- The sidebar (`AppSidebar`) is `hidden md:flex` with widths `w-60` (~240 px) expanded, `w-16` collapsed, `w-0` fully hidden.
- To the right, a vertical flex column holds the top app bar and the scrollable `main` content area (`NuxtPage`).
- `main` scrolls independently while the top bar remains visible.

## Mobile Layout

- Below the `md` breakpoint the sidebar is hidden.
- `AppMobileDrawer` renders the sidebar as a fixed-position drawer (`w-64`, `z-40`) with a `z-30` semi-transparent backdrop (`md:hidden`).
- The drawer closes on backdrop tap, nav selection, or the close button; transitions use Tailwind `transition` utilities.

## Top App Bar

`AppTopBar` is a horizontal flex row slotted into `AppShell`. Its slots are filled by `admin.vue`:

1. **Toggle** — `AppSidebarToggle` (collapse/expand; sidebar can also be restored via `AppSidebarRestoreHandle` / hidden via `AppSidebarHideButton` in the sidebar itself).
2. **Brand** — "CBS Nova Admin" logo/brand linking back to the dashboard.
3. **Widgets** — `DslDraftsWidget` (saved DSL drafts menu) and `NotificationsWidget` (notification bell with unread count and a details drawer with mark-read / mark-all-read / dismiss).
4. **Trailing** — auth state from `useAuth`: sign-in/sign-out and the display name.

## Sidebar Navigation

The sidebar holds a single flat nav list (`navItems` in `admin.vue`, rendered by `AppNavItem`), not grouped sections. Each item shows a hotkey hint chip:

| Nav item | Route |
|----------|-------|
| Dashboard | `/` |
| DSL Workbench | `/dsl-workbench` |
| Runner | `/runner` |
| Schedules | `/schedules` |
| Activity | `/activity` |
| Webhooks | `/webhooks` |
| Notifications | `/notifications` |
| Approvals | `/approvals` |
| Promote | `/promote` |
| Executions | `/executions` |

The active item is styled with `bg-primary-500 text-white`; hover uses a neutral highlight. The sidebar surface is dark (`bg-neutral-800`).

## Footer

`AppFooter` (composed of `AppFooterCopyright`, `AppFooterGitInfo`, `AppFooterLinks`) sits at the bottom of the shell with build/git info from `useAdminInfo` (`/api/v1/info`) and a link to the documentation site (base URL via `buildDocsBaseUrl`).

## Global surfaces

- **Toasts** — `AppToastContainer` renders ephemeral notifications driven by the `useNotifications` toast store in `components/src/composables/useNotifications.ts`. (Note: `app/composables/useNotifications.ts` is a different, backend-facing composable for notification-rule CRUD used by the `/notifications` page.)
- **Drafts entry points** — the top-bar `DslDraftsWidget`, plus the workbench draft flow (`useWorkbenchDraft`, `DraftRestoreBanner`, `DraftsList`). `admin.vue` owns server-draft fetching via `useSavedDrafts` and dispatches `dsl:save` on Ctrl/Cmd+S.
- **Activity feed** — the `/activity` page hosting the `ActivityFeed` component. The other five operations screens (Schedules, Webhooks, Notifications, Approvals, Promote) are documented in [operations-pages.md](./operations-pages.md).

## Breakpoints

| Breakpoint | Width | Layout behavior |
|------------|-------|-----------------|
| `sm` | 640 px | Minor spacing adjustments. |
| `md` | 768 px | Sidebar becomes visible inline; mobile drawer hidden. |
| `lg` | 1024 px | Comfortable content padding; runner grid switches to two columns. |
| `xl` | 1280 px | Optional wider max-width for dashboards with large tables. |

## Z-Index Stacking

| Layer | Z-Index | Notes |
|-------|---------|-------|
| Main content | auto | Base scrolling layer. |
| Top app bar | sticky | Stays above content while scrolling. |
| Backdrop | 30 | Dims content when the mobile drawer is open. |
| Sidebar drawer | 40 | Sits above backdrop. |
| Dropdowns / drawers | 50+ | User menu, drafts, and notification drawers. |

## Accessibility

- The sidebar toggle has an accessible label.
- The mobile drawer traps focus while open and restores focus on close.
- Nav links use semantic `<NuxtLink>` elements.
- Sufficient color contrast is maintained per `docs/colors.md`.
