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
let bodyValue: unknown

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getRouterParam: (_event: unknown, name: string) => routerParams[name],
    getQuery: () => queryParams,
    readBody: async () => bodyValue,
  }
})

const listRulesHandler = (await import('../rules/index.get')).default
const createRuleHandler = (await import('../rules/index.post')).default
const readRuleHandler = (await import('../rules/[id].get')).default
const updateRuleHandler = (await import('../rules/[id].put')).default
const deleteRuleHandler = (await import('../rules/[id].delete')).default
const enabledRuleHandler = (await import('../rules/[id]/enabled.post')).default
const channelsHandler = (await import('../channels/index.get')).default
const fireLogHandler = (await import('../fire-log/index.get')).default
const testHandler = (await import('../test/index.post')).default

const fakeEvent = {} as Parameters<typeof proxyToBackendMock>[0]

beforeEach(() => {
  proxyToBackendMock.mockReset()
  proxyToBackendMock.mockResolvedValue({})
  routerParams = {}
  queryParams = {}
  bodyValue = undefined
})

describe('dsl/notifications rules', () => {
  it('list forwards GET /api/dsl/notifications/rules without params', async () => {
    await listRulesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules')
  })

  it('list forwards offset/limit query params', async () => {
    queryParams = { offset: '25', limit: '25' }

    await listRulesHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules', {
      query: { offset: '25', limit: '25' },
    })
  })

  it('create forwards POST /api/dsl/notifications/rules with the body', async () => {
    bodyValue = { name: 'rule-1' }

    await createRuleHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules', {
      method: 'POST',
      body: { name: 'rule-1' },
    })
  })

  it('read forwards GET /api/dsl/notifications/rules/{id}', async () => {
    routerParams = { id: '7' }

    await readRuleHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules/7')
  })

  it('update forwards PUT /api/dsl/notifications/rules/{id} with the body', async () => {
    routerParams = { id: '7' }
    bodyValue = { name: 'rule-1b' }

    await updateRuleHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules/7', {
      method: 'PUT',
      body: { name: 'rule-1b' },
    })
  })

  it('delete forwards DELETE /api/dsl/notifications/rules/{id}', async () => {
    routerParams = { id: '7' }

    await deleteRuleHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/rules/7', {
      method: 'DELETE',
    })
  })

  it('enabled forwards POST /api/dsl/notifications/rules/{id}/enabled with the body', async () => {
    routerParams = { id: '7' }
    bodyValue = { enabled: false }

    await enabledRuleHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(
      fakeEvent,
      '/api/dsl/notifications/rules/7/enabled',
      {
        method: 'POST',
        body: { enabled: false },
      },
    )
  })
})

describe('dsl/notifications channels / fire-log / test', () => {
  it('channels forwards GET /api/dsl/notifications/channels', async () => {
    await channelsHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/channels')
  })

  it('fire-log forwards offset/limit/ruleId query params', async () => {
    queryParams = { offset: '0', limit: '25', ruleId: '7' }

    await fireLogHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/fire-log', {
      query: { offset: '0', limit: '25', ruleId: '7' },
    })
  })

  it('fire-log forwards without params', async () => {
    await fireLogHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/fire-log')
  })

  it('test forwards POST /api/dsl/notifications/test with the body', async () => {
    bodyValue = { eventType: 'RunFailed' }

    await testHandler(fakeEvent)

    expect(proxyToBackendMock).toHaveBeenCalledTimes(1)
    expect(proxyToBackendMock).toHaveBeenCalledWith(fakeEvent, '/api/dsl/notifications/test', {
      method: 'POST',
      body: { eventType: 'RunFailed' },
    })
  })
})
