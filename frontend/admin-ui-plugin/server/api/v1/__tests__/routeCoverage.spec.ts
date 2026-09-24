import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// `__tests__/routeCoverage.spec.ts` lives at server/api/v1/__tests__/.
// Step one directory up so we scan server/api/v1/. Use both fileURLToPath
// (Node ESM) and import.meta.dirname (Node 20.11+) so the test is robust
// regardless of how vitest/esbuild rewrites import.meta.url.
function resolveHere(): string {
  const metaUrl = (import.meta as { url?: string }).url
  if (metaUrl && (metaUrl.startsWith('file://') || metaUrl.startsWith('file:'))) {
    return dirname(fileURLToPath(metaUrl))
  }
  const metaDirname = (import.meta as { dirname?: string }).dirname
  if (metaDirname) return metaDirname
  throw new Error('Cannot resolve current directory: import.meta.url is not a file URL')
}

const here = resolveHere()
const apiDir = join(here, '..')

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

type ExpectedProxy = {
  /** HTTP method exposed by the backend endpoint. */
  method: Method
  /** Backend path (the Spring Boot functional route). */
  backendPath: string
  /** BFF path (mounted under the Nitro /api/v1/ prefix). */
  bffPath: string
}

// Literal manifest of every backend `/api/dsl/*` and `/api/executions*` path
// the BFF must proxy. Source of truth:
//   backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/
//     - DslIntrospectionRouterConfiguration.java
//     - DslRuntimeRouterConfiguration.java
//     - DslReloadRouterConfiguration.java
//     - DslManifestRouterConfiguration.java
//     - DslExecutionsRouterConfiguration.java
//     - DslDiagnosticsRouterConfiguration.java
//     - ApiKeyAdminRouterConfiguration.java
//
// T561: plain-proxy entries are GENERATED from docs/openapi.json
// (`pnpm gen:bff-routes` → server/api/v1/generated/) — adding a backend route
// to a *RouterConfiguration and regenerating is enough; the drift guard below
// verifies the generated manifest covers every entry. Entries whose BFF route
// does more than plain proxying (query rewrites, SSE, CSV export, ...) stay
// explicit files and must keep an entry here.
const expectedProxies: readonly ExpectedProxy[] = [
  // DslIntrospectionRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/definitions/{name}/tests',
    bffPath: '/api/v1/dsl/definitions/{name}/tests',
  },
  {
    method: 'PUT',
    backendPath: '/api/dsl/definitions/{name}/tests',
    bffPath: '/api/v1/dsl/definitions/{name}/tests',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/definitions/{name}/tests/run',
    bffPath: '/api/v1/dsl/definitions/{name}/tests/run',
  },
  // DslDefinitionBundleRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/definitions/export',
    bffPath: '/api/v1/dsl/definitions/export',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/definitions/import',
    bffPath: '/api/v1/dsl/definitions/import',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/objects/search',
    bffPath: '/api/v1/dsl/objects/search',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/working-set',
    bffPath: '/api/v1/dsl/working-set',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/{type}/{name}/construct',
    bffPath: '/api/v1/dsl/{type}/{name}/construct',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/{type}/{name}/schema',
    bffPath: '/api/v1/dsl/{type}/{name}/schema',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/{type}/{name}/structure',
    bffPath: '/api/v1/dsl/{type}/{name}/structure',
  },
  // DslRuntimeRouterConfiguration
  {
    method: 'POST',
    backendPath: '/api/dsl/preview/{name}',
    bffPath: '/api/v1/dsl/preview/{name}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/run/{name}',
    bffPath: '/api/v1/dsl/run/{name}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/explain/{name}',
    bffPath: '/api/v1/dsl/explain/{name}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/hierarchy/{name}',
    bffPath: '/api/v1/dsl/hierarchy/{name}',
  },
  // DslSignalsRouterConfiguration
  {
    method: 'POST',
    backendPath: '/api/dsl/signals/{runId}',
    bffPath: '/api/v1/dsl/signals/{runId}',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/queries/{runId}',
    bffPath: '/api/v1/dsl/queries/{runId}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/save',
    bffPath: '/api/v1/dsl/drafts/{name}/save',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/publish',
    bffPath: '/api/v1/dsl/drafts/{name}/publish',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}/history',
    bffPath: '/api/v1/dsl/drafts/{name}/history',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/history/{timestamp}/restore',
    bffPath: '/api/v1/dsl/drafts/{name}/history/{timestamp}/restore',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}/history/{timestamp}',
    bffPath: '/api/v1/dsl/drafts/{name}/history/{timestamp}',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}/history/{timestamp}/diff',
    bffPath: '/api/v1/dsl/drafts/{name}/history/{timestamp}/diff',
  },
  {
    method: 'DELETE',
    backendPath: '/api/dsl/drafts/{name}',
    bffPath: '/api/v1/dsl/drafts/{name}/delete',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}/commits',
    bffPath: '/api/v1/dsl/drafts/{name}/commits',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/discard',
    bffPath: '/api/v1/dsl/drafts/{name}/discard',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts',
    bffPath: '/api/v1/dsl/drafts',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/metadata',
    bffPath: '/api/v1/dsl/drafts/metadata',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}',
    bffPath: '/api/v1/dsl/drafts/{name}',
  },
  // ChangeRequestRouterConfiguration (T568)
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/change-request',
    bffPath: '/api/v1/dsl/drafts/{name}/change-request',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/change-requests',
    bffPath: '/api/v1/dsl/change-requests',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/change-requests/{id}/approve',
    bffPath: '/api/v1/dsl/change-requests/{id}/approve',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/change-requests/{id}/reject',
    bffPath: '/api/v1/dsl/change-requests/{id}/reject',
  },
  // DslPromoteRouterConfiguration (T569 environment promotion)
  {
    method: 'GET',
    backendPath: '/api/dsl/promote/environments',
    bffPath: '/api/v1/dsl/promote/environments',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/promote/definitions',
    bffPath: '/api/v1/dsl/promote/definitions',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/promote',
    bffPath: '/api/v1/dsl/promote',
  },
  // DslScheduleRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/schedules',
    bffPath: '/api/v1/dsl/schedules',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/schedules',
    bffPath: '/api/v1/dsl/schedules',
  },
  {
    method: 'DELETE',
    backendPath: '/api/dsl/schedules/{definition}',
    bffPath: '/api/v1/dsl/schedules/{definition}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/schedules/{definition}/pause',
    bffPath: '/api/v1/dsl/schedules/{definition}/pause',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/schedules/{definition}/resume',
    bffPath: '/api/v1/dsl/schedules/{definition}/resume',
  },

  // DslReloadRouterConfiguration
  {
    method: 'POST',
    backendPath: '/api/dsl/reload',
    bffPath: '/api/v1/dsl/reload',
  },
  // DslManifestRouterConfiguration (T551 guard snapshot)
  {
    method: 'GET',
    backendPath: '/api/dsl/manifest/guard',
    bffPath: '/api/v1/dsl/manifest/guard',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/manifest/reload',
    bffPath: '/api/v1/dsl/manifest/reload',
  },
  // DslAuditRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/audit',
    bffPath: '/api/v1/dsl/audit',
  },
  // WebhookRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/webhooks/deliveries',
    bffPath: '/api/v1/dsl/webhooks/deliveries',
  },
  // NotificationRuleRouterConfiguration (T565)
  {
    method: 'GET',
    backendPath: '/api/dsl/notifications/rules',
    bffPath: '/api/v1/dsl/notifications/rules',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/notifications/rules',
    bffPath: '/api/v1/dsl/notifications/rules',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/notifications/rules/{id}',
    bffPath: '/api/v1/dsl/notifications/rules/{id}',
  },
  {
    method: 'PUT',
    backendPath: '/api/dsl/notifications/rules/{id}',
    bffPath: '/api/v1/dsl/notifications/rules/{id}',
  },
  {
    method: 'DELETE',
    backendPath: '/api/dsl/notifications/rules/{id}',
    bffPath: '/api/v1/dsl/notifications/rules/{id}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/notifications/rules/{id}/enabled',
    bffPath: '/api/v1/dsl/notifications/rules/{id}/enabled',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/notifications/channels',
    bffPath: '/api/v1/dsl/notifications/channels',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/notifications/fire-log',
    bffPath: '/api/v1/dsl/notifications/fire-log',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/notifications/test',
    bffPath: '/api/v1/dsl/notifications/test',
  },
  // DslDiagnosticsRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/diagnostics',
    bffPath: '/api/v1/dsl/diagnostics',
  },
  // DslEventRouterConfiguration (T411)
  {
    method: 'GET',
    backendPath: '/api/dsl/events',
    bffPath: '/api/v1/dsl/events',
  },
  // DryRunLogSseController (T563) — live dry-run log SSE stream
  {
    method: 'GET',
    backendPath: '/api/dsl/dry-run/{traceId}/logs',
    bffPath: '/api/v1/dsl/dry-run/{traceId}/logs',
  },

  // DslFileRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/files',
    bffPath: '/api/v1/dsl/files',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/files/by-name/{name}',
    bffPath: '/api/v1/dsl/files/by-name/{name}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/files/by-name/{name}',
    bffPath: '/api/v1/dsl/files/by-name/{name}',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/files/{*path}',
    bffPath: '/api/v1/dsl/files/{...path}',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/files/bulk',
    bffPath: '/api/v1/dsl/files/bulk',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/files/flush',
    bffPath: '/api/v1/dsl/files/flush',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/files/status',
    bffPath: '/api/v1/dsl/files/status',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/files/{*path}',
    bffPath: '/api/v1/dsl/files/{...path}',
  },
  // ApiKeyAdminRouterConfiguration (T503)
  {
    method: 'GET',
    backendPath: '/api/dsl/auth/keys',
    bffPath: '/api/v1/dsl/auth/keys',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/auth/keys',
    bffPath: '/api/v1/dsl/auth/keys',
  },
  {
    method: 'DELETE',
    backendPath: '/api/dsl/auth/keys/{id}',
    bffPath: '/api/v1/dsl/auth/keys/{id}',
  },

  // DslExecutionsRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/executions',
    bffPath: '/api/v1/executions',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/export.csv',
    bffPath: '/api/v1/executions/export',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/stats',
    bffPath: '/api/v1/executions/stats',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/stats/timeseries',
    bffPath: '/api/v1/executions/stats/timeseries',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/{id}',
    bffPath: '/api/v1/executions/{id}',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/{id}/transactions',
    bffPath: '/api/v1/executions/{id}/transactions',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/{id}/events',
    bffPath: '/api/v1/executions/{id}/events',
  },
  {
    method: 'POST',
    backendPath: '/api/executions/{id}/cancel',
    bffPath: '/api/v1/executions/{id}/cancel',
  },
]

// Nitro route filename: "<segments>.<method>.ts". Examples:
//   dsl/explain/[name].post.ts → POST /api/v1/dsl/explain/{name}
//   executions/index.get.ts    → GET  /api/v1/executions
const ROUTE_FILENAME = /^(?<route>.+)\.(?<method>get|post|put|patch|delete)\.ts$/

type DiscoveredRoute = {
  method: Method
  bffPath: string
  relFile: string
}

function discoverRoutes(dir: string): DiscoveredRoute[] {
  const out: DiscoveredRoute[] = discoverRouteFiles(dir)
  // T561: merge the OpenAPI-generated stubs (mounted by module.ts via
  // server/api/v1/generated/manifest.json) so they count as discovered routes.
  const manifestPath = join(apiDir, 'generated', 'manifest.json')
  if (existsSync(manifestPath)) {
    const manifest = JSON.parse(readFileSync(manifestPath, 'utf8')) as {
      routes: Array<{ method: Method; bffPath: string; handler: string }>
    }
    for (const route of manifest.routes) {
      out.push({
        method: route.method,
        bffPath: route.bffPath,
        relFile: join('generated', route.handler),
      })
    }
  }
  return out
}

function discoverRouteFiles(dir: string): DiscoveredRoute[] {
  const out: DiscoveredRoute[] = []
  for (const entry of readdirSync(dir)) {
    if (entry === '__tests__' || entry === 'generated') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      out.push(...discoverRoutes(full))
      continue
    }
    const m = ROUTE_FILENAME.exec(entry)
    if (!m?.groups) continue
    const method = m.groups.method.toUpperCase() as Method
    // Convert "[name]" segments to "{name}" and strip a trailing "index".
    const fileSegments = m.groups.route.split('/').map((s) => {
      const bracket = /^\[(?<name>.+)\]$/.exec(s)
      return bracket?.groups?.name ? `{${bracket.groups.name}}` : s
    })
    if (fileSegments[fileSegments.length - 1] === 'index') fileSegments.pop()
    // Combine the directory layout with the filename segments, converting
    // bracketed parent directory names (e.g. "[name]") into "{name}".
    const parentRel = relative(apiDir, dir)
      .split(sep)
      .filter((s) => s.length > 0)
      .map((s) => {
        const bracket = /^\[(?<name>.+)\]$/.exec(s)
        return bracket?.groups?.name ? `{${bracket.groups.name}}` : s
      })
    const allSegments = [...parentRel, ...fileSegments]
    const bffPath = `/api/v1/${allSegments.join('/')}`
    out.push({ method, bffPath, relFile: relative(apiDir, full) })
  }
  return out
}

describe('BFF proxy route coverage', () => {
  it('discovers at least one route file under server/api/v1/ (sanity check)', () => {
    const routes = discoverRoutes(apiDir)
    expect(routes.length).toBeGreaterThan(0)
  })

  it('manifest of expected backend proxies is non-empty', () => {
    expect(expectedProxies.length).toBeGreaterThan(0)
  })

  it('every backend /api/dsl/* + /api/executions* path has a matching BFF proxy route file', () => {
    const discovered = discoverRoutes(apiDir)
    const discoveredKeys = new Set(discovered.map((r) => `${r.method} ${r.bffPath}`))
    const missing = expectedProxies.filter((e) => !discoveredKeys.has(`${e.method} ${e.bffPath}`))
    if (missing.length > 0) {
      const lines = missing.map(
        (m) => `  - ${m.method.padEnd(4)} ${m.backendPath.padEnd(34)} → ${m.bffPath}`,
      )
      const discoveredSummary = discovered
        .map((r) => `${r.method} ${r.bffPath} (${r.relFile})`)
        .sort()
        .join('\n    ')
      throw new Error(
        `Missing BFF proxy route(s) for backend endpoint(s):\n${lines.join('\n')}\n\n` +
          `Create server/api/v1/<path>.{get,post}.ts using defineEventHandler + proxyToBackend(), ` +
          `add the matching entry to expectedProxies in this file, and add a unit test in routes.spec.ts.\n\n` +
          `Discovered BFF routes (for reference):\n    ${discoveredSummary}`,
      )
    }
    expect(missing).toEqual([])
  })

  it('every discovered BFF route under /api/v1/dsl/* or /api/v1/executions* is covered by the manifest (no orphans)', () => {
    // Reverse guard: if someone deletes a manifest entry by mistake, the
    // manifest-based check above would still pass. This catches that.
    const manifestBffPaths = new Set(expectedProxies.map((e) => e.bffPath))
    const orphans = discoverRoutes(apiDir).filter(
      (r) =>
        (r.bffPath.startsWith('/api/v1/dsl/') || r.bffPath.startsWith('/api/v1/executions')) &&
        !manifestBffPaths.has(r.bffPath),
    )
    if (orphans.length > 0) {
      const lines = orphans.map(
        (o) => `  - ${o.method.padEnd(4)} ${o.bffPath.padEnd(34)} (${o.relFile})`,
      )
      throw new Error(
        `BFF proxy route(s) found under /api/v1/dsl/* or /api/v1/executions* but not listed in expectedProxies:\n${lines.join('\n')}\n\n` +
          `Add the matching entry to expectedProxies in this file (mirror the backend path in the corresponding *RouterConfiguration.java).`,
      )
    }
    expect(orphans).toEqual([])
  })
})
