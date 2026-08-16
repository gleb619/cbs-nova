# CBS Nova — Operator Portal Host App

This is a reference Nuxt 3 host application that consumes the `@cbs/admin-ui-plugin` package from a local tarball registry, just like a real client integrating CBS Nova into their own admin portal.

## What it demonstrates

- A client Nuxt app with its own landing page and shell.
- The CBS Nova admin UI mounted under `/nova-admin` via the plugin module.
- Backend-for-frontend (BFF) routes merged automatically from the plugin.
- Local package distribution using `pnpm pack` (Maven-local equivalent for frontend).

## Setup

From the repository root:

```bash
cd app/ui
pnpm install
pnpm pack:local
pnpm install
```

`pnpm pack:local` packs `frontend/components` and `frontend/admin-ui-plugin` into `app/ui/local-registry/` and rewrites the host `package.json` to reference the fresh tarballs.

## Development

```bash
pnpm dev
```

The host app runs on [http://localhost:3000](http://localhost:3000).  
The CBS Nova admin UI is available at [http://localhost:3000/nova-admin](http://localhost:3000/nova-admin).

## Build

```bash
pnpm build
pnpm preview
```

## Updating the plugin

Whenever you change `frontend/components` or `frontend/admin-ui-plugin`, re-run:

```bash
pnpm pack:local
pnpm install
```

Then restart the dev server.
## Testing

```bash
pnpm test
```

Runs a minimal smoke test that mounts the host landing page and asserts the portal branding and the admin entry link are rendered.

## Architecture notes for client integrators

- The host app uses a **local tarball registry** (`local-registry/`) instead of a remote npm registry.
- Plugin pages, composables, and the shared component library use **explicit Vue/Nuxt imports** so they work reliably when consumed from `node_modules`.
- The plugin exposes its composables via package subpath exports (`@cbs/admin-ui-plugin/composables/*`) so host apps and plugin pages can import them without brittle relative paths.
- The plugin module registers `@cbs/components` with `pathPrefix: false` so the library's internal SFC references resolve correctly.
