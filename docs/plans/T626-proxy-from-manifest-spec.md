# T626 — proxyFromManifest unit spec

## Goal

Add unit spec for `frontend/admin-ui-plugin/server/utils/proxyFromManifest.ts` (52 LOC) — the
helper behind every generated BFF proxy route (~50 routes under
`server/api/v1/generated/routes/`).

## Why

All BFF API traffic flows through this util. Every sibling util in `server/utils/` has a dedicated
spec (`backendHeaders`, `config`, `httpClient`, `logout`, `moduleRuntimeConfig`, `oidcSession`) —
`proxyFromManifest` has only indirect coverage via `manifestRouteRegistration.spec.ts`.

## Acceptance criteria

- [ ] Happy path: valid manifest entry → proxy request built with correct backend URL, method,
      path params, query, headers.
- [ ] Path interpolation: route params substituted correctly; missing param behavior pinned.
- [ ] Error paths: missing manifest entry, unreachable backend base URL — pinned per current
      behavior (error envelope shape).
- [ ] Test style matches sibling specs in `server/utils/__tests__/` (same mocking approach).
- [ ] `make lint` (Biome) passes; spec green under existing FE test runner.

## Tier

`frontend`

## Files to create/modify

- Create: `frontend/admin-ui-plugin/server/utils/__tests__/proxyFromManifest.spec.ts`
- Read first: `proxyFromManifest.ts`, one generated route consumer, sibling spec for style
      (e.g. `backendHeaders.spec.ts`).

## Build/test commands

```bash
cd frontend && pnpm test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no util changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
