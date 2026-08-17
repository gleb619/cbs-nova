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

  it('saveDraft resolves with stub payload and does not call $fetch', async () => {
    const api = useDslApi()
    const result = await api.saveDraft('draft-1', { hello: 'world' })

    expect(fetchMock).not.toHaveBeenCalled()
    expect(result).toEqual({ success: true, name: 'draft-1', payload: { hello: 'world' } })
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
})
