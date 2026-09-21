import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyRequestMock } = vi.hoisted(() => ({
  proxyRequestMock: vi.fn(),
}))

const { useBackendConfigMock, buildBackendHeadersMock, attachAuthMock } = vi.hoisted(() => ({
  useBackendConfigMock: vi.fn(() => ({
    baseUrl: 'http://localhost:8090',
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
    getRouterParam: (_event: unknown, name: string) => (name === 'traceId' ? 'trace-1' : undefined),
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

const handler = (await import('../[traceId]/logs.get')).default

const fakeEvent = {} as Parameters<typeof proxyRequestMock>[0]

describe('dsl/dry-run/[traceId]/logs.get', () => {
  beforeEach(() => {
    proxyRequestMock.mockReset()
    proxyRequestMock.mockResolvedValue(undefined)
    buildBackendHeadersMock.mockClear()
    attachAuthMock.mockClear()
  })

  it('proxies to the backend dry-run log SSE endpoint with auth headers', async () => {
    await handler(fakeEvent)

    expect(useBackendConfigMock).toHaveBeenCalled()
    expect(buildBackendHeadersMock).toHaveBeenCalledWith(fakeEvent, { json: false })
    expect(attachAuthMock).toHaveBeenCalledWith(fakeEvent, { 'x-request-id': 'req-1' })
    expect(proxyRequestMock).toHaveBeenCalledTimes(1)
    expect(proxyRequestMock).toHaveBeenCalledWith(
      fakeEvent,
      'http://localhost:8090/api/dsl/dry-run/trace-1/logs',
      {
        fetchOptions: {
          headers: { 'x-request-id': 'req-1' },
        },
      },
    )
  })
})
