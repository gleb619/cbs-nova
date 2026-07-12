import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useExecutions } from '../useExecutions'

type ApiMock = {
  list: ReturnType<typeof vi.fn>
  get: ReturnType<typeof vi.fn>
}

const installApiMock = (overrides: Partial<ApiMock> = {}): ApiMock => {
  const api: ApiMock = {
    list: overrides.list ?? vi.fn(),
    get: overrides.get ?? vi.fn(),
  }
  vi.mocked(useExecutionsApi as never).mockReturnValue(api)
  return api
}

describe('useExecutions', () => {
  beforeEach(() => {
    vi.mocked(useExecutionsApi as never).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('loadExecutions', () => {
    it('handles plain array response', async () => {
      const arr = [
        {
          id: 'e1',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Pending' as const,
          startedAt: '2025-01-01',
        },
      ]
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce(arr) })

      const { loadExecutions, executions, total } = useExecutions()
      await loadExecutions()

      expect(api.list).toHaveBeenCalledWith({ page: 1 })
      expect(executions.value).toEqual(arr)
      expect(total.value).toBe(1)
    })

    it('handles {items,total} response shape', async () => {
      const items = [
        {
          id: 'x',
          entity: 'a',
          entityType: 'Function' as const,
          mode: 'RUN' as const,
          status: 'Completed' as const,
          startedAt: '2025-01-01',
        },
      ]
      installApiMock({
        list: vi.fn().mockResolvedValueOnce({ items, total: 42 }),
      })

      const { loadExecutions, executions, total } = useExecutions()
      await loadExecutions()

      expect(executions.value).toEqual(items)
      expect(total.value).toBe(42)
    })

    it('toggles loading on and off and passes current filters and page', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { loadExecutions, filters, page, loading } = useExecutions()
      filters.value = { status: 'Completed' }
      page.value = 3

      const p = loadExecutions()
      expect(loading.value).toBe(true)
      await p
      expect(loading.value).toBe(false)

      expect(api.list).toHaveBeenCalledWith({ status: 'Completed', page: 3 })
    })

    it('on error clears executions and resets total to 0', async () => {
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      installApiMock({ list: vi.fn().mockRejectedValueOnce(new Error('boom')) })

      const { loadExecutions, executions, total, loading } = useExecutions()
      await loadExecutions()

      expect(executions.value).toEqual([])
      expect(total.value).toBe(0)
      expect(loading.value).toBe(false)
      expect(errSpy).toHaveBeenCalled()
      errSpy.mockRestore()
    })
  })

  describe('loadDetail', () => {
    it('populates selectedExecution and toggles loading', async () => {
      const detail = {
        id: 'e1',
        entity: 'ent',
        entityType: 'Process' as const,
        mode: 'PREVIEW' as const,
        status: 'Running' as const,
        startedAt: '2025-01-01',
      }
      const api = installApiMock({ get: vi.fn().mockResolvedValueOnce(detail) })

      const { loadDetail, selectedExecution, loading } = useExecutions()
      const p = loadDetail('e1')
      expect(loading.value).toBe(true)
      await p
      expect(loading.value).toBe(false)

      expect(api.get).toHaveBeenCalledWith('e1')
      expect(selectedExecution.value).toEqual(detail)
    })

    it('on error sets selectedExecution to null and toggles loading off', async () => {
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      installApiMock({ get: vi.fn().mockRejectedValueOnce(new Error('fail')) })

      const { loadDetail, loading, selectedExecution } = useExecutions()
      await loadDetail('nope')

      expect(selectedExecution.value).toBeNull()
      expect(loading.value).toBe(false)
      expect(errSpy).toHaveBeenCalled()
      errSpy.mockRestore()
    })
  })

  describe('applyFilters', () => {
    it('resets page to 1, applies filters and reloads executions', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { applyFilters, filters, page, total } = useExecutions()
      total.value = 99
      page.value = 7

      await applyFilters({ status: 'Failed', entityName: 'foo' })

      expect(page.value).toBe(1)
      expect(filters.value).toEqual({ status: 'Failed', entityName: 'foo' })
      expect(api.list).toHaveBeenCalledWith({ status: 'Failed', entityName: 'foo', page: 1 })
    })
  })

  describe('setPage', () => {
    it('updates page and reloads', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { setPage, page } = useExecutions()
      page.value = 1
      await setPage(5)

      expect(page.value).toBe(5)
      expect(api.list).toHaveBeenCalledWith({ page: 5 })
    })
  })

  describe('polling', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    it('startPolling calls loadDetail on each tick', async () => {
      const detail = {
        id: 'e1',
        entity: 'ent',
        entityType: 'Process' as const,
        mode: 'PREVIEW' as const,
        status: 'Running' as const, // keep polling
        startedAt: '2025-01-01',
      }
      const api = installApiMock({
        get: vi.fn().mockResolvedValue(detail),
      })

      const { startPolling, stopPolling } = useExecutions()
      startPolling('e1')

      expect(vi.getTimerCount()).toBe(1)

      await vi.advanceTimersByTimeAsync(3000)
      await vi.advanceTimersByTimeAsync(3000)

      expect(api.get).toHaveBeenCalledTimes(2)
      expect(api.get).toHaveBeenCalledWith('e1')

      // because status === 'Running', polling continues
      expect(vi.getTimerCount()).toBe(1)

      stopPolling()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('stopPolling when execution status changes to not Running', async () => {
      // first call returns Running, second returns Completed — should stop after second tick
      const runningDetail = {
        id: 'e1',
        entity: 'ent',
        entityType: 'Process' as const,
        mode: 'PREVIEW' as const,
        status: 'Running' as const,
        startedAt: '2025-01-01',
      }
      const completedDetail = { ...runningDetail, status: 'Completed' as const }
      const api = installApiMock({
        get: vi.fn().mockResolvedValueOnce(runningDetail).mockResolvedValueOnce(completedDetail),
      })

      const { startPolling } = useExecutions()
      startPolling('e1')

      await vi.advanceTimersByTimeAsync(3000)
      expect(api.get).toHaveBeenCalledTimes(1)
      expect(vi.getTimerCount()).toBe(1) // still running

      await vi.advanceTimersByTimeAsync(3000)
      expect(api.get).toHaveBeenCalledTimes(2)
      // after Completed, polling should stop on its own
      expect(vi.getTimerCount()).toBe(0)
    })

    it('stopPolling clears an existing interval', () => {
      const api = installApiMock({
        get: vi.fn().mockResolvedValue({
          id: 'e1',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Running' as const,
          startedAt: '2025-01-01',
        }),
      })

      const { startPolling, stopPolling } = useExecutions()
      startPolling('e1')
      expect(vi.getTimerCount()).toBe(1)

      stopPolling()
      expect(vi.getTimerCount()).toBe(0)

      // calling again should be idempotent
      stopPolling()
      expect(vi.getTimerCount()).toBe(0)
      expect(api.get).not.toHaveBeenCalled()
    })
  })
})
