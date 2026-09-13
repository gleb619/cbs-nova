import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useDslApi } from '../useDslApi'

describe('useDslApi', () => {
  const fetchMock = vi.mocked($fetch)

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(undefined)
  })

  it('getDefinitions calls GET /api/v1/dsl/definitions', async () => {
    fetchMock.mockResolvedValueOnce([])

    const api = useDslApi()
    await api.getDefinitions()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions')
  })

  it('listHelpers GETs /api/v1/dsl/helpers', async () => {
    fetchMock.mockResolvedValueOnce({ names: [], helpers: [] })

    const api = useDslApi()
    const result = await api.listHelpers()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/helpers')
    expect(result).toEqual({ names: [], helpers: [] })
  })

  it('searchObjects GETs /api/v1/dsl/objects/search with all filters', async () => {
    fetchMock.mockResolvedValueOnce([])

    const api = useDslApi()
    await api.searchObjects({ name: 'Foo', type: 'helper', description: 'bar' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/objects/search', {
      query: { name: 'Foo', type: 'helper', description: 'bar' },
    })
  })

  it('searchObjects omits blank filters from the query', async () => {
    fetchMock.mockResolvedValueOnce([])

    const api = useDslApi()
    await api.searchObjects({ name: '', type: 'process' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/objects/search', {
      query: { type: 'process' },
    })
  })

  it('preview POSTs to /api/v1/dsl/preview/{name} with body and metadata', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    const body = { foo: 'bar' }
    const metadata = { source: 'test' }

    await api.preview('myDef', body, metadata)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/preview/myDef', {
      method: 'POST',
      body: { body, metadata },
    })
  })

  it('run POSTs to /api/v1/dsl/run/{name}', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    await api.run('myDef', { a: 1 }, { tag: 'x' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/run/myDef', {
      method: 'POST',
      body: { body: { a: 1 }, metadata: { tag: 'x' } },
    })
  })

  it('explain POSTs to /api/v1/dsl/explain/{name}', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    await api.explain('myDef', null, undefined)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/explain/myDef', {
      method: 'POST',
      body: { body: null, metadata: undefined },
    })
  })

  it('reload POSTs to /api/v1/dsl/reload', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()

    await api.reload()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/reload', { method: 'POST' })
  })

  it('saveDraft POSTs the draft payload to /api/v1/dsl/drafts/{name}/save', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const result = await api.saveDraft('draft-1', { name: 'draft-1', type: 'process' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/save', {
      method: 'POST',
      body: { name: 'draft-1', type: 'process' },
    })
    expect(result).toEqual({ ok: true })
  })

  it('publishDraft POSTs the construct payload to /api/v1/dsl/drafts/{name}/publish', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    await api.publishDraft('c1', { name: 'c1', type: 'helper', status: 'Published' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/c1/publish', {
      method: 'POST',
      body: { name: 'c1', type: 'helper', status: 'Published' },
    })
  })

  it('deleteDraft DELETEs to /api/v1/dsl/drafts/{name}/delete', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const result = await api.deleteDraft('draft-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/delete', {
      method: 'DELETE',
    })
    expect(result).toEqual({ ok: true })
  })

  it('listDrafts GETs /api/v1/dsl/drafts and unwraps the envelope', async () => {
    fetchMock.mockResolvedValueOnce({
      items: [{ name: 'draft-1', type: 'process' }],
      total: 1,
      offset: 0,
      limit: 50,
    })
    const api = useDslApi()
    const result = await api.listDrafts()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts')
    expect(result).toEqual([{ name: 'draft-1', type: 'process' }])
  })

  it('listDrafts still tolerates a legacy array response', async () => {
    fetchMock.mockResolvedValueOnce([{ name: 'legacy-draft', type: 'helper' }])
    const api = useDslApi()
    const result = await api.listDrafts()

    expect(result).toEqual([{ name: 'legacy-draft', type: 'helper' }])
  })

  it('readDraft GETs /api/v1/dsl/drafts/{name}', async () => {
    fetchMock.mockResolvedValueOnce({ name: 'draft-1', type: 'helper' })
    const api = useDslApi()
    const result = await api.readDraft('draft-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1')
    expect(result).toEqual({ name: 'draft-1', type: 'helper' })
  })

  it('listPublishHistory GETs /api/v1/dsl/drafts/{name}/history', async () => {
    fetchMock.mockResolvedValueOnce([{ timestamp: '123', timestampMillis: 123 }])
    const api = useDslApi()
    const result = await api.listPublishHistory('draft-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/history')
    expect(result).toEqual([{ timestamp: '123', timestampMillis: 123 }])
  })

  it('restorePublishHistory POSTs to /api/v1/dsl/drafts/{name}/history/{timestamp}/restore', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const result = await api.restorePublishHistory('draft-1', '123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/history/123/restore', {
      method: 'POST',
    })
    expect(result).toEqual({ ok: true })
  })

  it('getHistoryEntry GETs /api/v1/dsl/drafts/{name}/history/{timestamp}', async () => {
    fetchMock.mockResolvedValueOnce({ name: 'draft-1', version: 'A' })
    const api = useDslApi()
    const result = await api.getHistoryEntry('draft-1', '123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/history/123')
    expect(result).toEqual({ name: 'draft-1', version: 'A' })
  })

  it('getHistoryDiff GETs /api/v1/dsl/drafts/{name}/history/{timestamp}/diff', async () => {
    fetchMock.mockResolvedValueOnce({ name: 'draft-1', timestamp: '123', hunks: [] })
    const api = useDslApi()
    const result = await api.getHistoryDiff('draft-1', '123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1/history/123/diff')
    expect(result).toEqual({ name: 'draft-1', timestamp: '123', hunks: [] })
  })

  it('fetchDefinitionTests GETs /api/v1/dsl/definitions/{name}/tests and unwraps the envelope', async () => {
    const cases = [{ caseName: 'happy', input: { body: {} }, expectedOutput: { result: 1 } }]
    fetchMock.mockResolvedValueOnce({ items: cases, total: 1 })
    const api = useDslApi()

    const result = await api.fetchDefinitionTests('OrderProcess')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/OrderProcess/tests')
    expect(result).toEqual(cases)
  })

  it('fetchDefinitionTests tolerates a bare array response', async () => {
    const cases = [{ caseName: 'happy', input: null, expectedOutput: null }]
    fetchMock.mockResolvedValueOnce(cases)
    const api = useDslApi()

    const result = await api.fetchDefinitionTests('OrderProcess')

    expect(result).toEqual(cases)
  })

  it('fetchDefinitionTests maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({
      data: { message: 'definition not found' },
      statusCode: 404,
    })
    const api = useDslApi()

    await expect(api.fetchDefinitionTests('Nope')).rejects.toThrow('definition not found')
  })

  it('saveDefinitionTests PUTs the whole case set to /api/v1/dsl/definitions/{name}/tests', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const cases = [
      { caseName: 'a', input: { body: { x: 1 } }, expectedOutput: { result: 1 } },
      { caseName: 'b', input: { body: { x: 2 } }, expectedOutput: { result: 2 } },
    ]

    const result = await api.saveDefinitionTests('OrderProcess', cases)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/OrderProcess/tests', {
      method: 'PUT',
      body: cases,
    })
    expect(result).toEqual({ ok: true })
  })

  it('saveDefinitionTests maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({ data: { message: 'write failed' }, statusCode: 500 })
    const api = useDslApi()

    await expect(
      api.saveDefinitionTests('OrderProcess', [{ caseName: 'a', input: {}, expectedOutput: {} }]),
    ).rejects.toThrow('write failed')
  })

  it('runDefinitionTests POSTs without case filters by default', async () => {
    const report = { total: 1, passed: 1, failed: 0, errored: 0, cases: [] }
    fetchMock.mockResolvedValueOnce(report)
    const api = useDslApi()

    const result = await api.runDefinitionTests('OrderProcess')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/OrderProcess/tests/run', {
      method: 'POST',
      query: {},
    })
    expect(result).toEqual(report)
  })

  it('runDefinitionTests forwards selected case names as repeated query params', async () => {
    fetchMock.mockResolvedValueOnce({ total: 0, passed: 0, failed: 0, errored: 0, cases: [] })
    const api = useDslApi()

    await api.runDefinitionTests('OrderProcess', ['happy path', 'edge case'])

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/OrderProcess/tests/run', {
      method: 'POST',
      query: { case: ['happy path', 'edge case'] },
    })
  })

  it('runDefinitionTests maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({ data: { message: 'engine offline' }, statusCode: 503 })
    const api = useDslApi()

    await expect(api.runDefinitionTests('OrderProcess')).rejects.toThrow('engine offline')
  })

  it('fetchEvents GETs /api/v1/dsl/events with limit and offset', async () => {
    const envelope = { items: [], total: 0, offset: 0, limit: 25 }
    fetchMock.mockResolvedValueOnce(envelope)
    const api = useDslApi()
    const result = await api.fetchEvents({ limit: 25, offset: 0 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/events', {
      query: { limit: '25', offset: '0' },
    })
    expect(result).toEqual(envelope)
  })

  it('fetchEvents forwards all supported filters', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchEvents({
      type: 'RunCompleted',
      aggregateType: 'run',
      aggregateId: 'run-abc',
      correlationId: 'corr-1',
      since: '2026-09-13T00:00:00Z',
      limit: 25,
      offset: 25,
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/events', {
      query: {
        limit: '25',
        offset: '25',
        type: 'RunCompleted',
        aggregateType: 'run',
        aggregateId: 'run-abc',
        correlationId: 'corr-1',
        since: '2026-09-13T00:00:00Z',
      },
    })
  })

  it('fetchEvents omits blank filters from the query', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchEvents({
      type: '   ',
      aggregateId: '',
      correlationId: '  ',
      limit: 25,
      offset: 0,
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/events', {
      query: { limit: '25', offset: '0' },
    })
  })

  it('fetchEvents maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({
      data: { message: 'event store unavailable' },
      statusCode: 500,
    })
    const api = useDslApi()

    await expect(api.fetchEvents({ limit: 25, offset: 0 })).rejects.toThrow(
      'event store unavailable',
    )
  })

  it('fetchDiagnostics GETs /api/v1/dsl/diagnostics with limit and offset', async () => {
    const envelope = { items: [], total: 0, offset: 25, limit: 25 }
    fetchMock.mockResolvedValueOnce(envelope)
    const api = useDslApi()
    const result = await api.fetchDiagnostics({ limit: 25, offset: 25 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/diagnostics', {
      query: { limit: '25', offset: '25' },
    })
    expect(result).toEqual(envelope)
  })

  it('fetchDiagnostics forwards a non-blank definition filter', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchDiagnostics({ definition: 'OrderProcess', limit: 25, offset: 0 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/diagnostics', {
      query: { limit: '25', offset: '0', definition: 'OrderProcess' },
    })
  })

  it('fetchDiagnostics omits a blank definition filter from the query', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchDiagnostics({ definition: '   ', limit: 25, offset: 0 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/diagnostics', {
      query: { limit: '25', offset: '0' },
    })
  })

  it('fetchDiagnostics maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({
      data: { message: 'diagnostics store unavailable' },
      statusCode: 500,
    })
    const api = useDslApi()

    await expect(api.fetchDiagnostics({ limit: 25, offset: 0 })).rejects.toThrow(
      'diagnostics store unavailable',
    )
  })

  it('fetchWebhookDeliveries GETs /api/v1/dsl/webhooks/deliveries with limit and offset', async () => {
    const envelope = { items: [], total: 0, offset: 25, limit: 25 }
    fetchMock.mockResolvedValueOnce(envelope)
    const api = useDslApi()
    const result = await api.fetchWebhookDeliveries({ limit: 25, offset: 25 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/webhooks/deliveries', {
      query: { limit: '25', offset: '25' },
    })
    expect(result).toEqual(envelope)
  })

  it('fetchWebhookDeliveries forwards a non-blank subscriptionId filter', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchWebhookDeliveries({ subscriptionId: 'OrderProcess', limit: 25, offset: 0 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/webhooks/deliveries', {
      query: { limit: '25', offset: '0', subscriptionId: 'OrderProcess' },
    })
  })

  it('fetchWebhookDeliveries omits a blank subscriptionId filter from the query', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0, offset: 0, limit: 25 })
    const api = useDslApi()
    await api.fetchWebhookDeliveries({ subscriptionId: '   ', limit: 25, offset: 0 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/webhooks/deliveries', {
      query: { limit: '25', offset: '0' },
    })
  })

  it('fetchWebhookDeliveries maps BFF errors to a normalized message', async () => {
    fetchMock.mockRejectedValueOnce({
      data: { message: 'delivery store unavailable' },
      statusCode: 500,
    })
    const api = useDslApi()

    await expect(api.fetchWebhookDeliveries({ limit: 25, offset: 0 })).rejects.toThrow(
      'delivery store unavailable',
    )
  })

  it('validateConstruct delegates to preview with empty body', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    await api.validateConstruct('myDef')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/preview/myDef', {
      method: 'POST',
      body: { body: {}, metadata: undefined },
    })
  })

  it('omits metadata wrapper keys from body when not provided', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    await api.preview('myDef', { x: 1 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/preview/myDef', {
      method: 'POST',
      body: { body: { x: 1 }, metadata: undefined },
    })
  })

  it('run forwards optional headers', async () => {
    fetchMock.mockResolvedValueOnce({})
    const api = useDslApi()

    await api.run('myDef', { a: 1 }, { tag: 'x' }, { 'Idempotency-Key': 'idem-1' })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/run/myDef', {
      method: 'POST',
      body: { body: { a: 1 }, metadata: { tag: 'x' } },
      headers: { 'Idempotency-Key': 'idem-1' },
    })
  })

  it('exportDefinitions GETs /api/v1/dsl/definitions/export without query by default', async () => {
    fetchMock.mockResolvedValueOnce({ formatVersion: 1, definitions: [] })
    const api = useDslApi()

    await api.exportDefinitions()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/export', { query: {} })
  })

  it('exportDefinitions forwards include=drafts when requested', async () => {
    fetchMock.mockResolvedValueOnce({ formatVersion: 1, definitions: [] })
    const api = useDslApi()

    await api.exportDefinitions(true)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/export', {
      query: { include: 'drafts' },
    })
  })

  it('importDefinitions POSTs the bundle without dryRun by default', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const bundle = { formatVersion: 1, definitions: [{ definition: { name: 'A' } }] }

    await api.importDefinitions(bundle)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/import', {
      method: 'POST',
      body: bundle,
      query: {},
    })
  })

  it('importDefinitions forwards dryRun=true when requested', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()
    const bundle = { formatVersion: 1, definitions: [] }

    await api.importDefinitions(bundle, true)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/definitions/import', {
      method: 'POST',
      body: bundle,
      query: { dryRun: 'true' },
    })
  })

  it('readDslFile GETs /api/v1/dsl/files/by-name/{name} and returns content', async () => {
    fetchMock.mockResolvedValueOnce({ path: 'LoanDsl.java', content: 'class A {}', pending: false })
    const api = useDslApi()

    const result = await api.readDslFile('LoanDsl')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/files/by-name/LoanDsl')
    expect(result).toBe('class A {}')
  })

  it('writeDslFile POSTs content to /api/v1/dsl/files/by-name/{name}', async () => {
    fetchMock.mockResolvedValueOnce({ ok: true })
    const api = useDslApi()

    const result = await api.writeDslFile('LoanDsl', 'public class LoanDsl {}')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/files/by-name/LoanDsl', {
      method: 'POST',
      body: { content: 'public class LoanDsl {}' },
    })
    expect(result).toEqual({ ok: true })
  })
})
