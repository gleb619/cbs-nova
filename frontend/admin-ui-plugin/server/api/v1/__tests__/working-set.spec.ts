import { beforeEach, describe, expect, it, vi } from 'vitest'

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

const workingSetHandler = (await import('../dsl/working-set.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
})

describe('dsl/working-set.get', () => {
  it('forwards page, size, query, mode, and type params to /api/dsl/working-set', async () => {
    queryValue = { page: '2', size: '50', query: 'Loan', mode: 'exact', type: 'process' }

    await workingSetHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/working-set', {
      query: { page: '2', size: '50', query: 'Loan', mode: 'exact', type: 'process' },
    })
  })

  it('omits blank query params from the backend request', async () => {
    queryValue = { page: '', size: '  ', query: 'valid', mode: '', type: ' ' }

    await workingSetHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/working-set', {
      query: { query: 'valid' },
    })
  })

  it('sends empty query object when no filters provided', async () => {
    queryValue = {}

    await workingSetHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/working-set', {
      query: {},
    })
  })

  it('ignores unexpected query params', async () => {
    queryValue = { query: 'Helper', unknown: 'ignored' }

    await workingSetHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/working-set', {
      query: { query: 'Helper' },
    })
  })
})
