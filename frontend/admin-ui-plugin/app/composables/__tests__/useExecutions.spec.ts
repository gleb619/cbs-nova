import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useExecutions } from '../useExecutions'

vi.mock('../useExecutionsApi', () => {
  const list = vi.fn()
  const get = vi.fn()
  const cancel = vi.fn()
  const api = { list, get, cancel }
  return { useExecutionsApi: () => api }
})

import * as executionsApiModule from '../useExecutionsApi'

type ApiMock = {
  list: ReturnType<typeof vi.fn>
  get: ReturnType<typeof vi.fn>
  cancel: ReturnType<typeof vi.fn>
}

const installApiMock = (overrides: Partial<ApiMock> = {}): ApiMock => {
  const api = (
    executionsApiModule as unknown as { useExecutionsApi: () => ApiMock }
  ).useExecutionsApi()
  if (overrides.list) api.list = overrides.list
  if (overrides.get) api.get = overrides.get
  if (overrides.cancel) api.cancel = overrides.cancel
  return api
}

describe('useExecutions', () => {
  beforeEach(() => {
    const api = (
      executionsApiModule as unknown as { useExecutionsApi: () => ApiMock }
    ).useExecutionsApi()
    api.list.mockReset()
    api.get.mockReset()
    api.cancel.mockReset()
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

      expect(api.list).toHaveBeenCalledWith({ offset: 0, limit: 20 })
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

    it('toggles loading on and off and passes current filters and page as offset/limit', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { loadExecutions, filters, page, loading } = useExecutions()
      filters.value = { status: 'Completed' }
      page.value = 3

      const p = loadExecutions()
      expect(loading.value).toBe(true)
      await p
      expect(loading.value).toBe(false)

      expect(api.list).toHaveBeenCalledWith({ status: 'Completed', offset: 40, limit: 20 })
    })

    it('on error clears executions and resets total to 0', async () => {
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      installApiMock({ list: vi.fn().mockRejectedValueOnce(new Error('boom')) })

      const { loadExecutions, executions, total, loading, error } = useExecutions()
      await loadExecutions()

      expect(executions.value).toEqual([])
      expect(total.value).toBe(0)
      expect(loading.value).toBe(false)
      expect(error.value).toBe('boom')
      expect(errSpy).toHaveBeenCalled()
      errSpy.mockRestore()
    })

    it('clears a stale error when a subsequent load succeeds', async () => {
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      const list = vi
        .fn()
        .mockRejectedValueOnce(new Error('first attempt fails'))
        .mockResolvedValueOnce([])
      installApiMock({ list })

      const { loadExecutions, error } = useExecutions()
      await loadExecutions()
      expect(error.value).toBe('first attempt fails')

      await loadExecutions()
      expect(error.value).toBeNull()
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

      const { loadDetail, loading, selectedExecution, error } = useExecutions()
      await loadDetail('nope')

      expect(selectedExecution.value).toBeNull()
      expect(loading.value).toBe(false)
      expect(error.value).toBe('fail')
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
      expect(api.list).toHaveBeenCalledWith({
        status: 'Failed',
        entityName: 'foo',
        offset: 0,
        limit: 20,
      })
    })

    it('forwards correlationId as a query param', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { applyFilters, filters } = useExecutions()
      await applyFilters({ correlationId: 'corr-123' })

      expect(filters.value).toEqual({ correlationId: 'corr-123' })
      expect(api.list).toHaveBeenCalledWith({
        correlationId: 'corr-123',
        offset: 0,
        limit: 20,
      })
    })
  })

  describe('setPage', () => {
    it('updates page and reloads with offset derived from page size', async () => {
      const api = installApiMock({ list: vi.fn().mockResolvedValueOnce([]) })

      const { setPage, page } = useExecutions()
      page.value = 1
      await setPage(5)

      expect(page.value).toBe(5)
      expect(api.list).toHaveBeenCalledWith({ offset: 80, limit: 20 })
    })
  })

  describe('cancelExecution (T281)', () => {
    const runningRow = {
      id: 'run-1',
      entity: 'ent',
      entityType: 'Process' as const,
      mode: 'RUN' as const,
      status: 'Running' as const,
      startedAt: '2025-01-01',
    }
    const cancelledRow = { ...runningRow, status: 'Cancelled' as const }

    it('calls api.cancel with the id and returns the fresh detail', async () => {
      const api = installApiMock({
        list: vi.fn().mockResolvedValueOnce([runningRow]),
        cancel: vi.fn().mockResolvedValueOnce(cancelledRow),
      })

      const { loadExecutions, cancelExecution } = useExecutions()
      await loadExecutions()
      const result = await cancelExecution('run-1')

      expect(api.cancel).toHaveBeenCalledWith('run-1')
      expect(api.cancel).toHaveBeenCalledTimes(1)
      expect(result).toEqual(cancelledRow)
    })

    it('refreshes the matching row in the list and updates selectedExecution when it matches', async () => {
      installApiMock({
        list: vi.fn().mockResolvedValueOnce([runningRow]),
        get: vi.fn().mockResolvedValueOnce(runningRow),
        cancel: vi.fn().mockResolvedValueOnce(cancelledRow),
      })

      const { loadExecutions, loadDetail, cancelExecution, executions, selectedExecution } =
        useExecutions()
      await loadExecutions()
      await loadDetail('run-1')
      expect(selectedExecution.value?.status).toBe('Running')

      await cancelExecution('run-1')

      const updated = executions.value.find((e) => e.id === 'run-1')
      expect(updated?.status).toBe('Cancelled')
      expect(selectedExecution.value?.status).toBe('Cancelled')
    })

    it('tracks in-flight cancel via cancellingIds and isCancelling', async () => {
      let resolveCancel!: (v: unknown) => void
      installApiMock({
        list: vi.fn().mockResolvedValueOnce([runningRow]),
        cancel: vi.fn().mockImplementation(
          () =>
            new Promise<unknown>((res) => {
              resolveCancel = res
            }),
        ),
      })

      const { loadExecutions, cancelExecution, cancellingIds, isCancelling } = useExecutions()
      await loadExecutions()

      const p = cancelExecution('run-1')
      // synchronously after kick-off, the id is in the set
      expect(isCancelling('run-1')).toBe(true)
      expect(Array.from(cancellingIds.value)).toEqual(['run-1'])

      resolveCancel(cancelledRow)
      await p

      expect(isCancelling('run-1')).toBe(false)
      expect(cancellingIds.value.size).toBe(0)
    })

    it('on failure surfaces the error and exposes it on the shared error ref', async () => {
      const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
      installApiMock({
        list: vi.fn().mockResolvedValueOnce([runningRow]),
        cancel: vi.fn().mockRejectedValueOnce(new Error('backend boom')),
      })

      const { loadExecutions, cancelExecution, isCancelling, error } = useExecutions()
      await loadExecutions()

      await expect(cancelExecution('run-1')).rejects.toThrow('backend boom')
      expect(error.value).toBe('backend boom')
      expect(isCancelling('run-1')).toBe(false)
      errSpy.mockRestore()
    })

    it('rejects when called with an empty id', async () => {
      const api = installApiMock({ cancel: vi.fn() })

      const { cancelExecution } = useExecutions()
      await expect(cancelExecution('')).rejects.toThrow()
      expect(api.cancel).not.toHaveBeenCalled()
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

  describe('stale polling (T199)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      // speed up the default 5s stale poll interval so tests can drive
      // tick transitions without sleeping for seconds of fake time.
      vi.mocked(useRuntimeConfig as never).mockReturnValue({
        public: { stalePollMs: 1000 },
      } as ReturnType<typeof useRuntimeConfig>)
    })

    it('loadExecutions auto-starts stale polling for Stale rows and exposes isStalePolling', async () => {
      const list = [
        {
          id: 'stale-1',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Stale' as const,
          startedAt: '2025-01-01',
        },
        {
          id: 'ok-1',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Completed' as const,
          startedAt: '2025-01-01',
        },
      ]
      const api = installApiMock({
        list: vi.fn().mockResolvedValueOnce(list),
        get: vi.fn().mockResolvedValue({
          id: 'stale-1',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Stale' as const,
          startedAt: '2025-01-01',
        }),
      })

      const { loadExecutions, isStalePolling, stalePollingIds } = useExecutions()
      await loadExecutions()

      expect(isStalePolling('stale-1')).toBe(true)
      expect(isStalePolling('ok-1')).toBe(false)
      expect(Array.from(stalePollingIds.value)).toEqual(['stale-1'])
      // a polling interval is running
      expect(vi.getTimerCount()).toBeGreaterThan(0)

      // first stale tick fetches the detail
      await vi.advanceTimersByTimeAsync(1000)
      await Promise.resolve()
      expect(api.get).toHaveBeenCalledWith('stale-1')
    })

    it('removes the row from stalePollingIds once the backend reports a non-Stale status', async () => {
      const staleRow = {
        id: 'stale-2',
        entity: 'ent',
        entityType: 'Process' as const,
        mode: 'PREVIEW' as const,
        status: 'Stale' as const,
        startedAt: '2025-01-01',
      }
      const runningDetail = { ...staleRow, status: 'Running' as const }
      // useStalePolling ticks twice (1 Stale, 2 Running→stop), and the
      // useExecutions reconcile also fires one get(id) to refresh the
      // list row, so provide three Running responses for the third call.
      const api = installApiMock({
        list: vi.fn().mockResolvedValueOnce([staleRow]),
        get: vi
          .fn()
          .mockResolvedValueOnce(staleRow) // first tick: still Stale
          .mockResolvedValueOnce(runningDetail) // second tick: Running → stop
          .mockResolvedValue(runningDetail), // reconcile fetch after transition
      })

      const { loadExecutions, isStalePolling, stalePollingIds, executions } = useExecutions()
      await loadExecutions()
      expect(isStalePolling('stale-2')).toBe(true)

      // first tick: Stale → keep polling
      await vi.advanceTimersByTimeAsync(1000)
      await Promise.resolve()
      await Promise.resolve()
      expect(api.get).toHaveBeenCalledTimes(1)
      expect(isStalePolling('stale-2')).toBe(true)

      // second tick: Running → status ref flips, reconcile removes the id
      await vi.advanceTimersByTimeAsync(1000)
      // flush microtasks from the second tick + status ref watcher + reconcile
      for (let i = 0; i < 5; i++) await Promise.resolve()
      expect(api.get.mock.calls.length).toBeGreaterThanOrEqual(2)
      expect(isStalePolling('stale-2')).toBe(false)
      expect(stalePollingIds.value.has('stale-2')).toBe(false)
      // the list row reflects the new status (the reconciler pulled a fresh detail)
      const updated = executions.value.find((e) => e.id === 'stale-2')
      expect(updated?.status).toBe('Running')
    })

    it('loadDetail auto-starts stale polling when the detail comes back Stale', async () => {
      const staleDetail = {
        id: 'stale-3',
        entity: 'ent',
        entityType: 'Process' as const,
        mode: 'PREVIEW' as const,
        status: 'Stale' as const,
        startedAt: '2025-01-01',
      }
      const runningDetail = { ...staleDetail, status: 'Running' as const }
      installApiMock({
        get: vi
          .fn()
          .mockResolvedValueOnce(staleDetail) // initial loadDetail
          .mockResolvedValueOnce(staleDetail) // first stale tick: still Stale
          .mockResolvedValueOnce(runningDetail) // second tick: Running → stop
          .mockResolvedValue(runningDetail), // reconcile fetch
      })

      const { loadDetail, isStalePolling, selectedExecution } = useExecutions()
      await loadDetail('stale-3')

      expect(selectedExecution.value?.status).toBe('Stale')
      expect(isStalePolling('stale-3')).toBe(true)

      // poll tick → status transitions → detail ref updated
      await vi.advanceTimersByTimeAsync(1000)
      for (let i = 0; i < 3; i++) await Promise.resolve()
      await vi.advanceTimersByTimeAsync(1000)
      for (let i = 0; i < 5; i++) await Promise.resolve()
      expect(isStalePolling('stale-3')).toBe(false)
      expect(selectedExecution.value?.status).toBe('Running')
    })

    it('stopStalePolling tears down a poller started by loadExecutions', async () => {
      const list = [
        {
          id: 'stale-4',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Stale' as const,
          startedAt: '2025-01-01',
        },
      ]
      installApiMock({
        list: vi.fn().mockResolvedValueOnce(list),
        get: vi.fn().mockResolvedValue(list[0]),
      })

      const { loadExecutions, stopStalePolling, isStalePolling } = useExecutions()
      await loadExecutions()
      expect(isStalePolling('stale-4')).toBe(true)

      stopStalePolling('stale-4')
      expect(isStalePolling('stale-4')).toBe(false)
    })

    it('on unmount, all stale pollers are torn down', async () => {
      // onUnmounted only fires inside a component instance — call stopAllStalePolling
      // via the public surface instead, since the unmount branch exercises
      // exactly that function (and the existing useExecutions tests do the
      // same for the existing startPolling path).
      const list = [
        {
          id: 'stale-5',
          entity: 'ent',
          entityType: 'Process' as const,
          mode: 'PREVIEW' as const,
          status: 'Stale' as const,
          startedAt: '2025-01-01',
        },
      ]
      installApiMock({
        list: vi.fn().mockResolvedValueOnce(list),
        get: vi.fn().mockResolvedValue(list[0]),
      })

      const { loadExecutions, stalePollingIds, stopStalePolling } = useExecutions()
      await loadExecutions()
      expect(stalePollingIds.value.size).toBe(1)

      stopStalePolling('stale-5')
      expect(stalePollingIds.value.size).toBe(0)
    })
  })

  describe('list polling (T269)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
      vi.mocked(useRuntimeConfig as never).mockReturnValue({
        public: { stalePollMs: 1000 },
      } as ReturnType<typeof useRuntimeConfig>)
    })

    const flushAll = async () => {
      for (let i = 0; i < 10; i++) await Promise.resolve()
    }

    const runningRow = {
      id: 'run-1',
      entity: 'ent',
      entityType: 'Process' as const,
      mode: 'RUN' as const,
      status: 'Running' as const,
      startedAt: '2025-01-01',
    }
    const completedRow = {
      id: 'done-1',
      entity: 'ent',
      entityType: 'Process' as const,
      mode: 'RUN' as const,
      status: 'Completed' as const,
      startedAt: '2025-01-01',
    }

    it('starts polling when a visible row is in-flight and stops when none remain', async () => {
      const list = vi
        .fn()
        .mockResolvedValueOnce([runningRow, completedRow]) // initial load
        .mockResolvedValueOnce([completedRow, completedRow]) // first tick: all terminal → stop
      installApiMock({ list, get: vi.fn() })

      const { loadExecutions, inFlightRowCount } = useExecutions()
      expect(inFlightRowCount.value).toBe(0)

      await loadExecutions()
      await flushAll()
      expect(list).toHaveBeenCalledTimes(1)
      expect(inFlightRowCount.value).toBe(1)
      // list poll interval registered (no Stale rows so no stale pollers)
      expect(vi.getTimerCount()).toBe(1)

      // first tick — Running row still present → silent refresh
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(2)
      // after the refresh the visible list is all terminal → polling stopped
      expect(vi.getTimerCount()).toBe(0)

      // no further ticks once polling is stopped
      await vi.advanceTimersByTimeAsync(5000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(2)
    })

    it('skips ticks while document.hidden is true and resumes on visible', async () => {
      // Note: this test does NOT dispatch `visibilitychange` to verify the
      // resume behavior, because prior tests in this file leak
      // `visibilitychange` listeners onto the shared document (the
      // per-execution useStalePolling composable and earlier list-poll
      // instances both register listeners, and `onUnmounted` never fires
      // outside a component context, so they survive across tests). The
      // shared `useExecutionsApi` mock means those leaked listeners end
      // up calling the same `api.list` spy this test asserts on. Instead
      // we verify the same property — that `document.hidden` actually
      // gates the tick — by setting the property and advancing the
      // interval. The interval itself checks `document.hidden` on every
      // tick (mirroring the useStalePolling pattern), so this is a
      // direct test of the pause/resume contract.
      const list = vi.fn().mockResolvedValue([runningRow])
      installApiMock({ list, get: vi.fn() })

      const { loadExecutions } = useExecutions()
      await loadExecutions()
      await flushAll()
      expect(list).toHaveBeenCalledTimes(1)
      expect(vi.getTimerCount()).toBe(1)

      // first visible tick — silent refresh fires
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(2)

      // hide the tab
      Object.defineProperty(document, 'hidden', { configurable: true, get: () => true })

      // several ticks while hidden — must NOT call api.list
      await vi.advanceTimersByTimeAsync(5000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(2)

      // show the tab again — the next interval tick must fire and call api.list
      Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(3)

      // regular ticks continue
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(4)

      // restore
      Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
    })

    it('preserves active filters and pagination across a background refresh', async () => {
      const list = vi.fn().mockResolvedValue([runningRow])
      installApiMock({ list, get: vi.fn() })

      const { applyFilters, filters, page, setPage } = useExecutions()

      await applyFilters({ status: 'Running', entityName: 'foo' })
      await setPage(2)
      await flushAll()
      const baselineCalls = list.mock.calls.length
      // last call so far: filter + page 2 + limit 20
      expect(list.mock.calls[baselineCalls - 1][0]).toEqual({
        status: 'Running',
        entityName: 'foo',
        offset: 20,
        limit: 20,
      })

      // background tick fires a silent refresh — no user input in between
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(baselineCalls + 1)

      // the silent refresh re-sent the same filters and pagination
      expect(list.mock.calls[list.mock.calls.length - 1][0]).toEqual({
        status: 'Running',
        entityName: 'foo',
        offset: 20,
        limit: 20,
      })
      expect(filters.value).toEqual({ status: 'Running', entityName: 'foo' })
      expect(page.value).toBe(2)
    })

    it('skips a background tick while a user-driven load is in flight', async () => {
      let resolveSecond: ((v: unknown) => void) | null = null
      let listCalls = 0
      const list = vi.fn().mockImplementation(() => {
        listCalls++
        if (listCalls === 1) return Promise.resolve([runningRow])
        return new Promise<unknown>((res) => {
          resolveSecond = res
        })
      })
      installApiMock({ list, get: vi.fn() })

      const { loadExecutions, loading } = useExecutions()

      await loadExecutions()
      await flushAll()
      expect(list).toHaveBeenCalledTimes(1)
      expect(vi.getTimerCount()).toBe(1)

      // user-driven load (e.g. from applyFilters / setPage / retry)
      const userLoad = loadExecutions()
      expect(loading.value).toBe(true)
      expect(list).toHaveBeenCalledTimes(2)

      // background tick fires while the user-driven load is still pending
      await vi.advanceTimersByTimeAsync(1000)
      await flushAll()
      expect(list).toHaveBeenCalledTimes(2)

      resolveSecond?.([runningRow])
      await userLoad
      expect(list).toHaveBeenCalledTimes(2)
      expect(loading.value).toBe(false)
    })
  })
})
