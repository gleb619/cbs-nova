# `@cbs/admin-ui-plugin`

A Nuxt module that mounts the full CBS Nova admin UI (dashboard, DSL workbench,
runner, executions) into any host Nuxt application. It bundles its own Nitro
backend-for-frontend (BFF) that proxies requests from the browser to the
Spring Boot DSL orchestrator, transparently forwarding headers (API key,
trace context) and translating timeouts into a stable `504 BACKEND_TIMEOUT`
response. The module auto-registers Pinia, the `cbs-admin` shell layout, the
admin pages under a configurable route prefix, the global Tailwind stylesheet,
and all BFF routes — so a host app only needs to add it to `modules:` and
(optionally) override `adminUiPlugin` options.

## Install & setup

### 1. Add the dependency

In your host Nuxt app's `package.json`:

```json
{
  "dependencies": {
    "@cbs/admin-ui-plugin": "*"
  }
}
```

Then install with your package manager (`pnpm install`, `npm install`, etc.).

> In the cbs-nova monorepo the plugin is consumed as a workspace package; the
> `app/ui` reference host uses `pnpm-workspace.yaml` to resolve it.

### 2. Register the module

In your host `nuxt.config.ts`:

```ts
export default defineNuxtConfig({
  modules: ['@cbs/admin-ui-plugin'],
})
```

That's enough to mount the UI under `/` and proxy `/api/v1/**` to
`http://localhost:8090` (the BFF default).

### 3. Minimum viable configuration

If the Spring Boot backend runs on a non-default URL or requires an API key,
override the defaults:

```ts
export default defineNuxtConfig({
  modules: ['@cbs/admin-ui-plugin'],

  adminUiPlugin: {
    backendBaseUrl: process.env.BACKEND_BASE_URL || 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY,
  },
})
```

For monorepo / workspace consumers that import the plugin via TypeScript
sources, also enable transpilation so Nitro and Vite can resolve plugin code:

```ts
export default defineNuxtConfig({
  modules: ['@cbs/admin-ui-plugin'],

  build: { transpile: ['@cbs/admin-ui-plugin'] },
  nitro: { transpile: ['@cbs/admin-ui-plugin'] },
})
```

## ModuleOptions reference

All options are read by the `adminUiPlugin` config key in `nuxt.config.ts`.
Defaults are resolved once at module setup; later changes require a dev-server
restart.

| Option | Type | Default | Env-var fallback | Description |
|--------|------|---------|------------------|-------------|
| `routePrefix` | `string` | `'/'` | — | URL prefix for all admin UI pages. With `'/'`, pages mount at `/`, `/runner`, `/dsl-workbench`, `/executions`, `/executions/:id`. Set to e.g. `'/admin'` to mount them at `/admin`, `/admin/runner`, etc. The trailing slash is stripped automatically. |
| `backendBaseUrl` | `string` | `'http://localhost:8090'` | `BACKEND_BASE_URL` | Spring Boot backend base URL. Server-side only — never exposed to the client. Used by every BFF proxy route as the upstream origin. |
| `backendApiKey` | `string` | `''` | `BACKEND_API_KEY` | Optional API key forwarded to the backend as the `X-Api-Key` header on every proxied request. Server-side only. Leave empty to skip. |
| `backendTimeoutMs` | `number` | `10000` | `BACKEND_TIMEOUT_MS` | Outbound request timeout in milliseconds for BFF → backend calls. When exceeded, the BFF returns `504 BACKEND_TIMEOUT` so the UI can distinguish upstream timeouts from 5xx errors. |
| `appName` | `string` | `'CBS Nova Admin'` | — | Display name shown in the admin UI title bar. Surfaced as `runtimeConfig.public.appName`. |

Resolution order for each option: explicit `adminUiPlugin` value → env-var
fallback → built-in default. If a host app already set the matching
`runtimeConfig.backendBaseUrl` / `backendApiKey` / `backendTimeoutMs` /
`runtimeConfig.public.appName`, the module preserves those values rather than
overwriting them.

## BFF proxy route inventory

All BFF routes live under `frontend/admin-ui-plugin/server/api/v1/` and are
merged into the host Nitro build at module setup time. There is **no generic
catch-all** — each route maps to an explicit Nitro file. Add a new file here
when exposing a new backend DSL path.

| BFF route | Method | Backend path | Source file |
|-----------|--------|--------------|-------------|
| `/api/v1/health` | GET | _(BFF-only — no upstream call)_ | `server/api/v1/health.get.ts` |
| `/api/v1/info` | GET | `/actuator/info` | `server/api/v1/info.get.ts` |
| `/api/v1/dsl/definitions` | GET | `/api/dsl/definitions` | `server/api/v1/dsl/definitions.get.ts` |
| `/api/v1/dsl/reload` | POST | `/api/dsl/reload` | `server/api/v1/dsl/reload.post.ts` |
| `/api/v1/dsl/objects/search` | GET | `/api/dsl/objects/search` (forwards `name`, `type`, `description` query params) | `server/api/v1/dsl/objects/search.get.ts` |
| `/api/v1/dsl/helpers` | GET | `/api/dsl/helpers` | `server/api/v1/dsl/helpers/index.get.ts` |
| `/api/v1/dsl/processes` | GET | `/api/dsl/processes` | `server/api/v1/dsl/processes/index.get.ts` |
| `/api/v1/dsl/processes/:name` | GET | `/api/dsl/processes/:name` | `server/api/v1/dsl/processes/[name].get.ts` |
| `/api/v1/dsl/transactions` | GET | `/api/dsl/transactions` | `server/api/v1/dsl/transactions/index.get.ts` |
| `/api/v1/dsl/transactions/:name` | GET | `/api/dsl/transactions/:name` | `server/api/v1/dsl/transactions/[name].get.ts` |
| `/api/v1/dsl/explain/:name` | POST | `/api/dsl/explain/:name` (forwards JSON body) | `server/api/v1/dsl/explain/[name].post.ts` |
| `/api/v1/dsl/preview/:name` | POST | `/api/dsl/preview/:name` (forwards JSON body) | `server/api/v1/dsl/preview/[name].post.ts` |
| `/api/v1/dsl/run/:name` | POST | `/api/dsl/run/:name` (forwards JSON body) | `server/api/v1/dsl/run/[name].post.ts` |
| `/api/v1/executions` | GET | `/api/executions` (forwards `offset`, `limit` query params) | `server/api/v1/executions/index.get.ts` |
| `/api/v1/executions/:id` | GET | `/api/executions/:id` | `server/api/v1/executions/[id].get.ts` |

Every proxied route (except `/api/v1/health`) calls `proxyToBackend` in
`server/utils/httpClient.ts`, which:

- Forwards `Content-Type: application/json` and (when configured) `X-Api-Key`.
- Propagates `traceparent` and generates / forwards `X-Request-Id` for log
  correlation across BFF and backend.
- Forwards the inbound `Authorization` header verbatim (inbound-only — never
  fabricated; absent means no header is sent). This is what keeps the UI
  working when the Spring backend is started with
  `cbs.security.oidc.enabled=true` and a Keycloak JWT is presented by the
  browser session.
- Applies the `backendTimeoutMs` timeout and converts aborts to a
  `504 BACKEND_TIMEOUT` response with a stable `code` so the UI can branch on
  it without parsing upstream error shapes.

### Running against an OIDC-secured backend

The Spring backend ships an opt-in OIDC resource-server guard
(`cbs.security.oidc.enabled=true`) that validates JWTs against the compose
Keycloak realm. The BFF itself does **not** validate tokens — it just passes
the inbound `Authorization` header through to the upstream call, so the
browser must hold a valid Keycloak token and send it on every request to
`/api/v1/**`.

Minimal local workflow (T275-aligned):

1. Boot Keycloak + Postgres + Temporal from `app/docker-compose.yml`
   (Keycloak auto-provisions the CBS realm and a test user on first start —
   see `app/compose/keycloak/README.md` for the bootstrap credentials).
2. Start the Spring backend with OIDC enabled:
   ```bash
   SERVER_PORT=8090 \
   CBS_SECURITY_OIDC_ENABLED=true \
   backend/dsl-platform/gradlew -p backend/dsl-platform :starter-launcher:bootRun -x test
   ```
3. Obtain an access token from Keycloak (e.g. via the realm's `token` endpoint
   or the account console) and store it in the browser session — for example
   with a small dev-only `useState` hook or the browser devtools
   `Authorization: Bearer <token>` header override on the first request.
4. Start the Nuxt dev server normally (`pnpm dev`). Each subsequent request
   the browser makes to `/api/v1/**` will be proxied with the token attached
   and validated by the backend.

No plugin-side login or refresh flow is provided — the BFF is a pass-through
proxy. Token lifecycle (acquire, refresh, store) is the caller's
responsibility.

## Local development workflow

The frontend and backend run as separate processes. The BFF defaults to
`http://localhost:8090` for the backend, but Spring Boot defaults to port
`8080` — so you must override the backend port (or the BFF `BACKEND_BASE_URL`)
to make the two line up.

### 1. Start Postgres + Temporal (backend dependencies)

```bash
docker compose -f app/docker-compose.yml up -d postgres
```

### 2. Publish the DSL platform to Maven Local

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform publishToMavenLocal -x test
```

### 3. Start Spring Boot on the BFF-expected port

```bash
SERVER_PORT=8090 backend/dsl-platform/gradlew -p backend/dsl-starter :starter-launcher:bootRun -x test
```

> **Port mismatch caveat** — Spring Boot's default port is `8080`, but the BFF
> defaults to `http://localhost:8090`. Either start the backend with
> `SERVER_PORT=8090` (as above) or override the frontend side by setting
> `BACKEND_BASE_URL=http://localhost:8080` in the environment where you run
> `pnpm dev`. The two must agree or every proxied request will fail.

### 4. Start the Nuxt dev server

```bash
cd frontend
pnpm dev
```

By default the dev server listens on `http://localhost:3000`. The admin UI is
mounted at `/` and the BFF is reachable at `/api/v1/**`.

### 5. Smoke-test a proxied endpoint

```bash
curl http://localhost:3000/api/v1/dsl/definitions
```

A successful response confirms the BFF is wired up to the Spring Boot backend.

### Running the plugin standalone

`frontend/admin-ui-plugin` ships with a development-only Nuxt config
(`nuxt.config.dev.ts`) so you can iterate on the plugin in isolation:

```bash
cd frontend/admin-ui-plugin
pnpm dev   # NUXT_CONFIG_FILE=nuxt.config.dev.ts nuxt dev --cwd .
```

In production the same plugin is consumed via `modules: ['@cbs/admin-ui-plugin']`
inside a host Nuxt app — there is no separate "plugin dev server" requirement
for downstream consumers.

## Host-app example

The reference consumer is `app/ui` at the repository root. Its `nuxt.config.ts`
demonstrates non-default configuration choices that you may want to copy when
mounting the plugin under a sub-path or rebranding the title bar:

```ts
// app/ui/nuxt.config.ts
export default defineNuxtConfig({
  compatibilityDate: '2024-04-03',
  devtools: { enabled: true },
  pages: true,

  modules: ['@cbs/admin-ui-plugin'],

  adminUiPlugin: {
    routePrefix: '/nova-admin',
    appName: 'CBS Nova — Operator Portal',
    backendBaseUrl: process.env.BACKEND_BASE_URL || 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY,
  },

  runtimeConfig: {
    public: {
      portalName: 'CBS Operator Portal',
    },
  },

  postcss: {
    plugins: {
      tailwindcss: {},
      autoprefixer: {},
    },
  },

  build: { transpile: ['@cbs/admin-ui-plugin'] },
  nitro: { transpile: ['@cbs/admin-ui-plugin'] },
})
```

### Non-default choices made by `app/ui`

- **`routePrefix: '/nova-admin'`** — mounts the admin pages at
  `/nova-admin`, `/nova-admin/runner`, `/nova-admin/dsl-workbench`,
  `/nova-admin/executions`, `/nova-admin/executions/:id`. Useful when the host
  app has its own routes at `/` and the admin UI must coexist on a sub-path.
- **`appName: 'CBS Nova — Operator Portal'`** — overrides the title-bar name
  surfaced via `runtimeConfig.public.appName`. The host can read it at runtime
  with `useRuntimeConfig().public.appName`.
- **`backendBaseUrl` / `backendApiKey` read from `process.env`** — the host
  defers to env-var fallbacks rather than hard-coding values, so the same
  `app/ui` build works across local dev, CI, and production with no
  recompilation.
- **`build.transpile` + `nitro.transpile`** — required because `app/ui`
  imports the plugin as workspace TypeScript sources (rather than a pre-built
  `dist/`) and Vite / Nitro need the hint to transpile plugin code on the fly.
- **`runtimeConfig.public.portalName`** — a host-specific public value that
  lives alongside the plugin's `public.appName`. The two are independent and
  serve different UIs.

The `app/ui` package also pins the standard `postcss.tailwindcss` /
`postcss.autoprefixer` plugins. The plugin's own `tailwind.config.ts`
already scans both its pages and the shared `@cbs/components` SFCs, so hosts
do not need to extend the content glob unless they want plugin styles to
match additional directories.
