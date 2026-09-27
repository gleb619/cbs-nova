import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyRequestMock, useBackendConfigMock, buildBackendHeadersMock, attachAuthMock } =
  vi.hoisted(() => ({
    proxyRequestMock: vi.fn(),
    useBackendConfigMock: vi.fn(() => ({
      baseUrl: 'http://localhost:8090/',
      apiKey: '',
      timeoutMs: 10000,
    })),
    buildBackendHeadersMock: vi.fn(() => ({
      headers: { 'x-request-id': 'req-1' },
      requestId: 'req-1',
      correlationId: 'corr-1',
    })),
    attachAuthMock: vi.fn(),
  }))

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => (name === 'runId' ? 'run-42' : undefined),
    proxyRequest: proxyRequestMock,
  }
})

vi.mock('~/server/utils/config', () => ({
  useBackendConfig: useBackendConfigMock,
}))

vi.mock('~/server/utils/backendHeaders', () => ({
  buildBackendHeaders: buildBackendHeadersMock,
}))

vi.mock('~/server/utils/oidcSession', () => ({
  attachAuth: attachAuthMock,
}))

const handler = (await import('../[runId].post')).default

const fakeEvent = {} as Parameters<typeof proxyRequestMock>[0]

describe('dsl/signals/[runId] POST (T686)', () => {
  beforeEach(() => {
    proxyRequestMock.mockReset()
    proxyRequestMock.mockResolvedValue(undefined)
    buildBackendHeadersMock.mockClear()
    attachAuthMock.mockClear()
  })

  it('proxies POST to the backend signal endpoint for the runId path param', async () => {
    await handler(fakeEvent)

    expect(useBackendConfigMock).toHaveBeenCalled()
    expect(buildBackendHeadersMock).toHaveBeenCalledWith(fakeEvent, { json: true })
    expect(attachAuthMock).toHaveBeenCalledWith(fakeEvent, { 'x-request-id': 'req-1' })
    expect(proxyRequestMock).toHaveBeenCalledTimes(1)
    expect(proxyRequestMock).toHaveBeenCalledWith(
      fakeEvent,
      'http://localhost:8090/api/dsl/signals/run-42',
      {
        fetchOptions: {
          headers: { 'x-request-id': 'req-1' },
        },
      },
    )
  })

  it('strips a trailing slash from the backend base URL', async () => {
    await handler(fakeEvent)

    expect(proxyRequestMock).toHaveBeenCalledWith(
      fakeEvent,
      'http://localhost:8090/api/dsl/signals/run-42',
      expect.anything(),
    )
  })

  it('passes the request body through via proxyRequest without overrides', async () => {
    await handler(fakeEvent)

    // proxyRequest streams the incoming event body untouched; fetchOptions
    // must not add a body/duplex override.
    const options = proxyRequestMock.mock.calls[0][2]
    expect(Object.keys(options.fetchOptions)).toEqual(['headers'])
  })

  it('returns the proxy result unchanged on backend 4xx errors', async () => {
    const notFound = { status: 404, body: 'unknown runId' }
    proxyRequestMock.mockResolvedValue(notFound)

    const result = await handler(fakeEvent)

    expect(result).toBe(notFound)
  })

  it('returns the proxy result unchanged on backend 5xx errors', async () => {
    const badGateway = { status: 502, body: 'upstream down' }
    proxyRequestMock.mockResolvedValue(badGateway)

    const result = await handler(fakeEvent)

    expect(result).toBe(badGateway)
  })
})
