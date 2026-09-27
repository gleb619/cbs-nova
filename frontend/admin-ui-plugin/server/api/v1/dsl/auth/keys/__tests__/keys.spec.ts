import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock, setResponseHeaderMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
  setResponseHeaderMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let bodyValue: unknown

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    readBody: async (_event: unknown) => bodyValue,
    setResponseHeader: setResponseHeaderMock,
  }
})

const handler = (await import('../index.post')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  setResponseHeaderMock.mockClear()
  bodyValue = undefined
})

describe('dsl/auth/keys create POST', () => {
  it('sets cache-control: no-store', async () => {
    proxyToBackendMock.mockResolvedValue({ apiKey: 'secret' })

    await handler(fakeEvent)

    expect(setResponseHeaderMock).toHaveBeenCalledWith(fakeEvent, 'cache-control', 'no-store')
  })

  it('forwards the read body unchanged via POST', async () => {
    bodyValue = { name: 'ci-key' }
    proxyToBackendMock.mockResolvedValue({ apiKey: 'secret' })

    await handler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys', {
      method: 'POST',
      body: { name: 'ci-key' },
    })
  })

  it('returns whatever proxyToBackend resolves to', async () => {
    const backendResult = { apiKey: 'plaintext-once' }
    proxyToBackendMock.mockResolvedValue(backendResult)

    const result = await handler(fakeEvent)

    expect(result).toBe(backendResult)
  })
})
