import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { useStalePolling } from '@cbs/admin-ui-plugin/composables/useStalePolling'
import { computed, onUnmounted, ref, watch } from 'vue'
import type { Execution, ExecutionDetail, ExecutionFilters, ExecutionStatus } from '~/types'

export function useExecutions() {
  const log = useClientLogger('runtime')
  const executions = ref<Execution[]>([])
  const filters = ref<ExecutionFilters>({})
  const total = ref<number>(0)
  const page = ref<number>(1)
  const pageSize = 20
  const loading = ref<boolean>(false)
  const selectedExecution = ref<ExecutionDetail | null>(null)
  const error = ref<string | null>(null)

  /**
   * Set of execution ids that currently have an active stale poll.
   * Exposed to consumers (ExecutionList, StatusBadge) so the UI can
   * show a pulse while polling is in flight.
   */
  const stalePollingIds = ref<Set<string>>(new Set())

  /**
   * Internal map of id -> stop() function returned by useStalePolling.
   * Each entry is a live composable instance. We keep the handles so
   * `stopStalePolling(id)` can tear them down when the row is no
   * longer Stale, when the user navigates away, or on unmount.
   */
  const stalePollers: Map<string, () => void> = new Map()

  /**
   * Default stale poll interval. Read from `runtimeConfig.public.stalePollMs`
   * with a 5000ms fallback — mirrors `useStalePolling`'s own resolution so
   * tests can override via `useRuntimeConfig` instead of having to thread
   * a custom interval through every call site.
   */
  function resolveStalePollMs(): number {
    try {
      const cfg = (useRuntimeConfig as () => { public?: { stalePollMs?: number } } | undefined)()
      const v = cfg?.public?.stalePollMs
      if (typeof v === 'number' && v > 0) return v
    } catch {
      // not in a Nuxt context — fall through to default
    }
    return 5000
  }

  let pollHandle: ReturnType<typeof setInterval> | null = null

  const api = useExecutionsApi()
  const stalePollMs = resolveStalePollMs()

  // -------------------------------------------------------------------
  // Stale polling helpers
  // -------------------------------------------------------------------

  function isStalePolling(id: string): boolean {
    return stalePollingIds.value.has(id)
  }

  /**
   * Start stale polling for `id`. The `useStalePolling` composable
   * drives the loop, observing a per-id status ref. We update the
   * shared `executions` list (or `selectedExecution` if it matches) as
   * soon as the backend reports a transition out of Stale.
   */
  function startStalePolling(id: string, intervalMs: number = stalePollMs) {
    if (stalePollers.has(id)) return
    if (!id) return

    const statusRef = ref<ExecutionStatus | null>('Stale')
    const poller = useStalePolling({ status: statusRef, id, intervalMs })

    // Mirror polling state into the shared set so consumers can read it.
    const watcher = watch(
      poller.polling,
      (p) => {
        if (p) {
          const next = new Set(stalePollingIds.value)
          next.add(id)
          stalePollingIds.value = next
        } else {
          const next = new Set(stalePollingIds.value)
          next.delete(id)
          stalePollingIds.value = next
        }
      },
      { immediate: true },
    )

    // When the per-id status ref transitions out of Stale, refresh the
    // affected row in the shared state and tear down the poller.
    const stopStatusWatch = watch(statusRef, async (s) => {
      if (s && s !== 'Stale') {
        // Re-fetch the row to pick up any other field changes too
        // (startedAt, completedAt, …).
        try {
          const fresh = await api.get(id)
          updateRowInList(fresh)
          if (selectedExecution.value && selectedExecution.value.id === id) {
            selectedExecution.value = fresh
          }
        } catch (err) {
          log.error('stale polling refresh failed', { id, error: (err as Error).message })
        }
        stopStalePolling(id)
      }
    })

    // Wrap the composable's stop so we also detach our watchers.
    const originalStop = poller.stop
    stalePollers.set(id, () => {
      originalStop()
      stopStatusWatch()
      watcher()
    })
  }

  function stopStalePolling(id: string) {
    const stop = stalePollers.get(id)
    if (stop) {
      stop()
      stalePollers.delete(id)
    }
    if (stalePollingIds.value.has(id)) {
      const next = new Set(stalePollingIds.value)
      next.delete(id)
      stalePollingIds.value = next
    }
  }

  function stopAllStalePolling() {
    for (const stop of stalePollers.values()) stop()
    stalePollers.clear()
    stalePollingIds.value = new Set()
  }

  /**
   * Replace an existing row in `executions` by id, or no-op if the row
   * is gone. Used to fold fresh detail data into the list after a
   * stale polling transition.
   */
  function updateRowInList(detail: ExecutionDetail) {
    const idx = executions.value.findIndex((e) => e.id === detail.id)
    if (idx === -1) return
    const next = executions.value.slice()
    next[idx] = { ...next[idx], ...detail } as Execution
    executions.value = next
  }

  /**
   * Reconcile stale pollers against the current `executions` list. For
   * each row that is Stale, ensure a poller is running; for each poller
   * that is no longer pointed at a Stale row, tear it down.
   */
  function reconcileStalePolling() {
    const staleNow = new Set(executions.value.filter((e) => e.status === 'Stale').map((e) => e.id))
    // stop pollers for rows that are no longer Stale
    for (const id of Array.from(stalePollers.keys())) {
      if (!staleNow.has(id)) stopStalePolling(id)
    }
    // start pollers for new Stale rows
    for (const id of staleNow) {
      if (!stalePollers.has(id)) startStalePolling(id)
    }
  }

  // -------------------------------------------------------------------
  // List polling (T269) — refresh the executions list while any visible
  // (filtered) row is in-flight. Reuses the same `stalePollMs` runtime
  // config key as the per-row stale poller above; mirrors the visibility
  // pause/resume behaviour; and reuses the existing `loading` guard so a
  // background tick never clobbers a user-initiated load.
  // -------------------------------------------------------------------

  /**
   * Statuses that mean "still in flight — keep refreshing the list".
   * `Stale` is deliberately excluded: rows in that state already have a
   * dedicated stale poller driving their transition out of Stale.
   */
  const IN_FLIGHT_STATUSES: ReadonlyArray<ExecutionStatus> = ['Pending', 'Running']

  const inFlightRowCount = computed(() =>
    executions.value.filter((e) => IN_FLIGHT_STATUSES.includes(e.status)).length,
  )

  let listPollInterval: ReturnType<typeof setInterval> | null = null
  let listPollVisibilityHandler: (() => void) | null = null

  function clearListPollInterval(): void {
    if (listPollInterval != null) {
      clearInterval(listPollInterval)
      listPollInterval = null
    }
  }

  function detachListPollVisibilityListener(): void {
    if (listPollVisibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', listPollVisibilityHandler)
    }
    listPollVisibilityHandler = null
  }

  async function tickListPoll(): Promise<void> {
    if (typeof document !== 'undefined' && document.hidden) return
    // Reuse the existing `loading` guard so a background tick never
    // races a user-initiated load (page change, filter apply, retry).
    if (loading.value) return
    await loadExecutions({ silent: true })
  }

  function startListPolling(intervalMs: number = stalePollMs): void {
    if (listPollInterval != null) return
    if (typeof document === 'undefined') return

    listPollInterval = setInterval(() => {
      void tickListPoll()
    }, intervalMs)

    listPollVisibilityHandler = () => {
      if (!listPollInterval) return
      if (document.hidden) return
      // Resumed: fire one immediate tick so the user sees fresh data
      // the moment they come back to the tab.
      void tickListPoll()
    }
    document.addEventListener('visibilitychange', listPollVisibilityHandler)
  }

  function stopListPolling(): void {
    clearListPollInterval()
    detachListPollVisibilityListener()
  }

  // Drive start/stop from the in-flight row count: the interval only
  // runs while at least one visible row is in-flight; it tears itself
  // down as soon as the last in-flight row reaches a terminal state,
  // and restarts on the next load that brings an in-flight row back
  // into view (filter change, fresh fetch).
  watch(
    inFlightRowCount,
    (count) => {
      if (count > 0) startListPolling()
      else stopListPolling()
    },
    { immediate: true },
  )

  // -------------------------------------------------------------------
  // Public loaders
  // -------------------------------------------------------------------

  async function loadExecutions(options: { silent?: boolean } = {}) {
    const silent = options.silent === true
    if (!silent) loading.value = true
    error.value = null
    try {
      const offset = (page.value - 1) * pageSize
      const result = await api.list({ ...filters.value, offset, limit: pageSize })
      if (Array.isArray(result)) {
        executions.value = result
        total.value = result.length
      } else {
        executions.value = result.items ?? []
        total.value = result.total ?? executions.value.length
      }
      reconcileStalePolling()
      log.info('executions loaded', {
        count: executions.value.length,
        total: total.value,
        page: page.value,
      })
    } catch (err) {
      log.error('failed to load executions', { error: (err as Error).message })
      executions.value = []
      total.value = 0
      error.value = (err as Error).message || 'Failed to load'
    } finally {
      if (!silent) loading.value = false
    }
  }

  async function loadDetail(id: string) {
    loading.value = true
    error.value = null
    try {
      selectedExecution.value = await api.get(id)
      log.info('execution detail loaded', { id, status: selectedExecution.value?.status })
      // If the detail came back Stale, also drive a stale poller for it
      // so the banner re-renders as soon as the backend transitions the
      // status out.
      if (selectedExecution.value && selectedExecution.value.status === 'Stale') {
        startStalePolling(id)
      }
    } catch (err) {
      log.error('failed to load execution detail', { id, error: (err as Error).message })
      selectedExecution.value = null
      error.value = (err as Error).message || 'Failed to load'
    } finally {
      loading.value = false
    }
  }

  async function applyFilters(f: ExecutionFilters) {
    filters.value = { ...f }
    page.value = 1
    log.info('filters applied', { filters: filters.value })
    await loadExecutions()
  }

  async function setPage(n: number) {
    page.value = n
    log.info('page changed', { page: n })
    await loadExecutions()
  }

  // -------------------------------------------------------------------
  // Legacy Running polling — kept for explicit opt-in (e.g. from the
  // detail page when navigating to a known-Running execution).
  // -------------------------------------------------------------------

  function startPolling(id: string) {
    stopPolling()
    pollHandle = setInterval(async () => {
      await loadDetail(id)
      if (selectedExecution.value && selectedExecution.value.status !== 'Running') {
        stopPolling()
      }
    }, 3000)
  }

  function stopPolling() {
    if (pollHandle) {
      clearInterval(pollHandle)
      pollHandle = null
    }
  }

  onUnmounted(() => {
    stopPolling()
    stopAllStalePolling()
    stopListPolling()
  })

  return {
    executions,
    filters,
    total,
    page,
    pageSize,
    loading,
    error,
    selectedExecution,
    loadExecutions,
    loadDetail,
    applyFilters,
    setPage,
    startPolling,
    stopPolling,
    // stale polling public surface
    stalePollingIds,
    isStalePolling,
    startStalePolling,
    stopStalePolling,
    // list polling public surface (T269)
    inFlightRowCount,
    startListPolling,
    stopListPolling,
  }
}
