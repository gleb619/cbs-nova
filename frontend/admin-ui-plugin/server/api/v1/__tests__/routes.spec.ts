import { beforeEach, describe, expect, it, vi } from 'vitest'

// Stub `proxyToBackend` before importing the route files so the routes pick
// up the mock via `vi.mock`'s hoisting. `vi.hoisted` runs the factory during
// vi.mock hoisting so it's defined before the route module is evaluated.
const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

// The route files call `getRouterParam` and `readBody` as bare globals (Nitro
// auto-imports them at runtime). We install mutable per-suite stubs on
// globalThis so each test can configure what the route sees.
type RouterParamMap = Record<string, string | undefined>
let routerParams: RouterParamMap = {}
let bodyValue: unknown = {}

const g = globalThis as Record<string, unknown>
g.getRouterParam = (_event: unknown, name: string) => routerParams[name]
g.readBody = async (_event: unknown) => bodyValue

// Import after the mock + globals are in place.
const healthHandler = (await import('../health.get')).default
const definitionsHandler = (await import('../dsl/definitions.get')).default
const reloadHandler = (await import('../dsl/reload.post')).default
const runHandler = (await import('../dsl/run/[name].post')).default
const previewHandler = (await import('../dsl/preview/[name].post')).default
const explainHandler = (await import('../dsl/explain/[name].post')).default
const executionsIndexHandler = (await import('../executions/index.get')).default
const executionsIdHandler = (await import('../executions/[id].get')).default

// Minimal H3Event stub. The route handlers only pass this through to
// proxyToBackend, which is mocked, so a plain object is sufficient.
const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue({ ok: true })
  routerParams = {}
  bodyValue = {}
})

describe('health.get', () => {
  it('returns the static BFF health payload without calling proxyToBackend', async () => {
    const result = await healthHandler(fakeEvent)
    expect(result).toEqual({ status: 'ok', bff: 'admin-ui-plugin' })
    expect(proxyToBackendMock).not.toHaveBeenCalled()
  })
})

describe('dsl/definitions.get', () => {
  it('GETs /api/dsl/definitions with no body', async () => {
    await definitionsHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/definitions')
    // No opts (3rd arg) → no method override, no body.
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the aggregated backend body verbatim (200 path, no reshaping)', async () => {
    const aggregated = [
      { name: 'LoanDisbursement', type: 'process', inputSchema: { type: 'object' } },
      { name: 'SampleTransaction', type: 'transaction' },
      { name: 'sampleHelper', type: 'helper' },
      { name: 'sampleFunction', type: 'function' },
    ]
    proxyToBackendMock.mockResolvedValueOnce(aggregated)

    const result = await definitionsHandler(fakeEvent)

    // BFF is a thin passthrough — selector-friendly {name,type} shape is
    // guaranteed by the backend DslIntrospectionResource. See
    // docs/plans/T182-fix-definitions-introspection-wiring.md.
    expect(result).toEqual(aggregated)
    expect(result).toHaveLength(4)
    expect((result as Array<{ type: string }>).map((d) => d.type).sort()).toEqual(
      ['function', 'helper', 'process', 'transaction'],
    )
  })
})

describe('dsl/reload.post', () => {
  it('POSTs to /api/dsl/reload with method=POST and no body', async () => {
    await reloadHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/reload', {
      method: 'POST',
    })
  })
})

describe('dsl/run/[name].post', () => {
  it('interpolates the :name router param and forwards readBody() as body', async () => {
    routerParams = { name: 'myFlow' }
    bodyValue = { input: { x: 1 } }

    await runHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/run/myFlow', {
      method: 'POST',
      body: { input: { x: 1 } },
    })
  })

  it('forwards a falsy body through unchanged', async () => {
    routerParams = { name: 'emptyBody' }
    bodyValue = null

    await runHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/run/emptyBody', {
      method: 'POST',
      body: null,
    })
  })
})

describe('dsl/preview/[name].post', () => {
  it('interpolates the :name router param and forwards readBody() as body', async () => {
    routerParams = { name: 'previewFlow' }
    bodyValue = { body: { foo: 'bar' }, metadata: { source: 'test' } }

    await previewHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/preview/previewFlow', {
      method: 'POST',
      body: { body: { foo: 'bar' }, metadata: { source: 'test' } },
    })
  })
})

describe('dsl/explain/[name].post', () => {
  it('interpolates the :name router param and forwards readBody() as body', async () => {
    routerParams = { name: 'explainFlow' }
    bodyValue = { body: { a: 1 }, metadata: undefined }

    await explainHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/explain/explainFlow', {
      method: 'POST',
      body: { body: { a: 1 }, metadata: undefined },
    })
  })
})

describe('executions/index.get', () => {
  it('GETs /api/executions with no body and no opts', async () => {
    await executionsIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('executions/[id].get', () => {
  it('interpolates the :id router param into the backend path with GET (no opts)', async () => {
    routerParams = { id: 'exec-abc-123' }

    await executionsIdHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/exec-abc-123')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('passes through ids that contain URL-unsafe characters verbatim (no encoding in the route layer)', async () => {
    // The route layer is a thin passthrough — encoding is the backend's
    // concern. Documenting the current behavior so a future change is
    // explicit.
    routerParams = { id: 'with/slash' }

    await executionsIdHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/with/slash')
  })
})
