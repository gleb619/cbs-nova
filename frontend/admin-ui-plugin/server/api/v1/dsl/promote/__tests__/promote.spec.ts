import { beforeEach, describe, expect, it, vi } from 'vitest'

const { proxyToBackendMock } = vi.hoisted(() => ({
  proxyToBackendMock: vi.fn(),
}))

vi.mock('~/server/utils/httpClient', () => ({
  proxyToBackend: proxyToBackendMock,
}))

let queryValue: Record<string, unknown> = {}
let bodyValue: unknown

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getQuery: (_event: unknown) => queryValue,
    readBody: async (_event: unknown) => bodyValue,
  }
})

const environmentsHandler = (await import('../environments.get')).default
const definitionsHandler = (await import('../definitions.get')).default
const promoteHandler = (await import('../index.post')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue([])
  queryValue = {}
  bodyValue = undefined
})

describe('dsl/promote environments GET (T569)', () => {
  it('proxies GET /api/dsl/promote/environments', async () => {
    await environmentsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/promote/environments')
  })
})

describe('dsl/promote definitions GET (T569)', () => {
  it('forwards without query params when env is absent', async () => {
    await definitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/promote/definitions')
  })

  it('forwards the env query param', async () => {
    queryValue = { env: 'staging' }

    await definitionsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/promote/definitions', {
      query: { env: 'staging' },
    })
  })
})

describe('dsl/promote POST (T569)', () => {
  it('posts the promotion request body with dryRun query passthrough', async () => {
    queryValue = { dryRun: 'true' }
    bodyValue = { source: 'dev', target: 'staging', definitions: ['LoanDsl'] }

    await promoteHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/promote', {
      method: 'POST',
      body: { source: 'dev', target: 'staging', definitions: ['LoanDsl'] },
      query: { dryRun: 'true' },
    })
  })

  it('applies without a dryRun query param by default', async () => {
    bodyValue = { source: 'dev', target: 'staging' }

    await promoteHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/promote', {
      method: 'POST',
      body: { source: 'dev', target: 'staging' },
      query: {},
    })
  })
})
