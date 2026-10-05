import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const fetchAuditTrail = vi.fn()

vi.mock('../useDslApi', () => ({
  useDslApi: () => ({ fetchAuditTrail }),
}))

const logger = {
  error: vi.fn(),
  info: vi.fn(),
  warn: vi.fn(),
  debug: vi.fn(),
}

vi.mock('../useClientLogger', () => ({
  useClientLogger: () => logger,
}))

import { useAuditTrail } from '../useAuditTrail'

function entry(id: number): {
  id: number
  occurredAt: string
  actor: string
  action: string
  target: string
  correlationId: string | null
  outcome: string
  detailsJson: string | null
} {
  return {
    id,
    occurredAt: '2026-01-01T00:00:00Z',
    actor: 'operator',
    action: 'DEFINITION_PUBLISH',
    target: 'my-def',
    correlationId: null,
    outcome: 'SUCCESS',
    detailsJson: null,
  }
}

describe('useAuditTrail', () => {
  beforeEach(() => {
    fetchAuditTrail.mockReset()
    logger.error.mockReset()
    logger.info.mockReset()
    logger.warn.mockReset()
    logger.debug.mockReset()
    localStorage.clear()
  })

  afterEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('initial state is empty, not loading, no error', () => {
    const { entries, loading, error, hasPrev, hasNext } = useAuditTrail()

    expect(entries.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(hasPrev.value).toBe(false)
    expect(hasNext.value).toBe(false)
  })

  it('load() fetches the first page with default limit 50', async () => {
    fetchAuditTrail.mockResolvedValue({
      items: [entry(1)],
      total: 1,
      offset: 0,
      limit: 50,
    })

    const { entries, loading, error, hasNext, load } = useAuditTrail()
    await load(0)

    expect(fetchAuditTrail).toHaveBeenCalledWith({ limit: 50, offset: 0 })
    expect(entries.value).toEqual([entry(1)])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(hasNext.value).toBe(false)
  })

  it('applyActionFilter() persists the action and reloads from offset 0', async () => {
    fetchAuditTrail.mockResolvedValue({ items: [], total: 0, offset: 0, limit: 50 })

    const { actionInput, actionFilter, applyActionFilter } = useAuditTrail()
    actionInput.value = 'DEFINITION_PUBLISH'

    await applyActionFilter()

    expect(actionFilter.value).toBe('DEFINITION_PUBLISH')
    expect(fetchAuditTrail).toHaveBeenCalledWith({
      action: 'DEFINITION_PUBLISH',
      limit: 50,
      offset: 0,
    })
  })

  it('clearActionFilter() resets the action and reloads', async () => {
    fetchAuditTrail.mockResolvedValue({ items: [], total: 0, offset: 0, limit: 50 })

    const { actionInput, actionFilter, applyActionFilter, clearActionFilter } = useAuditTrail()
    actionInput.value = 'DEFINITION_PUBLISH'
    await applyActionFilter()

    await clearActionFilter()

    expect(actionInput.value).toBe('')
    expect(actionFilter.value).toBe('')
    expect(fetchAuditTrail).toHaveBeenLastCalledWith({ limit: 50, offset: 0 })
  })

  it('load() on rejection sets error and logs once', async () => {
    fetchAuditTrail.mockRejectedValue(new Error('audit unavailable'))

    const { error, entries, load } = useAuditTrail()
    await load(0)

    expect(error.value).toBe('audit unavailable')
    expect(entries.value).toEqual([])
    expect(logger.error).toHaveBeenCalledTimes(1)
    expect(logger.error).toHaveBeenCalledWith('failed to load audit trail', {
      error: 'audit unavailable',
    })
  })

  it('goNext() advances by the current limit and goPrev() returns', async () => {
    fetchAuditTrail
      .mockResolvedValueOnce({ items: [entry(1)], total: 3, offset: 0, limit: 1 })
      .mockResolvedValueOnce({ items: [entry(2)], total: 3, offset: 1, limit: 1 })
      .mockResolvedValueOnce({ items: [entry(1)], total: 3, offset: 0, limit: 1 })

    const { entries, hasNext, hasPrev, load, goNext, goPrev } = useAuditTrail()
    await load(0)

    expect(hasNext.value).toBe(true)
    expect(hasPrev.value).toBe(false)

    await goNext()
    expect(fetchAuditTrail).toHaveBeenLastCalledWith({ limit: 1, offset: 1 })
    expect(entries.value).toEqual([entry(2)])
    expect(hasPrev.value).toBe(true)

    await goPrev()
    expect(fetchAuditTrail).toHaveBeenLastCalledWith({ limit: 1, offset: 0 })
  })

  it('clamps returned limit to [1, 500]', async () => {
    fetchAuditTrail.mockResolvedValue({
      items: [entry(1)],
      total: 1,
      offset: 0,
      limit: 1000,
    })

    const { limit, load } = useAuditTrail()
    await load(0)

    expect(limit.value).toBe(500)
    expect(fetchAuditTrail).toHaveBeenCalledWith({ limit: 50, offset: 0 })
  })

  it('refresh() reloads the current offset', async () => {
    fetchAuditTrail.mockResolvedValue({ items: [entry(1)], total: 2, offset: 50, limit: 50 })

    const { refresh, offset, load } = useAuditTrail()
    await load(0)
    expect(offset.value).toBe(50)

    fetchAuditTrail.mockClear()
    await refresh()

    expect(offset.value).toBe(50)
    expect(fetchAuditTrail).toHaveBeenCalledWith({ limit: 50, offset: 50 })
  })
})
