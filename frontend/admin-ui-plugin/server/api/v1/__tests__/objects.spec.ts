import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let queryValue: Record<string, unknown> = {}

type RouterParamMap = Record<string, string | undefined>
let routerParams: RouterParamMap = {}

const g = globalThis as Record<string, unknown>
g.getRouterParam = (_event: unknown, name: string) => routerParams[name]
g.readBody = async (_event: unknown) => ({})
g.getQuery = (_event: unknown) => queryValue

const searchHandler = (await import('../dsl/objects/search.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
  routerParams = {}
})

describe('dsl/objects/search.get', () => {
  it('forwards name, type, and description query params to /api/dsl/objects/search', async () => {
    queryValue = { name: 'Foo', type: 'helper', description: 'bar baz' }

    await searchHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/objects/search', {
      query: { name: 'Foo', type: 'helper', description: 'bar baz' },
    })
  })

  it('omits blank query params from the backend request', async () => {
    queryValue = { name: '  ', type: '', description: 'valid' }

    await searchHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/objects/search', {
      query: { description: 'valid' },
    })
  })

  it('sends empty query object when no filters provided', async () => {
    queryValue = {}

    await searchHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/objects/search', {
      query: {},
    })
  })

  it('ignores unexpected query params', async () => {
    queryValue = { name: 'Helper', unknown: 'ignored' }

    await searchHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/objects/search', {
      query: { name: 'Helper' },
    })
  })
})
