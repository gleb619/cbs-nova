import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  check,
  discoverHandRoutes,
  generate,
  type GenerateResult,
} from '../generateBffRoutes'

const TMP_PREFIX = join(tmpdir(), 'gen-bff-routes-test-')

function makeTmp(): string {
  return mkdtempSync(TMP_PREFIX)
}

function cleanup(...dirs: string[]): void {
  for (const dir of dirs) {
    rmSync(dir, { recursive: true, force: true })
  }
}

function writeSpec(dir: string, paths: Record<string, Record<string, unknown>>): string {
  const specPath = join(dir, 'openapi.json')
  writeFileSync(specPath, JSON.stringify({ paths }))
  return specPath
}

function readGenerated(dir: string, rel: string): string {
  return readFileSync(join(dir, rel), 'utf8')
}

interface CheckFixture {
  pluginDir: string
  outDir: string
  result: GenerateResult
}

function makeCheckFixture(): CheckFixture {
  const pluginDir = makeTmp()
  const outDir = makeTmp()
  const specPath = join(pluginDir, 'openapi.json')
  writeFileSync(
    specPath,
    JSON.stringify({
      paths: {
        '/api/dsl/definitions': { get: { operationId: 'listDefs' } },
        '/api/dsl/drafts/{name}': { delete: { operationId: 'deleteDraft' } },
      },
    }),
  )
  mkdirSync(join(pluginDir, 'server/api/v1'), { recursive: true })
  const result = generate(specPath, pluginDir, outDir)
  return { pluginDir, outDir, result }
}

function commitGenerated(
  pluginDir: string,
  outDir: string,
  generatedFiles: string[],
): void {
  for (const rel of generatedFiles) {
    const fresh = readFileSync(join(outDir, rel), 'utf8')
    const target = join(pluginDir, rel)
    mkdirSync(dirname(target), { recursive: true })
    writeFileSync(target, fresh)
  }
}

function commitHandRoute(pluginDir: string, handRoute: string): string {
  const target = join(pluginDir, 'server/api/v1', handRoute)
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, 'export default defineEventHandler(() => {})')
  return target
}

function captureFailureMessages(err: ReturnType<typeof vi.spyOn>): string[] {
  return err.mock.calls
    .map((c) => String(c[0]))
    .join('\n')
    .split('\n')
    .map((line) => line.replace(/^\s*-\s*/, '').trim())
    .filter(Boolean)
}

// ---------------------------------------------------------------------------
// buildOperations — sort, filter, errors
// ---------------------------------------------------------------------------

describe('generateBffRoutes — buildOperations', () => {
  it('throws when the spec file does not exist', () => {
    const dir = makeTmp()
    try {
      expect(() => generate(join(dir, 'missing.json'), dir, dir)).toThrow(/not found/)
    } finally {
      cleanup(dir)
    }
  })

  it('throws when the spec has no "paths" object', () => {
    const dir = makeTmp()
    try {
      const specPath = join(dir, 'openapi.json')
      writeFileSync(specPath, JSON.stringify({ info: {} }))
      expect(() => generate(specPath, dir, dir)).toThrow(/no "paths" object/)
    } finally {
      cleanup(dir)
    }
  })

  it('throws when an operation is missing operationId', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, { '/api/dsl/x': { get: {} } })
      expect(() => generate(specPath, dir, dir)).toThrow(/no operationId/)
    } finally {
      cleanup(dir)
    }
  })

  it('throws on duplicate operationId', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/a': { get: { operationId: 'op' } },
        '/api/dsl/b': { get: { operationId: 'op' } },
      })
      expect(() => generate(specPath, dir, dir)).toThrow(/duplicate operationId op/)
    } finally {
      cleanup(dir)
    }
  })

  it('skips non-CRUD HTTP methods (options / head / patch / trace)', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/x': {
          get: { operationId: 'keptGet' },
          options: { operationId: 'skippedOptions' },
          head: { operationId: 'skippedHead' },
          patch: { operationId: 'skippedPatch' },
          trace: { operationId: 'skippedTrace' },
        },
      })
      const { manifest } = generate(specPath, dir, dir)
      expect(manifest.routes.map((r) => r.operationId)).toEqual(['keptGet'])
    } finally {
      cleanup(dir)
    }
  })

  it('sorts operations alphabetically by operationId', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/z': { get: { operationId: 'zeta' } },
        '/api/dsl/a': { get: { operationId: 'alpha' } },
        '/api/dsl/m': { get: { operationId: 'mid' } },
      })
      const { manifest } = generate(specPath, dir, dir)
      expect(manifest.routes.map((r) => r.operationId)).toEqual(['alpha', 'mid', 'zeta'])
    } finally {
      cleanup(dir)
    }
  })
})

// ---------------------------------------------------------------------------
// toBffPath + emitClient param round-trip
// ---------------------------------------------------------------------------

describe('generateBffRoutes — toBffPath + emitClient param templating', () => {
  it('rewrites every supported BFF prefix', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/definitions': { get: { operationId: 'listDefs' } },
        '/api/executions': { get: { operationId: 'listExecs' } },
        '/api/executions/{id}': { get: { operationId: 'getExec' } },
      })
      const { manifest } = generate(specPath, dir, dir)
      expect(manifest.routes.find((r) => r.operationId === 'listDefs')?.bffPath).toBe(
        '/api/v1/dsl/definitions',
      )
      expect(manifest.routes.find((r) => r.operationId === 'listExecs')?.bffPath).toBe(
        '/api/v1/executions',
      )
      expect(manifest.routes.find((r) => r.operationId === 'getExec')?.bffPath).toBe(
        '/api/v1/executions/{id}',
      )
    } finally {
      cleanup(dir)
    }
  })

  it('honours BFF_PATH_OVERRIDES for legacy path shapes (visible in emitClient)', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/drafts/{name}': { delete: { operationId: 'deleteDraft' } },
      })
      generate(specPath, dir, dir)
      // deleteDraft is excluded from generated stubs but emitClient iterates ALL operations,
      // so the override must still surface in the generated client function.
      const client = readGenerated(dir, 'app/composables/generated/useBffApi.ts')
      expect(client).toContain(
        'return bffFetch(`/api/v1/dsl/drafts/${name}/delete`,',
      )
    } finally {
      cleanup(dir)
    }
  })

  it('throws when backend path has no BFF prefix mapping', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/unknown/things': { get: { operationId: 'op' } },
      })
      expect(() => generate(specPath, dir, dir)).toThrow(/no BFF prefix mapping/)
    } finally {
      cleanup(dir)
    }
  })

  it('round-trips {param} placeholders into ${param} template literals (1 and 2 params)', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/drafts/{name}': { get: { operationId: 'readDraft' } },
        '/api/dsl/drafts/{name}/tests': { post: { operationId: 'runTests' } },
        '/api/dsl/{type}/{name}/schema': { get: { operationId: 'getSchema' } },
      })
      generate(specPath, dir, dir)
      const client = readGenerated(dir, 'app/composables/generated/useBffApi.ts')
      expect(client).toContain('export async function readDraft(name: string,')
      expect(client).toContain('return bffFetch(`/api/v1/dsl/drafts/${name}`,')
      expect(client).toContain('export async function runTests(name: string,')
      expect(client).toContain('return bffFetch(`/api/v1/dsl/drafts/${name}/tests`,')
      expect(client).toContain('export async function getSchema(type: string, name: string,')
      expect(client).toContain('return bffFetch(`/api/v1/dsl/${type}/${name}/schema`,')
    } finally {
      cleanup(dir)
    }
  })
})

// ---------------------------------------------------------------------------
// emitStub — hasBody / explicitMethod flags
// ---------------------------------------------------------------------------

describe('generateBffRoutes — emitStub flags', () => {
  it('marks BODY_OPERATIONS members with hasBody=true', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/drafts/{name}': { put: { operationId: 'saveDraft' } },
      })
      generate(specPath, dir, dir)
      expect(readGenerated(dir, 'server/api/v1/generated/routes/dsl/drafts/[name].put.ts')).toContain(
        '"hasBody": true',
      )
    } finally {
      cleanup(dir)
    }
  })

  it('marks EXPLICIT_METHOD_GET_OPERATIONS members with explicitMethod=true', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/publish/{name}/history/{id}': {
          get: { operationId: 'readPublishHistoryEntry' },
        },
      })
      generate(specPath, dir, dir)
      expect(
        readGenerated(
          dir,
          'server/api/v1/generated/routes/dsl/publish/[name]/history/[id].get.ts',
        ),
      ).toContain('"explicitMethod": true')
    } finally {
      cleanup(dir)
    }
  })

  it('leaves hasBody=false and explicitMethod=false for a regular GET', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/definitions': { get: { operationId: 'listDefs' } },
      })
      generate(specPath, dir, dir)
      const stub = readGenerated(
        dir,
        'server/api/v1/generated/routes/dsl/definitions.get.ts',
      )
      expect(stub).toContain('"hasBody": false')
      expect(stub).toContain('"explicitMethod": false')
    } finally {
      cleanup(dir)
    }
  })

  it('leaves explicitMethod=true for any non-GET operation', () => {
    const dir = makeTmp()
    try {
      const specPath = writeSpec(dir, {
        '/api/dsl/drafts/{name}': { put: { operationId: 'saveDraft' } },
      })
      generate(specPath, dir, dir)
      expect(readGenerated(dir, 'server/api/v1/generated/routes/dsl/drafts/[name].put.ts')).toContain(
        '"explicitMethod": true',
      )
    } finally {
      cleanup(dir)
    }
  })
})

// ---------------------------------------------------------------------------
// discoverHandRoutes — bracket mapping + skip dirs
// ---------------------------------------------------------------------------

describe('generateBffRoutes — discoverHandRoutes', () => {
  function setupApi(layout: Record<string, Record<string, string>>): string {
    const apiDir = join(makeTmp(), 'server/api/v1')
    mkdirSync(apiDir, { recursive: true })
    const walk = (base: string, tree: Record<string, Record<string, string>>) => {
      for (const [name, val] of Object.entries(tree)) {
        const full = join(base, name)
        if (val && typeof val === 'object') {
          mkdirSync(full, { recursive: true })
          walk(full, val)
        } else {
          mkdirSync(dirname(full), { recursive: true })
          writeFileSync(full, String(val))
        }
      }
    }
    walk(apiDir, layout)
    return apiDir
  }

  function tmpRoot(apiDir: string): string {
    return dirname(dirname(dirname(apiDir)))
  }

  it('maps [name].get.ts to GET /api/v1/dsl/drafts/{name}', () => {
    const apiDir = setupApi({
      dsl: {
        drafts: {
          '[name].get.ts': 'export default defineEventHandler(() => {})',
          '[name].delete.ts': 'export default defineEventHandler(() => {})',
        },
      },
    })
    try {
      const discovered = discoverHandRoutes(apiDir)
      expect(discovered).toContainEqual({
        key: 'GET /api/v1/dsl/drafts/{name}',
        relFile: 'dsl/drafts/[name].get.ts',
      })
      expect(discovered).toContainEqual({
        key: 'DELETE /api/v1/dsl/drafts/{name}',
        relFile: 'dsl/drafts/[name].delete.ts',
      })
    } finally {
      cleanup(tmpRoot(apiDir))
    }
  })

  it('collapses trailing "index" segment to the parent route', () => {
    const apiDir = setupApi({
      dsl: {
        auth: {
          keys: {
            'index.post.ts': 'export default defineEventHandler(() => {})',
          },
        },
      },
    })
    try {
      const discovered = discoverHandRoutes(apiDir)
      expect(discovered).toContainEqual({
        key: 'POST /api/v1/dsl/auth/keys',
        relFile: 'dsl/auth/keys/index.post.ts',
      })
    } finally {
      cleanup(tmpRoot(apiDir))
    }
  })

  it('skips __tests__ and generated subdirectories at any depth', () => {
    const apiDir = setupApi({
      dsl: {
        'definitions.get.ts': 'x',
        __tests__: {
          'foo.get.ts': 'x',
          nested: {
            'bar.get.ts': 'x',
          },
        },
        generated: {
          'baz.get.ts': 'x',
        },
      },
    })
    try {
      const discovered = discoverHandRoutes(apiDir)
      const keys = discovered.map((r) => r.key)
      expect(keys).toContain('GET /api/v1/dsl/definitions')
      expect(keys.some((k) => k.includes('foo'))).toBe(false)
      expect(keys.some((k) => k.includes('nested/bar'))).toBe(false)
      expect(keys.some((k) => k.includes('baz'))).toBe(false)
    } finally {
      cleanup(tmpRoot(apiDir))
    }
  })

  it('ignores files that do not match <route>.<method>.ts', () => {
    const apiDir = setupApi({
      dsl: {
        'definitions.get.ts': 'x',
        'helpers.ts': 'x',
        'utils.ts': 'x',
      },
    })
    try {
      const discovered = discoverHandRoutes(apiDir)
      expect(discovered).toHaveLength(1)
      expect(discovered[0].key).toBe('GET /api/v1/dsl/definitions')
    } finally {
      cleanup(tmpRoot(apiDir))
    }
  })

  it('returns every route under the api dir, ignoring files that do not match <route>.<method>.ts', () => {
    const apiDir = setupApi({
      dsl: {
        'z.get.ts': 'x',
        'a.get.ts': 'x',
      },
    })
    try {
      const discovered = discoverHandRoutes(apiDir)
      const keys = discovered.map((r) => r.key).sort()
      expect(keys).toEqual(['GET /api/v1/dsl/a', 'GET /api/v1/dsl/z'])
    } finally {
      cleanup(tmpRoot(apiDir))
    }
  })
})

// ---------------------------------------------------------------------------
// check — drift, stale, missing generated, missing hand route, shadowing
// ---------------------------------------------------------------------------

describe('generateBffRoutes — check', () => {
  let exitSpy: ReturnType<typeof vi.spyOn>
  let errSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    exitSpy = vi.spyOn(process, 'exit').mockImplementation((code) => code as never)
    errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    exitSpy.mockRestore()
    errSpy.mockRestore()
  })

  function runCheck(cfg: CheckFixture): string[] {
    check(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
    return captureFailureMessages(errSpy)
  }

  it('reports drift when a committed generated file differs from fresh', () => {
    const cfg = makeCheckFixture()
    try {
      commitGenerated(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
      const target = cfg.result.generatedFiles[1]
      writeFileSync(join(cfg.pluginDir, target), 'corrupted content')
      const failures = runCheck(cfg)
      expect(
        failures.some((m) => m.includes(target) && m.includes('drifted from docs/openapi.json')),
      ).toBe(true)
      expect(exitSpy).toHaveBeenCalledWith(1)
    } finally {
      cleanup(cfg.pluginDir, cfg.outDir)
    }
  })

  it('reports a missing committed generated file', () => {
    const cfg = makeCheckFixture()
    try {
      commitGenerated(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
      const rel = cfg.result.generatedFiles[1]
      rmSync(join(cfg.pluginDir, rel))
      const failures = runCheck(cfg)
      expect(
        failures.some((m) => m.includes(rel) && m.includes('missing generated file')),
      ).toBe(true)
      expect(exitSpy).toHaveBeenCalledWith(1)
    } finally {
      cleanup(cfg.pluginDir, cfg.outDir)
    }
  })

  it('reports a stale committed generated file that is no longer produced', () => {
    const cfg = makeCheckFixture()
    try {
      commitGenerated(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
      const staleRel = 'server/api/v1/generated/stale.get.ts'
      const staleAbs = join(cfg.pluginDir, staleRel)
      mkdirSync(dirname(staleAbs), { recursive: true })
      writeFileSync(staleAbs, '// leftover from a previous regeneration')
      const failures = runCheck(cfg)
      expect(
        failures.some(
          (m) => m.includes(staleRel) && m.includes('stale generated file'),
        ),
      ).toBe(true)
      expect(exitSpy).toHaveBeenCalledWith(1)
    } finally {
      cleanup(cfg.pluginDir, cfg.outDir)
    }
  })

  it('reports an excluded operation missing its hand-written route', () => {
    const cfg = makeCheckFixture()
    try {
      commitGenerated(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
      // deleteDraft is excluded and its handRoute is dsl/drafts/[name]/delete.delete.ts — leave it absent
      const failures = runCheck(cfg)
      expect(
        failures.some(
          (m) => m.includes('deleteDraft') && m.includes('missing its hand-written route'),
        ),
      ).toBe(true)
      expect(exitSpy).toHaveBeenCalledWith(1)
    } finally {
      cleanup(cfg.pluginDir, cfg.outDir)
    }
  })

  it('reports a hand-written route shadowing a generated BFF route', () => {
    const cfg = makeCheckFixture()
    try {
      commitGenerated(cfg.pluginDir, cfg.outDir, cfg.result.generatedFiles)
      // Commit deleteDraft hand route so its absence does not muddy this failure
      commitHandRoute(cfg.pluginDir, 'dsl/drafts/[name]/delete.delete.ts')
      // Drop a hand-written route at dsl/definitions.get.ts (listDefs is generated there)
      const shadow = join(cfg.pluginDir, 'server/api/v1/dsl/definitions.get.ts')
      mkdirSync(dirname(shadow), { recursive: true })
      writeFileSync(shadow, 'export default defineEventHandler(() => {})')
      const failures = runCheck(cfg)
      expect(
        failures.some(
          (m) =>
            m.includes('shadows a generated BFF route') &&
            m.includes('dsl/definitions.get.ts'),
        ),
      ).toBe(true)
      expect(exitSpy).toHaveBeenCalledWith(1)
    } finally {
      cleanup(cfg.pluginDir, cfg.outDir)
    }
  })
})