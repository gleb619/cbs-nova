# Admin UI Layout

This document defines the responsive admin-shell layout for the cbs-nova Vue/Nuxt frontend. It builds on the workspace structure described in `docs/architecture-ui.md` and the Tailwind color system in `docs/colors.md`.

## Overview

The admin interface follows a common sidebar + main-content pattern. The shell is a single flex column that fills the viewport. On desktop, a collapsible sidebar sits beside the content. On mobile, the sidebar becomes a slide-over drawer triggered from a sticky top bar.

The layout relies on Tailwind CSS utility classes and standard breakpoints (`sm`, `md`, `lg`, `xl`). No custom JavaScript layout engine is required.

## Desktop Layout

- The shell is a horizontal flex container (`flex`) that fills `100vh`.
- A left sidebar has a default width of `~240px` (`w-60`).
- The sidebar can be collapsed to a narrow rail (`w-16`) or hidden entirely via a state toggle.
- To the right of the sidebar, a vertical flex column holds the sticky top app bar and the scrollable `main` content area.
- `main` uses `flex-1 overflow-auto` so page content scrolls independently while the top bar remains visible.

### Desktop ASCII Diagram

```
+-------------------------------------------------------------+
|  SIDEBAR  |  TOP APP BAR (sticky)                          |
|  ~240px   |  [logo]  Page title        [actions] [user ▼]  |
|           +-------------------------------------------------+
|           |                                                 |
|  Nav      |  MAIN CONTENT                                   |
|  items    |  (scrollable)                                   |
|           |                                                 |
|           |  +-------------------------------------------+  |
|           |  |  Cards / tables / forms                   |  |
|           |  +-------------------------------------------+  |
|           |                                                 |
|           |  +-------------------------------------------+  |
|           |  |  More content...                          |  |
|           |  +-------------------------------------------+  |
|           |                                                 |
+-----------+-------------------------------------------------+
```

## Mobile Layout

- Below the `md` breakpoint, the sidebar is hidden by default.
- A sticky top bar spans the full width and contains a hamburger toggle on the left.
- Tapping the hamburger opens the sidebar as a slide-over drawer from the left.
- The drawer overlays the content with a semi-transparent backdrop and closes when the backdrop is tapped, a nav item is selected, or the close button is pressed.
- The `main` area remains scrollable underneath the fixed top bar.

### Mobile ASCII Diagram

```
Closed drawer:
+-----------------------------+
|  ☰  Page title    [actions] |
+-----------------------------+
|                             |
|  MAIN CONTENT               |
|  (scrollable)               |
|                             |
|  +-----------------------+  |
|  |  Cards / tables ...   |  |
|  +-----------------------+  |
|                             |
+-----------------------------+

Open drawer:
+-----------------------------+
|  ☰  Page title    [actions] |  <- top bar stays sticky
+-----------------------------+
|+-----------+                |
||  SIDEBAR  |  ▓▓▓▓▓▓▓▓▓▓▓▓  |
||  (drawer) |  ▓ backdrop ▓  |
||  Nav      |  ▓▓▓▓▓▓▓▓▓▓▓▓  |
||  items    |                |
|+-----------+                |
|  MAIN CONTENT               |
|  (scrollable)               |
+-----------------------------+
```

## Top App Bar

The top app bar is a single horizontal flex row, sticky at the top (`sticky top-0 z-10`), with a neutral or white surface background.

From left to right:

1. **Hamburger toggle** — visible on mobile only (`md:hidden`) to open the slide-over drawer.
2. **Logo / brand mark** — compact, links back to the dashboard.
3. **Page title** — current view name, left-aligned.
4. **Spacer** — `flex-1` to push global actions to the right.
5. **Global actions** — contextual buttons such as "New workflow", "Refresh", notifications.
6. **User menu** — avatar or name with a dropdown for profile, settings, and sign-out.

Suggested Tailwind classes:

- Height: `h-16`
- Background: `bg-white` or `bg-neutral-50`
- Border: `border-b border-neutral-200`
- Padding: `px-4`

## Sidebar

The sidebar holds primary navigation grouped by section.

### Desktop Behavior

- Width: `w-60` (~240 px) expanded, `w-16` collapsed.
- Background: `bg-white` or `bg-neutral-50`.
- Border: `border-r border-neutral-200`.
- Toggle control in the top bar or sidebar footer collapses/expands the sidebar.
- Collapsed state shows icon-only navigation with tooltips.

### Mobile Behavior

- Hidden by default.
- Rendered as a fixed-position drawer (`fixed inset-y-0 left-0 z-40`).
- Width: `w-64`.
- Backdrop: `fixed inset-0 z-30 bg-neutral-900/50`.
- Enter/exit transitions are handled with Tailwind `transition` utilities.

## Main Content Area

- `main` is the scrollable region for page content.
- It uses `flex-1 overflow-auto` inside the vertical flex column to the right of the sidebar.
- Internal pages use a content wrapper with consistent padding (`p-4 md:p-6` or `p-6 lg:p-8`).
- Page width is fluid; max-width containers are optional per view.

## Breakpoints

Use Tailwind’s default breakpoints for the responsive behavior:

| Breakpoint | Width   | Layout behavior                                              |
|------------|---------|--------------------------------------------------------------|
| `sm`       | 640 px  | Minor spacing adjustments.                                   |
| `md`       | 768 px  | Sidebar becomes visible inline; hamburger hidden.            |
| `lg`       | 1024 px | Comfortable content padding, sidebar fully expanded.         |
| `xl`       | 1280 px | Optional wider max-width for dashboards with large tables.   |

## Z-Index Stacking

| Layer        | Z-Index | Notes                                      |
|--------------|---------|--------------------------------------------|
| Main content | auto    | Base scrolling layer.                      |
| Top app bar  | 10      | Sticky header stays above content.         |
| Backdrop     | 30      | Dims content when mobile drawer is open.   |
| Sidebar drawer | 40    | Sits above backdrop and top app bar.       |
| Dropdowns    | 50      | User menu and action menus.                |

## Color Application

Apply the palette from `docs/colors.md` as follows:

- **Page background:** `bg-background` (`#f8f8f6`).
- **Top bar / sidebar:** `bg-white` with `border-neutral-200`.
- **Active nav item:** `bg-primary-100 text-primary-700` or `text-primary-600`.
- **Hover nav item:** `bg-neutral-100 text-neutral-800`.
- **Backdrop:** `bg-neutral-900/50`.

## Accessibility

- The hamburger toggle has an accessible label.
- The mobile drawer traps focus while open and restores focus on close.
- Nav links use semantic `<a>` or `<NuxtLink>` elements.
- Sufficient color contrast is maintained per `docs/colors.md`.
