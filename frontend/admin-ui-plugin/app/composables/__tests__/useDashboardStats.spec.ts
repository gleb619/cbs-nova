import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useDashboardStats } from '../useDashboardStats'

describe('useDashboardStats', () => {
  const fetchMock = vi.mocked($fetch)

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(undefined)
  })

  it('loads stats and recent runs on load()', async () => {
    fetchMock
      .mockResolvedValueOnce({ totalRuns: 7 })
      .mockResolvedValueOnce({ items: [{ id: 'r1' }] })

    const { stats, recentRuns, loading, load } = useDashboardStats()
    await load()

    expect(loading.value).toBe(false)
    expect(stats.value).toEqual({ totalRuns: 7 })
    expect(recentRuns.value).toEqual([{ id: 'r1' }])
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/stats')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', { query: { limit: 10 } })
  })

  it('falls back to a plain array response for recent runs', async () => {
    fetchMock.mockResolvedValueOnce({}).mockResolvedValueOnce([{ id: 'r2' }])

    const { recentRuns, load } = useDashboardStats()
    await load()

    expect(recentRuns.value).toEqual([{ id: 'r2' }])
  })

  it('sets error and clears loading on failure', async () => {
    fetchMock.mockRejectedValueOnce(new Error('boom'))

    const { stats, loading, error, load } = useDashboardStats()
    await load()

    expect(loading.value).toBe(false)
    expect(stats.value).toBeNull()
    expect(error.value).toBe('Failed to load dashboard data. Is the backend reachable?')
  })

  it('loads timeseries with default params', async () => {
    fetchMock.mockResolvedValueOnce({
      windowStart: '2026-08-13T10:00:00Z',
      windowEnd: '2026-08-13T11:00:00Z',
      bucketMinutes: 60,
      buckets: [{ bucketStart: '2026-08-13T10:00:00Z', statusCounts: { Completed: 3 } }],
    })

    const { timeseries, loadingTimeseries, timeseriesError, loadTimeseries } = useDashboardStats()
    await loadTimeseries()

    expect(loadingTimeseries.value).toBe(false)
    expect(timeseriesError.value).toBeNull()
    expect(timeseries.value?.buckets).toHaveLength(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/stats/timeseries', {
      query: { windowHours: 24, bucketMinutes: 60 },
    })
  })

  it('loads timeseries with custom params', async () => {
    fetchMock.mockResolvedValueOnce({ buckets: [] })

    const { loadTimeseries } = useDashboardStats()
    await loadTimeseries(6, 30)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/stats/timeseries', {
      query: { windowHours: 6, bucketMinutes: 30 },
    })
  })

  it('sets timeseriesError and clears loadingTimeseries on failure', async () => {
    fetchMock.mockRejectedValueOnce(new Error('chart boom'))

    const { timeseries, loadingTimeseries, timeseriesError, loadTimeseries } = useDashboardStats()
    await loadTimeseries()

    expect(loadingTimeseries.value).toBe(false)
    expect(timeseries.value).toBeNull()
    expect(timeseriesError.value).toBe('Failed to load trend chart data. Is the backend reachable?')
  })
})
