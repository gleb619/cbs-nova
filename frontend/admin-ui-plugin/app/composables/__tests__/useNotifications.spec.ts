import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../useDslApi', () => {
  const fetchNotificationRules = vi.fn()
  const createNotificationRule = vi.fn()
  const updateNotificationRule = vi.fn()
  const deleteNotificationRule = vi.fn()
  const setNotificationRuleEnabled = vi.fn()
  const fetchNotificationFireLog = vi.fn()
  const testNotificationRules = vi.fn()
  return {
    useDslApi: () => ({
      fetchNotificationRules,
      createNotificationRule,
      updateNotificationRule,
      deleteNotificationRule,
      setNotificationRuleEnabled,
      fetchNotificationFireLog,
      testNotificationRules,
    }),
  }
})

vi.mock('../useClientLogger', () => {
  const error = vi.fn()
  const info = vi.fn()
  const warn = vi.fn()
  return {
    useClientLogger: () => ({ error, info, warn }),
  }
})

// eslint-disable-next-line import/first
import * as loggerModule from '../useClientLogger'
// eslint-disable-next-line import/first
import * as apiModule from '../useDslApi'
import { useNotifications } from '../useNotifications'

type ApiMock = {
  fetchNotificationRules: ReturnType<typeof vi.fn>
  createNotificationRule: ReturnType<typeof vi.fn>
  updateNotificationRule: ReturnType<typeof vi.fn>
  deleteNotificationRule: ReturnType<typeof vi.fn>
  setNotificationRuleEnabled: ReturnType<typeof vi.fn>
  fetchNotificationFireLog: ReturnType<typeof vi.fn>
  testNotificationRules: ReturnType<typeof vi.fn>
}

type LoggerMock = {
  error: ReturnType<typeof vi.fn>
  info: ReturnType<typeof vi.fn>
  warn: ReturnType<typeof vi.fn>
}

const getApiMocks = (): ApiMock =>
  (apiModule as unknown as { useDslApi: () => ApiMock }).useDslApi()

const getLoggerMocks = (): LoggerMock =>
  (loggerModule as unknown as { useClientLogger: () => LoggerMock }).useClientLogger()

function rule(id: number) {
  return {
    id,
    name: `rule-${id}`,
    enabled: true,
    eventFilter: { eventType: 'RunFailed' },
    actions: [{ sink: 'webhook', url: 'https://hooks.example.com' }],
    priority: 0,
    rateClass: 'default',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

describe('useNotifications', () => {
  beforeEach(() => {
    const api = getApiMocks()
    api.fetchNotificationRules.mockReset()
    api.createNotificationRule.mockReset()
    api.updateNotificationRule.mockReset()
    api.deleteNotificationRule.mockReset()
    api.setNotificationRuleEnabled.mockReset()
    api.fetchNotificationFireLog.mockReset()
    api.testNotificationRules.mockReset()
    const logger = getLoggerMocks()
    logger.error.mockReset()
    logger.info.mockReset()
    logger.warn.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('initial state: empty rules, loading false, error null', () => {
    const { rules, loading, error } = useNotifications()

    expect(rules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() populates rules and toggles loading', async () => {
    const items = [rule(1), rule(2)]
    let resolve: (v: unknown) => void = () => {}
    getApiMocks().fetchNotificationRules.mockReturnValue(
      new Promise<unknown>((r) => {
        resolve = r
      }),
    )

    const { rules, loading, error, load } = useNotifications()

    const p = load()
    expect(loading.value).toBe(true)

    resolve({ items, total: 2, offset: 0 })
    await p

    expect(rules.value).toEqual(items)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() on rejection sets error, clears rules and logs error once', async () => {
    const api = getApiMocks()
    const logger = getLoggerMocks()
    api.fetchNotificationRules.mockRejectedValue(new Error('boom'))

    const { rules, loading, error, load } = useNotifications()
    await load()

    expect(error.value).toBe('boom')
    expect(rules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(logger.error).toHaveBeenCalledTimes(1)
    expect(logger.error).toHaveBeenCalledWith('failed to load notification rules', {
      error: 'boom',
    })
  })

  it('loadFireLog() populates the fire log page', async () => {
    const page = { items: [], total: 0, offset: 0 }
    getApiMocks().fetchNotificationFireLog.mockResolvedValue(page)

    const { fireLog, fireLogLoading, fireLogError, loadFireLog } = useNotifications()
    await loadFireLog(0)

    expect(getApiMocks().fetchNotificationFireLog).toHaveBeenCalledWith({
      offset: 0,
      limit: 25,
      ruleId: undefined,
    })
    expect(fireLog.value).toEqual(page)
    expect(fireLogLoading.value).toBe(false)
    expect(fireLogError.value).toBeNull()
  })

  it('loadFireLog() on rejection sets fireLogError', async () => {
    getApiMocks().fetchNotificationFireLog.mockRejectedValue(new Error('nope'))

    const { fireLog, fireLogError, loadFireLog } = useNotifications()
    await loadFireLog(25)

    expect(getApiMocks().fetchNotificationFireLog).toHaveBeenCalledWith({
      offset: 25,
      limit: 25,
      ruleId: undefined,
    })
    expect(fireLogError.value).toBe('nope')
    expect(fireLog.value).toBeNull()
  })

  it('create(payload) calls createNotificationRule then reloads', async () => {
    const api = getApiMocks()
    api.fetchNotificationRules.mockResolvedValue({ items: [] })

    const { create } = useNotifications()
    const payload = { name: 'r', eventFilter: { eventType: 'RunFailed' }, actions: [] }
    await create(payload)

    expect(api.createNotificationRule).toHaveBeenCalledWith(payload)
    expect(api.fetchNotificationRules).toHaveBeenCalled()
    const createOrder = api.createNotificationRule.mock.invocationCallOrder[0]
    const listOrder = api.fetchNotificationRules.mock.invocationCallOrder[0]
    expect(createOrder).toBeLessThan(listOrder)
  })

  it('update(id, payload) calls updateNotificationRule then reloads', async () => {
    const api = getApiMocks()
    api.fetchNotificationRules.mockResolvedValue({ items: [] })

    const { update } = useNotifications()
    const payload = { name: 'r2', eventFilter: { eventType: 'RunFailed' }, actions: [] }
    await update(7, payload)

    expect(api.updateNotificationRule).toHaveBeenCalledWith(7, payload)
    expect(api.fetchNotificationRules).toHaveBeenCalled()
    const updateOrder = api.updateNotificationRule.mock.invocationCallOrder[0]
    const listOrder = api.fetchNotificationRules.mock.invocationCallOrder[0]
    expect(updateOrder).toBeLessThan(listOrder)
  })

  it('toggleEnabled(id, enabled) calls setNotificationRuleEnabled then reloads', async () => {
    const api = getApiMocks()
    api.fetchNotificationRules.mockResolvedValue({ items: [] })

    const { toggleEnabled } = useNotifications()
    await toggleEnabled(7, false)

    expect(api.setNotificationRuleEnabled).toHaveBeenCalledWith(7, false)
    expect(api.fetchNotificationRules).toHaveBeenCalled()
    const toggleOrder = api.setNotificationRuleEnabled.mock.invocationCallOrder[0]
    const listOrder = api.fetchNotificationRules.mock.invocationCallOrder[0]
    expect(toggleOrder).toBeLessThan(listOrder)
  })

  it('remove(id) calls deleteNotificationRule then reloads', async () => {
    const api = getApiMocks()
    api.fetchNotificationRules.mockResolvedValue({ items: [] })

    const { remove } = useNotifications()
    await remove(7)

    expect(api.deleteNotificationRule).toHaveBeenCalledWith(7)
    expect(api.fetchNotificationRules).toHaveBeenCalled()
    const deleteOrder = api.deleteNotificationRule.mock.invocationCallOrder[0]
    const listOrder = api.fetchNotificationRules.mock.invocationCallOrder[0]
    expect(deleteOrder).toBeLessThan(listOrder)
  })

  it('test(payload) sets testing, stores the result and clears testing', async () => {
    const api = getApiMocks()
    const result = { matchedRuleIds: [1], fireResults: [] }
    let resolve: (v: unknown) => void = () => {}
    api.testNotificationRules.mockReturnValue(
      new Promise<unknown>((r) => {
        resolve = r
      }),
    )

    const { testing, testResult, testError, test } = useNotifications()
    const p = test({ eventType: 'RunFailed' })

    expect(testing.value).toBe(true)

    resolve(result)
    await p

    expect(api.testNotificationRules).toHaveBeenCalledWith({ eventType: 'RunFailed' })
    expect(testResult.value).toEqual(result)
    expect(testError.value).toBeNull()
    expect(testing.value).toBe(false)
  })

  it('test(payload) on rejection sets testError', async () => {
    const api = getApiMocks()
    api.testNotificationRules.mockRejectedValue(new Error('bad'))

    const { testing, testResult, testError, test } = useNotifications()
    await test({ eventType: 'RunFailed' })

    expect(testError.value).toBe('bad')
    expect(testResult.value).toBeNull()
    expect(testing.value).toBe(false)
  })
})
