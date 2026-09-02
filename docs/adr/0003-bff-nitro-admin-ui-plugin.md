# 0003. Ship the admin UI's BFF as a Nitro layer inside the Nuxt module

- **Status:** Accepted
- **Date:** 2026-09-02 (retroactive — records a foundational decision)

## Context

`@cbs/admin-ui-plugin` is a Nuxt module that mounts the entire cbs-nova admin UI (Dashboard,
Runner, DSL Workbench, Executions) into an arbitrary host Nuxt app. The UI needs to reach the
Spring Boot API, but the browser must not call Spring Boot directly: it needs a JWT it should not
hold, header shaping (`X-Api-Key`, `X-Request-Id`, `traceparent`, `Idempotency-Key`,
`X-Correlation-Id`), OIDC session/refresh handling, and response translation.

Options considered:

- **A separate BFF service** (its own deployable Node process) — clean separation, but it is a
  second thing to build, version, deploy, and keep in sync with the plugin. A host that adopts the
  module would have to also stand up and route to this service. High adoption friction for a plugin
  whose selling point is "add one module".
- **Call Spring Boot from the browser with CORS + a public token** — no BFF at all, but it leaks
  the token to the browser, needs CORS on the backend, and pushes auth/refresh logic into client
  code.
- **A Nitro server layer merged into the host** — the module ships a `server/` directory that Nuxt
  merges into the host's own Nitro server, so `/api/v1/**` is served by the host process. No extra
  deployable; the BFF's lifecycle *is* the host's lifecycle.

## Decision

We will ship the BFF as a **Nitro `server/` directory inside `@cbs/admin-ui-plugin`**, merged into
the host Nuxt app at module-activation time. Routes are **explicit Nitro files** under
`server/api/v1/` — there is deliberately no generic `/api/v1/dsl/*` catch-all; each backend path
exposed to the UI gets a matching proxy route (the convention is recorded in `frontend/CLAUDE.md`).
The JWT and OIDC session live server-side; the browser holds only its own session cookie.

## Consequences

**Positive**

- Adopting the admin UI is "add one module" — no second service to deploy or route to.
- The token never reaches the browser; auth, refresh-on-401, and header allowlisting are enforced
  in one place (`server/utils/httpClient.ts`, `server/utils/oidcSession.ts`).
- The BFF scales and deploys exactly with its host; no independent version skew.

**Negative**

- Every new backend endpoint the UI needs requires a hand-written Nitro proxy route; forgetting one
  is a silent 404 at runtime. `routeCoverage.spec.ts` exists to catch this.
- The BFF cannot be scaled or deployed independently of the host, and it inherits the host's
  Node/Nitro runtime constraints.
- BFF logic is coupled to Nuxt/Nitro; extracting it later (if a non-Nuxt host ever needs it) is a
  real migration.

**Neutral**

- Local standalone development uses `nuxt.config.dev.ts`, which loads the module directly so the
  BFF runs without a separate host app.
