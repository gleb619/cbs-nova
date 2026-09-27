import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let queryValue: Record<string, unknown> = {}
let bodyValue: unknown
let runIdValue: string | undefined

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getQuery: (_event: unknown) => queryValue,
    getRouterParam: (_event: unknown, name: string) => (name === 'runId' ? runIdValue : undefined),
    readBody: async (_event: unknown) => bodyValue,
  }
})

const indexHandler = (await import('../index.get')).default
const getHandler = (await import('../[runId].get')).default
const deleteHandler = (await import('../[runId].delete')).default
const replayHandler = (await import('../[runId]/replay.post')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue(undefined)
  queryValue = {}
  bodyValue = undefined
  runIdValue = 'run-1'
})

describe('vhs/tapes index GET', () => {
  it('proxies with no query option when no params given', async () => {
    await indexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes')
  })

  it('proxies with only the provided params, stringified', async () => {
    queryValue = { offset: 10, limit: '25', route: '/api/foo' }

    await indexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes', {
      query: { offset: '10', limit: '25', route: '/api/foo' },
    })
  })

  it('includes since when provided', async () => {
    queryValue = { since: '2026-01-01T00:00:00Z' }

    await indexHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes', {
      query: { since: '2026-01-01T00:00:00Z' },
    })
  })
})

describe('vhs/tapes [runId] GET', () => {
  it('proxies to the tape detail endpoint with raw: true', async () => {
    await getHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes/run-1', {
      raw: true,
    })
  })
})

describe('vhs/tapes [runId] DELETE', () => {
  it('proxies with method: DELETE', async () => {
    await deleteHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes/run-1', {
      method: 'DELETE',
    })
  })
})

describe('vhs/tapes [runId]/replay POST', () => {
  it('reads the body and proxies with method: POST and the body forwarded', async () => {
    bodyValue = { speed: 2 }

    await replayHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/v1/vhs/tapes/run-1/replay', {
      method: 'POST',
      body: { speed: 2 },
    })
  })
})
