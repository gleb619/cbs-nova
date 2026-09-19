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

const listHandler = (await import('../index.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
})

describe('dsl/change-requests GET (T568)', () => {
  it('forwards GET /api/dsl/change-requests with no query params by default', async () => {
    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/change-requests')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards the definitionName and status query params', async () => {
    queryValue = { definitionName: 'LoanDsl', status: 'PENDING' }

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/change-requests', {
      query: { definitionName: 'LoanDsl', status: 'PENDING' },
    })
  })

  it('forwards only the status query param when definitionName is absent', async () => {
    queryValue = { status: 'APPROVED' }

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/change-requests', {
      query: { status: 'APPROVED' },
    })
  })
})
