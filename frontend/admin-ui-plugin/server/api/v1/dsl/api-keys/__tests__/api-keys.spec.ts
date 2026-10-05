import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock, setResponseHeaderMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
  setResponseHeaderMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let bodyValue: unknown
let routerParams: Record<string, string | string[] | undefined> = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    readBody: async (_event: unknown) => bodyValue,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
    setResponseHeader: setResponseHeaderMock,
  }
})

const listHandler = (await import('../index.get')).default
const createHandler = (await import('../index.post')).default
const revokeHandler = (await import('../[id].delete')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  routerParams = {}
  setResponseHeaderMock.mockClear()
  bodyValue = undefined
})

describe('dsl/api-keys/index.get', () => {
  it('GETs /api/dsl/auth/keys', async () => {
    proxyToBackendMock.mockResolvedValue([])

    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys')
  })

  it('returns the backend list verbatim', async () => {
    const payload = [{ id: 1, label: 'ci', prefix: 'ak_ci' }]
    proxyToBackendMock.mockResolvedValueOnce(payload)

    const result = await listHandler(fakeEvent)

    expect(result).toEqual(payload)
  })
})

describe('dsl/api-keys/index.post', () => {
  it('POSTs the body to /api/dsl/auth/keys and sets no-store cache header', async () => {
    bodyValue = { label: 'deploy' }
    proxyToBackendMock.mockResolvedValue({ id: 1, label: 'deploy', key: 'secret' })

    await createHandler(fakeEvent)

    expect(setResponseHeaderMock).toHaveBeenCalledWith(fakeEvent, 'cache-control', 'no-store')
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys', {
      method: 'POST',
      body: { label: 'deploy' },
    })
  })

  it('returns the created key verbatim', async () => {
    const created = { id: 1, label: 'deploy', key: 'ak_secret' }
    proxyToBackendMock.mockResolvedValueOnce(created)

    const result = await createHandler(fakeEvent)

    expect(result).toEqual(created)
  })
})

describe('dsl/api-keys/[id].delete', () => {
  it('DELETEs to /api/dsl/auth/keys/{id}', async () => {
    routerParams = { id: '42' }
    proxyToBackendMock.mockResolvedValue(null)

    await revokeHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/auth/keys/42', {
      method: 'DELETE',
    })
  })
})
