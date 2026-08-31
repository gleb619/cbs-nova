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

  it('listDrafts GETs /api/v1/dsl/drafts', async () => {
    fetchMock.mockResolvedValueOnce([{ name: 'draft-1', type: 'process' }])
    const api = useDslApi()
    const result = await api.listDrafts()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts')
    expect(result).toEqual([{ name: 'draft-1', type: 'process' }])
  })

  it('readDraft GETs /api/v1/dsl/drafts/{name}', async () => {
    fetchMock.mockResolvedValueOnce({ name: 'draft-1', type: 'helper' })
    const api = useDslApi()
    const result = await api.readDraft('draft-1')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/drafts/draft-1')
    expect(result).toEqual({ name: 'draft-1', type: 'helper' })
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
})
