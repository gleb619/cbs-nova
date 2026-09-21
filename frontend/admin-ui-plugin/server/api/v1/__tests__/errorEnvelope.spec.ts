import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shapeOf, stripPaths } from './contract/shape'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

type RouterParamMap = Record<string, string | string[] | undefined>
let routerParams: RouterParamMap = {}
let bodyValue: unknown = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
    readBody: async (_event: unknown) => bodyValue,
  }
})

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

const unifiedError = {
  code: 'HELPER_NOT_FOUND',
  message: 'helper missing',
  entityName: 'MyProcess',
  runId: 'run-123',
  correlationId: 'cid-456',
  exceptionId: 'ex-789',
  diagnostics: [
    {
      file: 'test.dsl',
      line: 1,
      column: 2,
      message: 'bad syntax',
      severity: 'ERROR',
      code: 'DSL_COMPILATION_ERROR',
    },
  ],
  suggestion: 'check helper name',
  context: { helperName: 'missingHelper' },
}

const expectedShape = shapeOf(unifiedError)

type ErrorRouteCase = {
  name: string
  handler: () => Promise<unknown>
  params: RouterParamMap
  body?: unknown
}

// Import handlers after mocks are hoisted.
const errorRoutes: ErrorRouteCase[] = [
  {
    name: 'POST /api/v1/dsl/run/:name',
    handler: async () =>
      (await import('../generated/routes/dsl/run/[name].post')).default(fakeEvent),
    params: { name: 'runDef' },
    body: { input: {} },
  },
  {
    name: 'POST /api/v1/dsl/preview/:name',
    handler: async () =>
      (await import('../generated/routes/dsl/preview/[name].post')).default(fakeEvent),
    params: { name: 'previewDef' },
    body: { input: {} },
  },
  {
    name: 'POST /api/v1/dsl/explain/:name',
    handler: async () =>
      (await import('../generated/routes/dsl/explain/[name].post')).default(fakeEvent),
    params: { name: 'explainDef' },
    body: { input: {} },
  },
  {
    name: 'GET /api/v1/executions/:id',
    handler: async () =>
      (await import('../generated/routes/executions/[id].get')).default(fakeEvent),
    params: { id: 'exec-123' },
  },
  {
    name: 'POST /api/v1/executions/:id/cancel',
    handler: async () =>
      (await import('../generated/routes/executions/[id]/cancel.post')).default(fakeEvent),
    params: { id: 'exec-123' },
  },
  {
    name: 'GET /api/v1/dsl/drafts/:name',
    handler: async () =>
      (await import('../generated/routes/dsl/drafts/[name].get')).default(fakeEvent),
    params: { name: 'draftDef' },
  },
  {
    name: 'POST /api/v1/dsl/drafts/:name/save',
    handler: async () =>
      (await import('../generated/routes/dsl/drafts/[name]/save.post')).default(fakeEvent),
    params: { name: 'draftDef' },
    body: { source: 'foo: bar' },
  },
  {
    name: 'DELETE /api/v1/dsl/drafts/:name',
    handler: async () => (await import('../dsl/drafts/[name]/delete.delete')).default(fakeEvent),
    params: { name: 'draftDef' },
  },
]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue(unifiedError)
  routerParams = {}
  bodyValue = {}
})

describe('BFF error envelope contract', () => {
  it.each(errorRoutes)('$name proxies the unified ErrorResponse shape', async (route) => {
    routerParams = route.params
    bodyValue = route.body ?? {}
    const result = await route.handler()
    expect(stripPaths(shapeOf(result), [])).toEqual(expectedShape)
  })

  it('verifies the fixture contains every unified-envelope field', () => {
    expect(expectedShape).toEqual({
      code: 'string',
      context: { helperName: 'string' },
      correlationId: 'string',
      diagnostics: [
        {
          code: 'string',
          column: 'number',
          file: 'string',
          line: 'number',
          message: 'string',
          severity: 'string',
        },
      ],
      entityName: 'string',
      exceptionId: 'string',
      message: 'string',
      runId: 'string',
      suggestion: 'string',
    })
  })
})
