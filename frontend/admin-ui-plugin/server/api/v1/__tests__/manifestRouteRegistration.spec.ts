import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

// Same resolution approach as routeCoverage.spec.ts: this file lives at
// server/api/v1/__tests__/; step up to server/api/v1/ and the plugin root.
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
const pluginRoot = join(apiDir, '..', '..', '..')
const manifestDir = join(apiDir, 'generated')
const manifestPath = join(manifestDir, 'manifest.json')

type ManifestRoute = {
  method: string
  bffPath: string
  handler: string
  params?: string[]
}

function readManifest(): ManifestRoute[] {
  expect(existsSync(manifestPath)).toBe(true)
  return (JSON.parse(readFileSync(manifestPath, 'utf8')) as { routes: ManifestRoute[] }).routes
}

// Must stay in sync with the conversion in module.ts: OpenAPI `{param}` keeps
// the manifest and proxyFromManifest interpolation working, but the route
// registered on Nitro must use radix3 `:param` syntax — programmatic handlers
// are used verbatim and `{param}` would become a literal static segment,
// 404-ing every param route.
function toNitroRoute(bffPath: string): string {
  return bffPath.replace(/\{(\w+)\}/g, ':$1')
}

describe('generated BFF manifest route registration', () => {
  it('module.ts converts OpenAPI {param} placeholders to radix3 :param syntax', () => {
    const source = readFileSync(join(pluginRoot, 'module.ts'), 'utf8')
    expect(source).toContain("route.bffPath.replace(/\\{(\\w+)\\}/g, ':$1')")
  })

  it('module.ts pins a deterministic alias for the generated stubs’ proxy helper', () => {
    const source = readFileSync(join(pluginRoot, 'module.ts'), 'utf8')
    expect(source).toContain("nitroConfig.alias['~/server/utils/proxyFromManifest']")
    expect(existsSync(join(pluginRoot, 'server', 'utils', 'proxyFromManifest.ts'))).toBe(true)
  })

  it('every manifest bffPath converts to a Nitro-style route with no placeholders left', () => {
    const routes = readManifest()
    expect(routes.length).toBeGreaterThan(0)
    for (const route of routes) {
      const nitroRoute = toNitroRoute(route.bffPath)
      expect(nitroRoute, route.bffPath).not.toMatch(/[{}]/)
      for (const param of route.params ?? []) {
        expect(nitroRoute, route.bffPath).toContain(`:${param}`)
      }
    }
  })

  it('every manifest handler file exists under server/api/v1/generated/', () => {
    for (const route of readManifest()) {
      expect(existsSync(join(manifestDir, route.handler)), route.handler).toBe(true)
    }
  })
})
