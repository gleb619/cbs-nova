import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

type RouterParamMap = Record<string, string | undefined>
let routerParams: RouterParamMap = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
  }
})

const listHandler = (await import('../index.get')).default
const readHandler = (await import('../[name]/index.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  routerParams = {}
})

describe('dsl/drafts GET', () => {
  it('list forwards GET /api/dsl/drafts', async () => {
    await listHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts')
  })

  it('read forwards GET /api/dsl/drafts/{name} with the route param', async () => {
    routerParams = { name: 'draft-1' }

    await readHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/drafts/draft-1')
  })
})