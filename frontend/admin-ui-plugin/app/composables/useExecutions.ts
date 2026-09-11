import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { useStalePolling } from '@cbs/admin-ui-plugin/composables/useStalePolling'
import { useExecutionEvents } from '@cbs/admin-ui-plugin/composables/useExecutionEvents'
import { resolveStalePollMs } from '@cbs/admin-ui-plugin/composables/useStalePollInterval'
import { useIntervalEmitter } from '@cbs/admin-ui-plugin/composables/useIntervalEmitter'
import { unwrapListWithTotal } from '@cbs/components'
import { computed, onUnmounted, ref } from 'vue'
import type { Execution, ExecutionDetail, ExecutionFilters, ExecutionStatus } from '~/types'
import { extractApiError } from '../utils/extractApiError'

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

  const cancellingIds = ref<Set<string>>(new Set())

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

  const sseEnabled = ref<boolean>(true)
  const sseIds = ref<Set<string>>(new Set())

  const api = useExecutionsApi()
  const stalePollMs = resolveStalePollMs()

  const events = useExecutionEvents({ ids: sseIds })
  events.onExecutionEvent(({ id, status }) => {
    void handleExecutionEvent(id, status)
  })
  events.onOpen(() => {
    sseEnabled.value = true
    syncListPolling()
  })
  events.onError(() => {
    sseEnabled.value = false
    syncListPolling()
  })

  // -------------------------------------------------------------------
  // List polling (T269) — event-driven ticker. It keeps refreshing the
  // executions list while any visible (filtered) row is in-flight. Uses
  // the same interval as the stale poller, pauses on hidden tabs, and
  // reuses the existing `loading` guard so a background tick never
  // clobbers a user-initiated load.
  // -------------------------------------------------------------------

  /**
   * Statuses that mean "still in flight — keep refreshing the list".
   * `Stale` is deliberately excluded: rows in that state already have a
   * dedicated stale poller driving their transition out of Stale.
   */
  const IN_FLIGHT_STATUSES: ReadonlyArray<ExecutionStatus> = ['Pending', 'Running']

  const inFlightRowCount = computed(
    () => executions.value.filter((e) => IN_FLIGHT_STATUSES.includes(e.status)).length,
  )

  const listTicker = useIntervalEmitter({ intervalMs: stalePollMs, pauseOnHidden: true })
  const stopListTick = listTicker.onTick(() => {
    void tickListPoll()
  })

  async function tickListPoll(): Promise<void> {
    // The ticker already gates ticks while hidden, but guard again for
    // callers that might invoke this directly.
    if (typeof document !== 'undefined' && document.hidden) return
    // Reuse the existing `loading` guard so a background tick never
    // races a user-initiated load (page change, filter apply, retry).
    if (loading.value) return
    await loadExecutions({ silent: true })
  }

  function startListPolling(intervalMs: number = stalePollMs): void {
    listTicker.setIntervalMs(intervalMs)
    listTicker.start()
  }

  function stopListPolling(): void {
    listTicker.stop()
  }

  /**
   * Drive list polling from the current executions state. Called after
   * every list mutation so polling tracks in-flight rows without a
   * generic `watch` on the computed count.
   */
  function syncListPolling(): void {
    if (sseEnabled.value && sseIds.value.size > 0) {
      stopListPolling()
      return
    }
    if (inFlightRowCount.value > 0) startListPolling()
    else stopListPolling()
  }

  // -------------------------------------------------------------------
  // Stale polling helpers
  // -------------------------------------------------------------------

  function isStalePolling(id: string): boolean {
    return stalePollingIds.value.has(id)
  }

  /**
   * Start stale polling for `id`. The `useStalePolling` composable
   * drives the loop. We update the shared `executions` list (and
   * `selectedExecution` if it matches) by listening to its
   * `transition` event instead of watching a shared status ref.
   */
  function startStalePolling(id: string, intervalMs: number = stalePollMs) {
    if (stalePollers.has(id)) return
    if (!id) return

    const statusRef = ref<ExecutionStatus | null>('Stale')
    const poller = useStalePolling({ status: statusRef, id, intervalMs })

    // Mirror polling state into the shared set so consumers can read it.
    const next = new Set(stalePollingIds.value)
    next.add(id)
    stalePollingIds.value = next

    // When the per-id poller confirms a transition out of Stale, refresh
    // the affected row in the shared state and tear down the poller.
    const stopTransition = poller.onTransition(async ({ id: execId, status: s }) => {
      if (s && s !== 'Stale') {
        // Re-fetch the row to pick up any other field changes too
        // (startedAt, completedAt, …).
        try {
          const fresh = await api.get(execId)
          updateRowInList(fresh)
          if (selectedExecution.value && selectedExecution.value.id === execId) {
            selectedExecution.value = fresh
          }
        } catch (err) {
          log.error('stale polling refresh failed', { id: execId, error: extractApiError(err).message })
        }
        stopStalePolling(execId)
      }
    })

    // Wrap the composable's stop so we also detach our listener.
    const originalStop = poller.stop
    stalePollers.set(id, () => {
      originalStop()
      stopTransition()
      if (stalePollingIds.value.has(id)) {
        const next = new Set(stalePollingIds.value)
        next.delete(id)
        stalePollingIds.value = next
      }
    })
  }

  function stopStalePolling(id: string) {
    const stop = stalePollers.get(id)
    if (stop) {
      stop()
      stalePollers.delete(id)
    } else if (stalePollingIds.value.has(id)) {
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

  async function handleExecutionEvent(id: string, status: ExecutionStatus): Promise<void> {
    log.info('execution sse event', { id, status })
    if (!IN_FLIGHT_STATUSES.includes(status)) {
      const next = new Set(sseIds.value)
      next.delete(id)
      sseIds.value = next
    }
    try {
      const fresh = await api.get(id)
      updateRowInList(fresh)
      if (selectedExecution.value && selectedExecution.value.id === id) {
        selectedExecution.value = fresh
      }
      syncListPolling()
    } catch (err) {
      log.error('sse refresh failed', { id, error: extractApiError(err).message })
    }
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
  // Public loaders
  // -------------------------------------------------------------------

  async function loadExecutions(options: { silent?: boolean } = {}) {
    const silent = options.silent === true
    if (!silent) loading.value = true
    error.value = null
    try {
      const offset = (page.value - 1) * pageSize
      const result = await api.list({ ...filters.value, offset, limit: pageSize })
      const envelope = unwrapListWithTotal<Execution>(result)
      executions.value = envelope.items
      total.value = envelope.total ?? 0
      reconcileStalePolling()
      if (sseEnabled.value) {
        sseIds.value = new Set(
          executions.value
            .filter((e) => IN_FLIGHT_STATUSES.includes(e.status))
            .map((e) => e.id),
        )
      } else {
        sseIds.value = new Set()
      }
      syncListPolling()
      log.info('executions loaded', {
        count: executions.value.length,
        total: total.value,
        page: page.value,
      })
    } catch (err) {
      log.error('failed to load executions', { error: extractApiError(err).message })
      executions.value = []
      total.value = 0
      error.value = extractApiError(err, 'Failed to load').message
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
      if (
        selectedExecution.value &&
        IN_FLIGHT_STATUSES.includes(selectedExecution.value.status)
      ) {
        const next = new Set(sseIds.value)
        next.add(id)
        sseIds.value = next
      }
      syncListPolling()
      // If the detail came back Stale, also drive a stale poller for it
      // so the banner re-renders as soon as the backend transitions the
      // status out.
      if (selectedExecution.value && selectedExecution.value.status === 'Stale') {
        startStalePolling(id)
      }
    } catch (err) {
      log.error('failed to load execution detail', { id, error: extractApiError(err).message })
      selectedExecution.value = null
      error.value = extractApiError(err, 'Failed to load').message
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
  // Cancel — T281. Calls the BFF cancel route, then refreshes the
  // affected row (and `selectedExecution` when it matches) so the UI
  // transitions to Cancelled without depending on a separate polling
  // window. Surfaced errors flow through the shared `error` ref so
  // pages can render the existing ErrorBanner pattern.
  // -------------------------------------------------------------------

  function isCancelling(id: string): boolean {
    return cancellingIds.value.has(id)
  }

  /**
   * Cancel a running execution by id. Returns the fresh detail row
   * on success so callers can update local state without an extra
   * round-trip. Throws on failure — the error message is also
   * mirrored into the shared `error` ref so pages can surface it.
   */
  async function cancelExecution(id: string): Promise<ExecutionDetail> {
    if (!id) throw new Error('cancelExecution: id is required')
    const next = new Set(cancellingIds.value)
    next.add(id)
    cancellingIds.value = next
    try {
      const fresh = await api.cancel(id)
      updateRowInList(fresh)
      if (selectedExecution.value && selectedExecution.value.id === id) {
        selectedExecution.value = fresh
      }
      if (fresh.status && !IN_FLIGHT_STATUSES.includes(fresh.status)) {
        const next = new Set(sseIds.value)
        next.delete(id)
        sseIds.value = next
      }
      syncListPolling()
      log.info('execution cancelled', { id, status: fresh.status })
      return fresh
    } catch (err) {
      const message = extractApiError(err, 'Failed to cancel execution').message
      error.value = message
      log.error('failed to cancel execution', { id, error: message })
      throw err
    } finally {
      const after = new Set(cancellingIds.value)
      after.delete(id)
      cancellingIds.value = after
    }
  }

  // -------------------------------------------------------------------
  // Legacy Running polling — kept for explicit opt-in (e.g. from the
  // detail page when navigating to a known-Running execution). Uses the
  // shared interval emitter so it no longer owns a raw setInterval.
  // -------------------------------------------------------------------

  let detailPoller: ReturnType<typeof useIntervalEmitter> | null = null

  function startPolling(id: string) {
    stopPolling()
    detailPoller = useIntervalEmitter({ intervalMs: 3000, pauseOnHidden: true })
    detailPoller.onTick(async () => {
      await loadDetail(id)
      if (selectedExecution.value && selectedExecution.value.status !== 'Running') {
        stopPolling()
      }
    })
    detailPoller.start()
  }

  function stopPolling() {
    if (detailPoller) {
      detailPoller.stop()
      detailPoller = null
    }
  }

  onUnmounted(() => {
    stopPolling()
    stopAllStalePolling()
    stopListPolling()
    stopListTick()
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
    // cancel action surface (T281)
    cancellingIds,
    isCancelling,
    cancelExecution,
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
