import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

type RouterParamMap = Record<string, string | undefined>
let routerParams: RouterParamMap = {}
let queryParams: Record<string, string> = {}

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
    getQuery: () => queryParams,
  }
})

const discardHandler = (await import('../../drafts/[name]/discard.post')).default
const commitsHandler = (await import('../../drafts/[name]/commits.get')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  routerParams = {}
  queryParams = {}
})

describe('dsl/drafts git routes', () => {
  it('discard forwards POST /api/dsl/drafts/{name}/discard', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await discardHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/LoanDisbursement/discard',
      { method: 'POST' },
    )
  })

  it('commits forwards GET /api/dsl/drafts/{name}/commits without query when limit absent', async () => {
    routerParams = { name: 'LoanDisbursement' }

    await commitsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/LoanDisbursement/commits',
    )
  })

  it('commits forwards GET /api/dsl/drafts/{name}/commits?limit=10', async () => {
    routerParams = { name: 'LoanDisbursement' }
    queryParams = { limit: '10' }

    await commitsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/drafts/LoanDisbursement/commits?limit=10',
    )
  })
})
