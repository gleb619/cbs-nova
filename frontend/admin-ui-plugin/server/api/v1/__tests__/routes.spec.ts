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

// The route files import `getRouterParam`/`readBody`/`getQuery` directly from
// `h3` (Nitro auto-imports the same functions at runtime, but the source
// files use explicit imports). Mock just those three on the real `h3` module
// so each test can configure what the route sees; `defineEventHandler` and
// everything else pass through unmocked.
type RouterParamMap = Record<string, string | undefined>
let routerParams: RouterParamMap = {}
let bodyValue: unknown = {}
let queryValue: Record<string, unknown> = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
    readBody: async (_event: unknown) => bodyValue,
    getQuery: (_event: unknown) => queryValue,
  }
})

// Import after the mock + globals are in place.
const healthHandler = (await import('../health.get')).default
const definitionsHandler = (await import('../dsl/definitions.get')).default
const reloadHandler = (await import('../dsl/reload.post')).default
const runHandler = (await import('../dsl/run/[name].post')).default
const previewHandler = (await import('../dsl/preview/[name].post')).default
const explainHandler = (await import('../dsl/explain/[name].post')).default
const executionsIndexHandler = (await import('../executions/index.get')).default
const executionsStatsHandler = (await import('../executions/stats.get')).default
const executionsIdHandler = (await import('../executions/[id].get')).default
const executionsTransactionsHandler = (await import('../executions/[id]/transactions.get')).default
const executionsCancelHandler = (await import('../executions/[id]/cancel.post')).default
const infoHandler = (await import('../info.get')).default
const saveDraftHandler = (await import('../dsl/drafts/[name]/save.post')).default
const publishDraftHandler = (await import('../dsl/drafts/[name]/publish.post')).default
const deleteDraftHandler = (await import('../dsl/drafts/[name]/delete.delete')).default
const helpersIndexHandler = (await import('../dsl/helpers/index.get')).default
const processesIndexHandler = (await import('../dsl/processes/index.get')).default
const processDetailHandler = (await import('../dsl/processes/[name].get')).default
const transactionsIndexHandler = (await import('../dsl/transactions/index.get')).default
const transactionDetailHandler = (await import('../dsl/transactions/[name].get')).default
const constructBodyHandler = (await import('../dsl/constructs/[name].get')).default
const processDiagramHandler = (await import('../dsl/processes/[name]/diagram.get')).default

// Minimal H3Event stub. The route handlers only pass this through to
// proxyToBackend, which is mocked, so a plain object is sufficient.
const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue({ ok: true })
  routerParams = {}
  bodyValue = {}
  queryValue = {}
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
    expect((result as Array<{ type: string }>).map((d) => d.type).sort()).toEqual([
      'function',
      'helper',
      'process',
      'transaction',
    ])
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

  it('passes the inbound event through so Idempotency-Key can be forwarded', async () => {
    const event = {
      node: { req: { headers: { 'idempotency-key': 'idem-123' } } },
    } as Parameters<typeof proxyToBackendMock>[0]
    routerParams = { name: 'myFlow' }
    bodyValue = { input: { x: 1 } }

    await runHandler(event)

    expect(proxyToBackendMock).toHaveBeenCalledWith(event, '/api/dsl/run/myFlow', {
      method: 'POST',
      body: { input: { x: 1 } },
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

describe('executions/stats.get', () => {
  it('GETs /api/executions/stats with no body and no opts', async () => {
    await executionsStatsHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/stats')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('executions/index.get', () => {
  it('GETs /api/executions with no body and no opts', async () => {
    await executionsIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards offset and limit query params to the backend', async () => {
    queryValue = { offset: '40', limit: '20' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { offset: '40', limit: '20' },
    })
  })

  it('forwards only offset when limit is absent', async () => {
    queryValue = { offset: '60' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { offset: '60' },
    })
  })

  it('forwards only limit when offset is absent', async () => {
    queryValue = { limit: '100' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { limit: '100' },
    })
  })

  it('omits unrelated query params and does not call the backend with an empty query object', async () => {
    queryValue = { foo: 'bar' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards the status query param to the backend', async () => {
    queryValue = { status: 'Completed' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { status: 'Completed' },
    })
  })

  it('renames the UI entityName filter to the backend processName param', async () => {
    queryValue = { entityName: 'LoanDisbursement' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { processName: 'LoanDisbursement' },
    })
  })

  it('forwards the mode query param to the backend', async () => {
    queryValue = { mode: 'RUN' }

    await executionsIndexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { mode: 'RUN' },
    })
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

describe('executions/[id]/transactions.get', () => {
  it('interpolates the :id router param into the backend transactions path with GET (no opts)', async () => {
    routerParams = { id: 'exec-abc-123' }

    await executionsTransactionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/executions/exec-abc-123/transactions',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('executions/[id]/cancel.post', () => {
  it('POSTs to the backend cancel path with the :id router param and no body', async () => {
    routerParams = { id: 'exec-abc-123' }

    await executionsCancelHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/executions/exec-abc-123/cancel',
      { method: 'POST' },
    )
  })
})

describe('dsl/drafts/[name]/save.post', () => {
  it('interpolates the :name router param and forwards readBody() as body', async () => {
    routerParams = { name: 'DraftOne' }
    bodyValue = { name: 'DraftOne', type: 'Process', version: 'v2' }

    await saveDraftHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts/DraftOne/save', {
      method: 'POST',
      body: { name: 'DraftOne', type: 'Process', version: 'v2' },
    })
  })
})

describe('dsl/drafts/[name]/publish.post', () => {
  it('interpolates the :name router param and forwards readBody() as body', async () => {
    routerParams = { name: 'DraftOne' }
    bodyValue = { name: 'DraftOne', type: 'Process', version: 'v2' }

    await publishDraftHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts/DraftOne/publish', {
      method: 'POST',
      body: { name: 'DraftOne', type: 'Process', version: 'v2' },
    })
  })
})

describe('dsl/drafts/[name]/delete.delete', () => {
  it('interpolates the :name router param and DELETEs to the backend delete path', async () => {
    routerParams = { name: 'DraftOne' }

    await deleteDraftHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts/DraftOne', {
      method: 'DELETE',
    })
  })
})

describe('info.get', () => {
  it('GETs /actuator/info with no body and returns the backend payload verbatim', async () => {
    const infoPayload = {
      git: { branch: 'main' },
      build: { version: '0.0.1-SNAPSHOT' },
    }
    proxyToBackendMock.mockResolvedValueOnce(infoPayload)

    const result = await infoHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/actuator/info')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
    expect(result).toEqual(infoPayload)
  })
})

describe('dsl/helpers/index.get', () => {
  it('GETs /api/dsl/helpers with no body and no opts', async () => {
    await helpersIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/helpers')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend NamesResponse verbatim', async () => {
    const payload = { names: ['currentTimestamp', 'formatMessage'] }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await helpersIndexHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/processes/index.get', () => {
  it('GETs /api/dsl/processes with no body and no opts', async () => {
    await processesIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/processes')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend NamesResponse verbatim', async () => {
    const payload = { names: ['LoanDisbursement', 'SampleProcess'] }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await processesIndexHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/processes/[name].get', () => {
  it('interpolates the :name router param into the backend path with GET (no opts)', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await processDetailHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/processes/LoanDisbursement',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend ProcessDetail verbatim', async () => {
    routerParams = { name: 'SampleProcess' }
    const payload = {
      name: 'SampleProcess',
      version: '1.0.0',
      taskQueue: 'sample',
      inputType: 'SampleIn',
      outputType: 'SampleOut',
      hasCompensation: false,
      inputSchema: { type: 'object' },
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await processDetailHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/constructs/[name].get', () => {
  it('interpolates the :name router param into the backend path with GET (no opts)', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await constructBodyHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/constructs/LoanDisbursement',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend ConstructBodyDto verbatim', async () => {
    routerParams = { name: 'SampleProcess' }
    const payload = {
      name: 'SampleProcess',
      type: 'process',
      code: '{"steps":[]}',
      steps: [{ id: 'tx1', type: 'transaction', name: 'tx1' }],
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await constructBodyHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})
describe('dsl/transactions/index.get', () => {
  it('GETs /api/dsl/transactions with no body and no opts', async () => {
    await transactionsIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/transactions')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend NamesResponse verbatim', async () => {
    const payload = { names: ['SampleTransaction'] }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await transactionsIndexHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/transactions/[name].get', () => {
  it('interpolates the :name router param into the backend path with GET (no opts)', async () => {
    routerParams = { name: 'SampleTransaction' }

    await transactionDetailHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/transactions/SampleTransaction',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend TransactionDetail verbatim', async () => {
    routerParams = { name: 'SampleTransaction' }
    const payload = {
      name: 'SampleTransaction',
      version: '1.0.0',
      taskQueue: 'sample',
      inputType: 'SampleIn',
      outputType: 'SampleOut',
      hasCompensation: false,
      startToCloseTimeoutMs: 30000,
      inputSchema: { type: 'object' },
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await transactionDetailHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/processes/[name]/diagram.get', () => {
  it('forwards the diagram request without a format query param by default', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = {}

    await processDiagramHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/processes/LoanDisbursement/diagram',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards the format query param when provided', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = { format: 'plantuml' }

    await processDiagramHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/processes/LoanDisbursement/diagram',
      { query: { format: 'plantuml' } },
    )
  })

  it('returns the backend ProcessDiagramDto verbatim', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = { format: 'mermaid' }
    const payload = {
      name: 'LoanDisbursement',
      format: 'mermaid',
      diagram: 'graph TD\n  Start([Start]) --> Execute[LoanDisbursement]',
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await processDiagramHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})
