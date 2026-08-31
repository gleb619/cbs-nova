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

const listHandler = (await import('../executions/index.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
})

describe('executions/index.get', () => {
  it('forwards paging and filter query params to /api/executions', async () => {
    queryValue = {
      offset: '20',
      limit: '10',
      status: 'Completed',
      mode: 'RUN',
      entityName: 'Loan',
      correlationId: 'corr-123',
    }

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: {
        offset: '20',
        limit: '10',
        status: 'Completed',
        mode: 'RUN',
        processName: 'Loan',
        correlationId: 'corr-123',
      },
    })
  })

  it('forwards correlationId on its own', async () => {
    queryValue = { correlationId: 'corr-abc' }

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions', {
      query: { correlationId: 'corr-abc' },
    })
  })

  it('proxies without query when no known params are provided', async () => {
    queryValue = {}

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/executions')
  })
})
