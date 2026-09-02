import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shapeOf, stripPaths } from './contract/shape'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let queryValue: Record<string, unknown> = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getQuery: (_event: unknown) => queryValue,
  }
})

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

// Load fixtures from disk so TypeScript JSON-module settings do not matter.
const here = dirname(fileURLToPath(import.meta.url))
function loadFixture(name: string): unknown {
  const raw = readFileSync(join(here, 'contract', 'fixtures', name), 'utf8')
  return JSON.parse(raw) as unknown
}

const definitionsFixture = loadFixture('definitions.json')
const helpersFixture = loadFixture('helpers.json')
const draftsFixture = loadFixture('drafts.json')
const executionsFixture = loadFixture('executions.json')
const executionsStatsFixture = loadFixture('executions-stats.json')

// Import handlers after mocks are hoisted.
const definitionsHandler = (await import('../dsl/definitions.get')).default
const helpersHandler = (await import('../dsl/helpers/index.get')).default
const draftsHandler = (await import('../dsl/drafts/index.get')).default
const executionsHandler = (await import('../executions/index.get')).default
const executionsStatsHandler = (await import('../executions/stats.get')).default

type RouteCase = {
  /** Fixture filename under contract/fixtures/. */
  fixtureName: string
  /** Human-readable route label for test titles. */
  name: string
  /** Nitro handler under test. */
  handler: (event: unknown) => Promise<unknown>
  /** Canonical backend response fixture. */
  fixture: unknown
  /** JSON-path-ish dotted paths to strip before comparison (intentional deltas). */
  deltas: string[]
}

const routes: RouteCase[] = [
  {
    fixtureName: 'definitions.json',
    name: 'GET /api/v1/dsl/definitions',
    handler: definitionsHandler,
    fixture: definitionsFixture,
    deltas: [],
  },
  {
    fixtureName: 'helpers.json',
    name: 'GET /api/v1/dsl/helpers',
    handler: helpersHandler,
    fixture: helpersFixture,
    deltas: [],
  },
  {
    fixtureName: 'drafts.json',
    name: 'GET /api/v1/dsl/drafts',
    handler: draftsHandler,
    fixture: draftsFixture,
    deltas: [],
  },
  {
    fixtureName: 'executions.json',
    name: 'GET /api/v1/executions',
    handler: executionsHandler,
    fixture: executionsFixture,
    deltas: [],
  },
  {
    fixtureName: 'executions-stats.json',
    name: 'GET /api/v1/executions/stats',
    handler: executionsStatsHandler,
    fixture: executionsStatsFixture,
    deltas: [],
  },
]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
})

describe('BFF↔backend contract shape', () => {
  it.each(routes)('$name returns the same JSON shape as the backend fixture', async (route) => {
    proxyToBackendMock.mockResolvedValue(route.fixture)
    const result = await route.handler(fakeEvent)
    expect(stripPaths(shapeOf(result), route.deltas)).toEqual(
      stripPaths(shapeOf(route.fixture), route.deltas),
    )
  })

  it('has a resolvable handler import for every fixture file', () => {
    for (const route of routes) {
      expect(route.handler, `missing handler for fixture ${route.fixtureName}`).toBeDefined()
    }
  })

  describe('shapeOf', () => {
    it('merges array element shapes so an optional field present in only one element is retained', () => {
      const shape = shapeOf([
        { id: 'a', status: 'ok' },
        { id: 'b', extra: true },
      ])
      expect(shape).toEqual([{ extra: 'boolean', id: 'string', status: 'string' }])
    })

    it('represents null as the literal string "null"', () => {
      expect(shapeOf(null)).toBe('null')
    })

    it('sorts keys recursively for nested objects', () => {
      const shape = shapeOf({ z: { b: 1, a: 2 }, a: { y: 1, x: 2 } })
      expect(shape).toEqual({
        a: { x: 'number', y: 'number' },
        z: { a: 'number', b: 'number' },
      })
    })
  })
})
