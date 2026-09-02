# BFF↔backend contract deltas

This file documents intentional shape differences between the raw backend
response and what the BFF returns. Paths are stripped from both shapes
before comparison in `contract.spec.ts`.

## 1. Fixture provenance

Fixtures were hand-derived from the backend DTOs on **2026-09-02**. Refresh
them against a running backend (started with `SERVER_PORT=8090`) using:

```bash
# /api/v1/dsl/definitions  → backend /api/dsl/definitions
curl -s http://localhost:8090/api/dsl/definitions > definitions.json

# /api/v1/dsl/helpers      → backend /api/dsl/helpers
curl -s http://localhost:8090/api/dsl/helpers > helpers.json

# /api/v1/dsl/drafts       → backend /api/dsl/drafts
curl -s http://localhost:8090/api/dsl/drafts > drafts.json

# /api/v1/executions       → backend /api/executions
curl -s http://localhost:8090/api/executions > executions.json

# /api/v1/executions/stats → backend /api/executions/stats
curl -s http://localhost:8090/api/executions/stats > executions-stats.json
```

Pretty-print for readability:

```bash
for f in definitions.json helpers.json drafts.json executions.json executions-stats.json; do
  jq . "$f" > "${f}.tmp" && mv "${f}.tmp" "$f"
done
```

## 2. Intentional deltas

| Route | Path to strip | Reason | Added |
|-------|--------------|--------|-------|
| —     | —            | none   | —     |

All five covered routes are currently pure pass-through (`return proxyToBackend(event, path)`),
so no deltas are required. Future row example:

| Route | Path to strip | Reason | Added |
|-------|--------------|--------|-------|
| `GET /api/v1/executions` | `items[].internalTraceId` | BFF strips internal tracing fields | 2026-09-02 |

## 3. Scope note

These tests assert **JSON shape only**: object keys, array element shape, and
primitive `typeof`. They do **not** assert value-level or semantic
correctness (timestamps, IDs, counts, ordering of unordered maps). Value
drift should be covered by dedicated functional tests or by comparing live
backend fixtures during a refresh cycle.

## 4. Growth path

To add a new route to the contract suite:

1. Capture or hand-derive a canonical backend response as
   `contract/fixtures/<route>.json`.
2. Add a row to the `routes` table in `contract.spec.ts` with the BFF handler
   import, the fixture, and any delta paths.
3. If the BFF intentionally transforms the body, document the delta in section
   2 with the JSON-path string and the reason.
4. Run `pnpm --filter @cbs/admin-ui-plugin test` to confirm the new contract
   pins the shape.
