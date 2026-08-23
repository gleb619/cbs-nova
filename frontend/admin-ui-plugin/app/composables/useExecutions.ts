import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { useStalePolling } from '@cbs/admin-ui-plugin/composables/useStalePolling'
import { onUnmounted, ref, watch } from 'vue'
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
  // Public loaders
  // -------------------------------------------------------------------

  async function loadExecutions() {
    loading.value = true
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
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(id: string) {
    loading.value = true
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
  })

  return {
    executions,
    filters,
    total,
    page,
    pageSize,
    loading,
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
  }
}
