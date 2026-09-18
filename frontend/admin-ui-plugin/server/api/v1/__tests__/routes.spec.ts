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
type RouterParamMap = Record<string, string | string[] | undefined>
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
    getRequestHeader: (event: unknown, name: string) => {
      const req = (event as { node?: { req?: { headers?: Record<string, string> } } }).node?.req
      return req?.headers?.[name.toLowerCase()]
    },
    setResponseHeader: (event: unknown, name: string, value: string) => {
      const res = (event as { node?: { res?: { headers?: Record<string, string> } } }).node?.res
      if (res?.headers) res.headers[name] = value
    },
    setResponseStatus: (event: unknown, statusCode: number) => {
      const res = (event as { node?: { res?: { statusCode?: number } } }).node?.res
      if (res) res.statusCode = statusCode
    },
  }
})

// Import after the mock + globals are in place.
const healthHandler = (await import('../health.get')).default
const definitionsHandler = (await import('../dsl/definitions.get')).default
const reloadHandler = (await import('../dsl/reload.post')).default
const manifestGuardHandler = (await import('../dsl/manifest/guard.get')).default
const auditHandler = (await import('../dsl/audit.get')).default
const eventsHandler = (await import('../dsl/events.get')).default
const runHandler = (await import('../dsl/run/[name].post')).default
const previewHandler = (await import('../dsl/preview/[name].post')).default
const explainHandler = (await import('../dsl/explain/[name].post')).default
const executionsIndexHandler = (await import('../executions/index.get')).default
const executionsStatsHandler = (await import('../executions/stats.get')).default
const executionsTimeseriesHandler = (await import('../executions/stats/timeseries.get')).default
const executionsIdHandler = (await import('../executions/[id].get')).default
const executionsTransactionsHandler = (await import('../executions/[id]/transactions.get')).default
const executionsCancelHandler = (await import('../executions/[id]/cancel.post')).default
const executionsExportHandler = (await import('../executions/export.get')).default
const infoHandler = (await import('../info.get')).default
const saveDraftHandler = (await import('../dsl/drafts/[name]/save.post')).default
const publishDraftHandler = (await import('../dsl/drafts/[name]/publish.post')).default
const deleteDraftHandler = (await import('../dsl/drafts/[name]/delete.delete')).default
const listHistoryHandler = (await import('../dsl/drafts/[name]/history/index.get')).default
const restoreHistoryHandler = (
  await import('../dsl/drafts/[name]/history/[timestamp]/restore.post')
).default
const historyEntryHandler = (await import('../dsl/drafts/[name]/history/[timestamp]/index.get'))
  .default
const historyDiffHandler = (await import('../dsl/drafts/[name]/history/[timestamp]/diff.get'))
  .default
const helpersIndexHandler = (await import('../dsl/helpers/index.get')).default
const processesIndexHandler = (await import('../dsl/processes/index.get')).default
const processDetailHandler = (await import('../dsl/processes/[name].get')).default
const transactionsIndexHandler = (await import('../dsl/transactions/index.get')).default
const transactionDetailHandler = (await import('../dsl/transactions/[name].get')).default
const constructBodyHandler = (await import('../dsl/constructs/[name].get')).default
const constructSchemaHandler = (await import('../dsl/schemas/[name].get')).default
const processDiagramHandler = (await import('../dsl/processes/[name]/diagram.get')).default
const schedulesIndexHandler = (await import('../dsl/schedules/index.get')).default
const schedulesCreateHandler = (await import('../dsl/schedules/index.post')).default
const schedulesDeleteHandler = (await import('../dsl/schedules/[definition].delete')).default
const schedulesPauseHandler = (await import('../dsl/schedules/[definition]/pause.post')).default
const schedulesResumeHandler = (await import('../dsl/schedules/[definition]/resume.post')).default
const listApiKeysHandler = (await import('../dsl/auth/keys/index.get')).default
const createApiKeyHandler = (await import('../dsl/auth/keys/index.post')).default
const revokeApiKeyHandler = (await import('../dsl/auth/keys/[id]/index.delete')).default
const exportDefinitionsHandler = (await import('../dsl/definitions/export.get')).default
const importDefinitionsHandler = (await import('../dsl/definitions/import.post')).default
const listDefinitionTestsHandler = (await import('../dsl/definitions/[name]/tests/index.get'))
  .default
const replaceDefinitionTestsHandler = (await import('../dsl/definitions/[name]/tests/index.put'))
  .default
const runDefinitionTestsHandler = (await import('../dsl/definitions/[name]/tests/run.post')).default
const listDslFilesHandler = (await import('../dsl/files/index.get')).default
const readDslFileByNameHandler = (await import('../dsl/files/by-name/[name].get')).default
const writeDslFileByNameHandler = (await import('../dsl/files/by-name/[name].post')).default
const readDslFilePathHandler = (await import('../dsl/files/[...path].get')).default
const writeDslFilePathHandler = (await import('../dsl/files/[...path].post')).default
const bulkWriteDslFilesHandler = (await import('../dsl/files/bulk.post')).default
const flushDslFilesHandler = (await import('../dsl/files/flush.post')).default
const dslFileStatusHandler = (await import('../dsl/files/status.get')).default

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

  it('returns the paged backend body verbatim (200 path, no reshaping)', async () => {
    const aggregated = {
      items: [
        { name: 'LoanDisbursement', type: 'process', inputSchema: { type: 'object' } },
        { name: 'SampleTransaction', type: 'transaction' },
        { name: 'sampleHelper', type: 'helper' },
        { name: 'sampleFunction', type: 'function' },
      ],
      total: 4,
      offset: 0,
      limit: 50,
    }
    proxyToBackendMock.mockResolvedValueOnce(aggregated)

    const result = await definitionsHandler(fakeEvent)

    // BFF is a thin passthrough — the paged envelope is produced by the
    // backend DslIntrospectionHandler. See T382.
    expect(result).toEqual(aggregated)
    expect(
      (result as { items?: Array<{ type: string }> }).items?.map((d) => d.type).sort(),
    ).toEqual(['function', 'helper', 'process', 'transaction'])
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

describe('dsl/manifest/guard.get', () => {
  it('GETs /api/dsl/manifest/guard with no query and no opts', async () => {
    await manifestGuardHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/manifest/guard')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/audit.get', () => {
  it('GETs /api/dsl/audit with no query and no opts', async () => {
    await auditHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/audit')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards offset, limit and action query params', async () => {
    queryValue = { offset: '20', limit: '10', action: 'DEFINITION_PUBLISH' }

    await auditHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/audit', {
      query: { offset: '20', limit: '10', action: 'DEFINITION_PUBLISH' },
    })
  })
})

describe('dsl/events.get', () => {
  it('GETs /api/dsl/events with no query and no opts', async () => {
    await eventsHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/events')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards offset, limit, type, aggregateType, aggregateId, correlationId and since', async () => {
    queryValue = {
      offset: '20',
      limit: '10',
      type: 'RunCompleted',
      aggregateType: 'run',
      aggregateId: 'run-123',
      correlationId: 'corr-1',
      since: '2026-09-10T12:00:00Z',
    }

    await eventsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/events', {
      query: {
        offset: '20',
        limit: '10',
        type: 'RunCompleted',
        aggregateType: 'run',
        aggregateId: 'run-123',
        correlationId: 'corr-1',
        since: '2026-09-10T12:00:00Z',
      },
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

describe('executions/stats/timeseries.get', () => {
  it('GETs /api/executions/stats/timeseries with no body and no opts', async () => {
    queryValue = { windowHours: '24', bucketMinutes: '60' }

    await executionsTimeseriesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/stats/timeseries')
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

describe('dsl/drafts/[name]/history/index.get', () => {
  it('interpolates the :name router param and GETs the backend history path', async () => {
    routerParams = { name: 'DraftOne' }

    await listHistoryHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts/DraftOne/history')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/drafts/[name]/history/[timestamp]/restore.post', () => {
  it('interpolates :name and :timestamp and POSTs to the backend restore path', async () => {
    routerParams = { name: 'DraftOne', timestamp: '123456789' }

    await restoreHistoryHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/DraftOne/history/123456789/restore',
      { method: 'POST' },
    )
  })
})

describe('dsl/drafts/[name]/history/[timestamp]/index.get', () => {
  it('interpolates :name and :timestamp and GETs the backend history entry path', async () => {
    routerParams = { name: 'DraftOne', timestamp: '123456789' }

    await historyEntryHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/DraftOne/history/123456789',
      { method: 'GET' },
    )
  })
})

describe('dsl/drafts/[name]/history/[timestamp]/diff.get', () => {
  it('interpolates :name and :timestamp and GETs the backend diff path', async () => {
    routerParams = { name: 'DraftOne', timestamp: '123456789' }

    await historyDiffHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/DraftOne/history/123456789/diff',
      { method: 'GET' },
    )
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

describe('dsl/schemas/[name].get', () => {
  it('interpolates the :name router param into the backend path with GET (no opts)', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await constructSchemaHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schemas/LoanDisbursement')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend ConstructSchemaDto verbatim', async () => {
    routerParams = { name: 'SampleProcess' }
    const payload = {
      name: 'SampleProcess',
      type: 'process',
      inputSchema: { type: 'object', properties: {} },
      outputSchema: { type: 'object', properties: {} },
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await constructSchemaHandler(fakeEvent)

    expect(result).toEqual(payload)
  })

  it('forwards the mode query param to the backend', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = { mode: 'explain' }

    await constructSchemaHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/schemas/LoanDisbursement?mode=explain',
    )
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

describe('dsl/schedules/index.get', () => {
  it('GETs /api/dsl/schedules with no body', async () => {
    await schedulesIndexHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/schedules/index.post', () => {
  it('POSTs readBody() to /api/dsl/schedules', async () => {
    bodyValue = { definition: 'A', cron: '0 9 * * *', timezone: 'UTC' }

    await schedulesCreateHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules', {
      method: 'POST',
      body: { definition: 'A', cron: '0 9 * * *', timezone: 'UTC' },
    })
  })
})

describe('dsl/schedules/[definition].delete', () => {
  it('interpolates the :definition router param and DELETEs to /api/dsl/schedules/{definition}', async () => {
    routerParams = { definition: 'A' }

    await schedulesDeleteHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules/A', {
      method: 'DELETE',
    })
  })
})

describe('dsl/schedules/[definition]/pause.post', () => {
  it('interpolates the :definition router param and POSTs readBody() to the pause path', async () => {
    routerParams = { definition: 'A' }
    bodyValue = { reason: 'maintenance' }

    await schedulesPauseHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules/A/pause', {
      method: 'POST',
      body: { reason: 'maintenance' },
    })
  })

  it('forwards a falsy body through unchanged', async () => {
    routerParams = { definition: 'A' }
    bodyValue = null

    await schedulesPauseHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules/A/pause', {
      method: 'POST',
      body: null,
    })
  })
})

describe('dsl/schedules/[definition]/resume.post', () => {
  it('interpolates the :definition router param and POSTs readBody() to the resume path', async () => {
    routerParams = { definition: 'A' }
    bodyValue = { reason: 'back online' }

    await schedulesResumeHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/schedules/A/resume', {
      method: 'POST',
      body: { reason: 'back online' },
    })
  })
})
describe('dsl/auth/keys/index.get', () => {
  it('GETs /api/dsl/auth/keys with no body', async () => {
    await listApiKeysHandler(fakeEvent)
    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('returns the backend key list verbatim (no hash/plaintext fields)', async () => {
    const payload = [{ id: 'k1', label: 'ci', prefix: 'ak_ci', createdAt: '2026-09-01T00:00:00Z' }]
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await listApiKeysHandler(fakeEvent)

    expect(result).toEqual(payload)
  })

  it('propagates a backend 401/403 rejection so unauthenticated requests stay unauthorized', async () => {
    const err = Object.assign(new Error('Unauthorized'), { statusCode: 401 })
    proxyToBackendMock.mockRejectedValueOnce(err)

    await expect(listApiKeysHandler(fakeEvent)).rejects.toBe(err)
  })
})

describe('dsl/auth/keys/index.post', () => {
  it('POSTs readBody() to /api/dsl/auth/keys', async () => {
    bodyValue = { label: 'ci' }

    await createApiKeyHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys', {
      method: 'POST',
      body: { label: 'ci' },
    })
  })

  it('sets cache-control: no-store on the response (plaintext key is returned exactly once)', async () => {
    const event = {
      node: { res: { headers: {} } },
    } as Parameters<typeof proxyToBackendMock>[0]

    await createApiKeyHandler(event)

    expect(event.node.res.headers['cache-control']).toBe('no-store')
  })

  it('returns the created key verbatim and never logs the plaintext', async () => {
    const created = { id: 'k1', label: 'ci', apiKey: 'ak_live_super_secret_plaintext' }
    proxyToBackendMock.mockResolvedValueOnce(created)
    const spies = (['log', 'info', 'debug', 'warn', 'error'] as const).map((level) =>
      vi.spyOn(console, level).mockImplementation(() => {}),
    )

    try {
      const result = await createApiKeyHandler(fakeEvent)

      expect(result).toEqual(created)
      const logged = spies
        .flatMap((spy) => spy.mock.calls)
        .flat()
        .join(' ')
      expect(logged).not.toContain('ak_live_super_secret_plaintext')
    } finally {
      for (const spy of spies) spy.mockRestore()
    }
  })

  it('propagates a backend 401/403 rejection so unauthenticated requests stay unauthorized', async () => {
    const err = Object.assign(new Error('Forbidden'), { statusCode: 403 })
    proxyToBackendMock.mockRejectedValueOnce(err)

    await expect(createApiKeyHandler(fakeEvent)).rejects.toBe(err)
  })
})

describe('dsl/auth/keys/[id]/index.delete', () => {
  it('interpolates the :id router param and DELETEs to /api/dsl/auth/keys/{id}', async () => {
    routerParams = { id: 'k1' }

    await revokeApiKeyHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys/k1', {
      method: 'DELETE',
    })
  })

  it('maps the backend 204 (revoked) and 404 (unknown id) statuses verbatim', async () => {
    // Status mapping is proxyToBackend's job (h3 returns the handler result
    // with the backend status); the route must not reshape it.
    routerParams = { id: 'k1' }
    proxyToBackendMock.mockResolvedValueOnce(null)

    const result = await revokeApiKeyHandler(fakeEvent)

    expect(result).toBeNull()
  })

  it('propagates a backend 401/403 rejection so unauthenticated requests stay unauthorized', async () => {
    routerParams = { id: 'k1' }
    const err = Object.assign(new Error('Unauthorized'), { statusCode: 401 })
    proxyToBackendMock.mockRejectedValueOnce(err)

    await expect(revokeApiKeyHandler(fakeEvent)).rejects.toBe(err)
  })
})

describe('dsl/definitions/export.get', () => {
  it('GETs /api/dsl/definitions/export forwarding the include query param', async () => {
    queryValue = { include: 'drafts' }

    await exportDefinitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/definitions/export', {
      query: { include: 'drafts' },
    })
  })

  it('forwards an empty query object when no params are present', async () => {
    queryValue = {}

    await exportDefinitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/definitions/export', {
      query: {},
    })
  })

  it('returns the backend bundle verbatim', async () => {
    const payload = {
      formatVersion: 1,
      engineVersion: '1.0.0',
      exportedAt: '2026-01-01T00:00:00Z',
      definitions: [{ definition: { name: 'A' }, source: 'published' }],
    }
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await exportDefinitionsHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/definitions/import.post', () => {
  it('POSTs the bundle body and forwards the dryRun query param', async () => {
    queryValue = { dryRun: 'true' }
    bodyValue = {
      formatVersion: 1,
      definitions: [{ definition: { name: 'A' }, source: 'published' }],
    }

    await importDefinitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/definitions/import', {
      method: 'POST',
      body: bodyValue,
      query: { dryRun: 'true' },
    })
  })

  it('POSTs the bundle body without query params when dryRun is absent', async () => {
    queryValue = {}
    bodyValue = { formatVersion: 1, definitions: [] }

    await importDefinitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/definitions/import', {
      method: 'POST',
      body: bodyValue,
      query: {},
    })
  })
})

describe('executions/export.get', () => {
  it('proxies to the backend CSV endpoint with renamed entityName filter', async () => {
    queryValue = { status: 'Completed', entityName: 'Loan', mode: 'RUN', correlationId: 'c1' }

    await executionsExportHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/export.csv', {
      query: {
        status: 'Completed',
        processName: 'Loan',
        mode: 'RUN',
        correlationId: 'c1',
      },
      raw: true,
      forwardResponseHeaders: ['content-type', 'content-disposition', 'x-export-truncated'],
    })
  })

  it('forwards only the query params that are present', async () => {
    queryValue = {}

    await executionsExportHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions/export.csv', {
      query: {},
      raw: true,
      forwardResponseHeaders: ['content-type', 'content-disposition', 'x-export-truncated'],
    })
  })
})
describe('dsl/files/index.get', () => {
  it('GETs the backend files list path', async () => {
    await listDslFilesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/files/by-name/[name].get', () => {
  it('interpolates the :name router param and GETs the backend by-name path', async () => {
    routerParams = { name: 'LoanDsl' }

    await readDslFileByNameHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/by-name/LoanDsl')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/files/by-name/[name].post', () => {
  it('interpolates the :name router param and POSTs the body to the by-name path', async () => {
    routerParams = { name: 'LoanDsl' }
    bodyValue = { content: 'public class LoanDsl {}' }

    await writeDslFileByNameHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/by-name/LoanDsl', {
      method: 'POST',
      body: { content: 'public class LoanDsl {}' },
    })
  })
})

describe('dsl/files/[...path].get', () => {
  it('interpolates the :path catch-all param and GETs the backend files path', async () => {
    routerParams = { path: ['flows', 'LoanDsl.java'] }

    await readDslFilePathHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/flows/LoanDsl.java')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('joins a single-segment path without a separator', async () => {
    routerParams = { path: 'LoanDsl.java' }

    await readDslFilePathHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/LoanDsl.java')
  })
})

describe('dsl/files/[...path].post', () => {
  it('interpolates the :path catch-all param and POSTs the body to the backend files path', async () => {
    routerParams = { path: ['flows', 'LoanDsl.java'] }
    bodyValue = { content: 'public class LoanDsl {}' }

    await writeDslFilePathHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/files/flows/LoanDsl.java',
      {
        method: 'POST',
        body: { content: 'public class LoanDsl {}' },
      },
    )
  })
})

describe('dsl/files/bulk.post', () => {
  it('POSTs the body to the backend bulk path', async () => {
    bodyValue = { writes: [{ path: 'LoanDsl.java', content: 'class A {}' }] }

    await bulkWriteDslFilesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/bulk', {
      method: 'POST',
      body: { writes: [{ path: 'LoanDsl.java', content: 'class A {}' }] },
    })
  })
})

describe('dsl/files/flush.post', () => {
  it('POSTs to the backend flush path', async () => {
    await flushDslFilesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/flush', {
      method: 'POST',
    })
  })
})

describe('dsl/files/status.get', () => {
  it('GETs the backend files status path', async () => {
    await dslFileStatusHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/files/status')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/definitions/[name]/tests/index.get', () => {
  it('interpolates the :name router param and GETs the backend tests path', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await listDefinitionTestsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/definitions/LoanDisbursement/tests',
    )
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })
})

describe('dsl/definitions/[name]/tests/index.put', () => {
  it('interpolates the :name router param and PUTs the body to the backend tests path', async () => {
    routerParams = { name: 'LoanDisbursement' }
    bodyValue = [{ caseName: 'happy', input: { x: 1 }, expectedOutput: { y: 2 } }]

    await replaceDefinitionTestsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/definitions/LoanDisbursement/tests',
      {
        method: 'PUT',
        body: [{ caseName: 'happy', input: { x: 1 }, expectedOutput: { y: 2 } }],
      },
    )
  })
})

describe('dsl/definitions/[name]/tests/run.post', () => {
  it('interpolates the :name router param and POSTs without query params when no case subset is provided', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await runDefinitionTestsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/definitions/LoanDisbursement/tests/run',
      { method: 'POST', query: {} },
    )
  })

  it('forwards a single case subset query param', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = { case: 'happy' }

    await runDefinitionTestsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/definitions/LoanDisbursement/tests/run',
      { method: 'POST', query: { case: ['happy'] } },
    )
  })

  it('forwards multiple repeated case query params', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryValue = { case: ['happy', 'edge'] }

    await runDefinitionTestsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/definitions/LoanDisbursement/tests/run',
      { method: 'POST', query: { case: ['happy', 'edge'] } },
    )
  })
})
