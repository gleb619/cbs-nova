import { readdirSync, statSync } from 'node:fs'
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

type Method = 'GET' | 'POST' | 'DELETE'

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
//     - DslExecutionsRouterConfiguration.java
//
// When a new backend route is added under `/api/dsl/*` or `/api/executions*`,
// add a matching entry here AND a Nitro proxy file under server/api/v1/.
// This drift-guard fails CI when one is added without the other.
const expectedProxies: readonly ExpectedProxy[] = [
  // DslIntrospectionRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/dsl/definitions',
    bffPath: '/api/v1/dsl/definitions',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/objects/search',
    bffPath: '/api/v1/dsl/objects/search',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/helpers',
    bffPath: '/api/v1/dsl/helpers',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/processes',
    bffPath: '/api/v1/dsl/processes',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/processes/{name}',
    bffPath: '/api/v1/dsl/processes/{name}',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/processes/{name}/diagram',
    bffPath: '/api/v1/dsl/processes/{name}/diagram',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/transactions',
    bffPath: '/api/v1/dsl/transactions',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/transactions/{name}',
    bffPath: '/api/v1/dsl/transactions/{name}',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/constructs/{name}',
    bffPath: '/api/v1/dsl/constructs/{name}',
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
    backendPath: '/api/dsl/drafts/{name}/save',
    bffPath: '/api/v1/dsl/drafts/{name}/save',
  },
  {
    method: 'POST',
    backendPath: '/api/dsl/drafts/{name}/publish',
    bffPath: '/api/v1/dsl/drafts/{name}/publish',
  },
  {
    method: 'DELETE',
    backendPath: '/api/dsl/drafts/{name}',
    bffPath: '/api/v1/dsl/drafts/{name}/delete',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts',
    bffPath: '/api/v1/dsl/drafts',
  },
  {
    method: 'GET',
    backendPath: '/api/dsl/drafts/{name}',
    bffPath: '/api/v1/dsl/drafts/{name}',
  },
  // DslReloadRouterConfiguration
  {
    method: 'POST',
    backendPath: '/api/dsl/reload',
    bffPath: '/api/v1/dsl/reload',
  },
  // DslExecutionsRouterConfiguration
  {
    method: 'GET',
    backendPath: '/api/executions',
    bffPath: '/api/v1/executions',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/stats',
    bffPath: '/api/v1/executions/stats',
  },
  {
    method: 'GET',
    backendPath: '/api/executions/{id}',
    bffPath: '/api/v1/executions/{id}',
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
const ROUTE_FILENAME = /^(?<route>.+)\.(?<method>get|post|delete)\.ts$/

type DiscoveredRoute = {
  method: Method
  bffPath: string
  relFile: string
}

function discoverRoutes(dir: string): DiscoveredRoute[] {
  const out: DiscoveredRoute[] = []
  for (const entry of readdirSync(dir)) {
    if (entry === '__tests__') continue
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
      return bracket?.groups.name ? `{${bracket.groups.name}}` : s
    })
    if (fileSegments[fileSegments.length - 1] === 'index') fileSegments.pop()
    // Combine the directory layout with the filename segments, converting
    // bracketed parent directory names (e.g. "[name]") into "{name}".
    const parentRel = relative(apiDir, dir).split(sep).filter((s) => s.length > 0).map((s) => {
      const bracket = /^\[(?<name>.+)\]$/.exec(s)
      return bracket?.groups.name ? `{${bracket.groups.name}}` : s
    })
    const allSegments = [...parentRel, ...fileSegments]
    const bffPath = '/api/v1/' + allSegments.join('/')
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
    const missing = expectedProxies.filter(
      (e) => !discoveredKeys.has(`${e.method} ${e.bffPath}`),
    )
    if (missing.length > 0) {
      const lines = missing.map(
        (m) =>
          `  - ${m.method.padEnd(4)} ${m.backendPath.padEnd(34)} → ${m.bffPath}`,
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
