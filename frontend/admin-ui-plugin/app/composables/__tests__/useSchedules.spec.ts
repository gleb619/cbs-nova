import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../useDslApi', () => {
  const listSchedules = vi.fn()
  const createSchedule = vi.fn()
  const deleteSchedule = vi.fn()
  const pauseSchedule = vi.fn()
  const resumeSchedule = vi.fn()
  return {
    useDslApi: () => ({ listSchedules, createSchedule, deleteSchedule, pauseSchedule, resumeSchedule }),
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
import { useSchedules } from '../useSchedules'

type ApiMock = {
  listSchedules: ReturnType<typeof vi.fn>
  createSchedule: ReturnType<typeof vi.fn>
  deleteSchedule: ReturnType<typeof vi.fn>
  pauseSchedule: ReturnType<typeof vi.fn>
  resumeSchedule: ReturnType<typeof vi.fn>
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

describe('useSchedules', () => {
  beforeEach(() => {
    getApiMocks().listSchedules.mockReset()
    getApiMocks().createSchedule.mockReset()
    getApiMocks().deleteSchedule.mockReset()
    getApiMocks().pauseSchedule.mockReset()
    getApiMocks().resumeSchedule.mockReset()
    getLoggerMocks().error.mockReset()
    getLoggerMocks().info.mockReset()
    getLoggerMocks().warn.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('initial state: empty schedules, loading false, error null', () => {
    const { schedules, loading, error } = useSchedules()

    expect(schedules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() array response populates schedules and toggles loading', async () => {
    const arr = [{ definition: 'daily', cron: '0 0 * * *' }]
    let resolve: (v: unknown) => void = () => {}
    getApiMocks().listSchedules.mockReturnValue(
      new Promise<unknown>((r) => {
        resolve = r
      }),
    )

    const { schedules, loading, error, load } = useSchedules()

    const p = load()
    expect(loading.value).toBe(true)

    resolve(arr)
    await p

    expect(schedules.value).toEqual(arr)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() handles { items: [...] } envelope shape', async () => {
    const items = [{ definition: 'weekly', cron: '0 0 * * 1' }]
    getApiMocks().listSchedules.mockResolvedValue({ items })

    const { schedules, loading, error, load } = useSchedules()
    await load()

    expect(schedules.value).toEqual(items)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() with {} resolves schedules to []', async () => {
    getApiMocks().listSchedules.mockResolvedValue({})

    const { schedules, loading, error, load } = useSchedules()
    await load()

    expect(schedules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() with { items: undefined } resolves schedules to []', async () => {
    getApiMocks().listSchedules.mockResolvedValue({ items: undefined })

    const { schedules, loading, error, load } = useSchedules()
    await load()

    expect(schedules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() on rejection sets error, clears schedules and logs error once', async () => {
    const api = getApiMocks()
    const logger = getLoggerMocks()
    api.listSchedules.mockRejectedValue(new Error('boom'))

    const { schedules, loading, error, load } = useSchedules()
    await load()

    expect(error.value).toBe('boom')
    expect(schedules.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(logger.error).toHaveBeenCalledTimes(1)
    expect(logger.error).toHaveBeenCalledWith('failed to load schedules', { error: 'boom' })
  })

  it('create(payload) calls createSchedule then reloads', async () => {
    const api = getApiMocks()
    api.listSchedules.mockResolvedValue([])

    const { create } = useSchedules()
    const payload = { definition: 'monthly', cron: '0 0 1 * *' }
    await create(payload)

    expect(api.createSchedule).toHaveBeenCalledWith(payload)
    expect(api.listSchedules).toHaveBeenCalled()
    const createOrder = api.createSchedule.mock.invocationCallOrder[0]
    const listOrder = api.listSchedules.mock.invocationCallOrder[0]
    expect(createOrder).toBeLessThan(listOrder)
  })

  it('remove(definition) calls deleteSchedule then reloads', async () => {
    const api = getApiMocks()
    api.listSchedules.mockResolvedValue([])

    const { remove } = useSchedules()
    await remove('daily')

    expect(api.deleteSchedule).toHaveBeenCalledWith('daily')
    expect(api.listSchedules).toHaveBeenCalled()
    const deleteOrder = api.deleteSchedule.mock.invocationCallOrder[0]
    const listOrder = api.listSchedules.mock.invocationCallOrder[0]
    expect(deleteOrder).toBeLessThan(listOrder)
  })

  it('pause(definition) calls pauseSchedule, marks pausing, then reloads', async () => {
    const api = getApiMocks()
    let resolve: (value?: unknown) => void = () => {}
    api.pauseSchedule.mockReturnValue(new Promise((r) => { resolve = r }))
    api.listSchedules.mockResolvedValue([])

    const { pausing, pause } = useSchedules()
    const p = pause('daily')

    expect(pausing.value.daily).toBe(true)

    resolve()
    await p

    expect(api.pauseSchedule).toHaveBeenCalledWith('daily')
    expect(api.listSchedules).toHaveBeenCalled()
    expect(pausing.value.daily).toBeUndefined()
    const pauseOrder = api.pauseSchedule.mock.invocationCallOrder[0]
    const listOrder = api.listSchedules.mock.invocationCallOrder[0]
    expect(pauseOrder).toBeLessThan(listOrder)
  })

  it('resume(definition) calls resumeSchedule, marks pausing, then reloads', async () => {
    const api = getApiMocks()
    let resolve: (value?: unknown) => void = () => {}
    api.resumeSchedule.mockReturnValue(new Promise((r) => { resolve = r }))
    api.listSchedules.mockResolvedValue([])

    const { pausing, resume } = useSchedules()
    const p = resume('daily')

    expect(pausing.value.daily).toBe(true)

    resolve()
    await p

    expect(api.resumeSchedule).toHaveBeenCalledWith('daily')
    expect(api.listSchedules).toHaveBeenCalled()
    expect(pausing.value.daily).toBeUndefined()
    const resumeOrder = api.resumeSchedule.mock.invocationCallOrder[0]
    const listOrder = api.listSchedules.mock.invocationCallOrder[0]
    expect(resumeOrder).toBeLessThan(listOrder)
  })
})
