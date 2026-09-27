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

const deliveriesHandler = (await import('../deliveries.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
})

describe('dsl/webhooks/deliveries GET (T686)', () => {
  it('forwards GET /api/dsl/webhooks/deliveries with no query params by default', async () => {
    await deliveriesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/webhooks/deliveries')
    expect(proxyToBackendMock.mock.calls[0][2]).toBeUndefined()
  })

  it('forwards the offset and limit pagination params', async () => {
    queryValue = { offset: '20', limit: '10' }

    await deliveriesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/webhooks/deliveries', {
      query: { offset: '20', limit: '10' },
    })
  })

  it('forwards the subscriptionId filter param', async () => {
    queryValue = { subscriptionId: 'sub-1' }

    await deliveriesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/webhooks/deliveries', {
      query: { subscriptionId: 'sub-1' },
    })
  })

  it('stringifies non-string query values before forwarding', async () => {
    queryValue = { offset: 0, limit: 50 }

    await deliveriesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/webhooks/deliveries', {
      query: { offset: '0', limit: '50' },
    })
  })

  it('returns the backend success envelope unchanged', async () => {
    const envelope = { items: [{ id: 'd1' }], total: 1 }
    proxyToBackendMock.mockResolvedValue(envelope)

    const result = await deliveriesHandler(fakeEvent)

    expect(result).toBe(envelope)
  })

  it('propagates backend error statuses as-is', async () => {
    const backendError = Object.assign(new Error('backend 502'), { statusCode: 502 })
    proxyToBackendMock.mockRejectedValue(backendError)

    await expect(deliveriesHandler(fakeEvent)).rejects.toBe(backendError)
  })
})
