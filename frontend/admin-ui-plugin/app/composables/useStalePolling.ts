import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { onUnmounted, ref, watch } from 'vue'
import type { ExecutionDetail, ExecutionStatus } from '~/types'

function resolveIntervalMs(explicit?: number): number {
  if (typeof explicit === 'number' && explicit > 0) return explicit
  try {
    const config = (useRuntimeConfig as () => { public?: { stalePollMs?: number } } | undefined)()
    const fromConfig = config?.public?.stalePollMs
    if (typeof fromConfig === 'number' && fromConfig > 0) return fromConfig
  } catch {
    // not in a Nuxt context — fall through to default
  }
  return 5000
}

/**
 * useStalePolling
 *
 * Polls `GET /api/v1/executions/{id}` while the referenced run is in the
 * `Stale` state and stops as soon as it transitions to any other state.
 * A single final fetch confirms the new state before stopping (per T199
 * acceptance criteria).
 *
 * Pauses while the document is hidden (Page Visibility API) and resumes
 * when it becomes visible again. The interval is left in place while
 * hidden — it no-ops on tick — and an immediate tick fires when the tab
 * becomes visible so the operator sees fresh data on return.
 *
 * Cleanup is bound to `onUnmounted`, so route changes and host
 * unmounts automatically tear down the interval and the
 * visibilitychange listener.
 *
 * Usage:
 *   const status = computed(() => selectedExecution.value?.status)
 *   const id = computed(() => selectedExecution.value?.id ?? '')
 *   const { polling } = useStalePolling({ status, id })
 *
 *   // in template:
 *   <ExecutionsStatusBadge :status="..." :polling="polling.value" />
 */
export function useStalePolling(options: {
  status: Ref<ExecutionStatus | null | undefined>
  id: Ref<string> | string
  intervalMs?: number
}) {
  const { status, id } = options
  const intervalMs = resolveIntervalMs(options.intervalMs)

  const api = useExecutionsApi()
  const polling = ref(false)

  let interval: ReturnType<typeof setInterval> | null = null
  let visibilityHandler: (() => void) | null = null
  let stopStatusWatch: (() => void) | null = null

  function readId(): string {
    return typeof id === 'string' ? id : (id.value ?? '')
  }

  function clearTimer(): void {
    if (interval != null) {
      clearInterval(interval)
      interval = null
    }
  }

  function detachVisibilityListener(): void {
    if (visibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', visibilityHandler)
    }
    visibilityHandler = null
  }

  async function tick(): Promise<void> {
    if (typeof document !== 'undefined' && document.hidden) return
    const execId = readId()
    if (!execId) return

    let detail: ExecutionDetail
    try {
      detail = await api.get(execId)
    } catch (err) {
      // Network blip — leave the interval alive, surface via console.
      // The host's status ref is unchanged so the consumer keeps
      // seeing Stale until the next successful fetch resolves it.
      console.error('[useStalePolling] poll failed', err)
      return
    }

    const next = detail?.status
    if (next && next !== 'Stale') {
      // Confirmed transition out of Stale. Push the new status into the
      // consumer's ref so the UI re-renders, then stop.
      if (typeof status !== 'string') {
        ;(status as Ref<ExecutionStatus | null | undefined>).value = next
      }
      stop()
    }
  }

  function start(): void {
    if (interval != null) return
    if (typeof document === 'undefined') return

    polling.value = true

    interval = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return
      void tick()
    }, intervalMs)

    visibilityHandler = () => {
      if (!interval) return
      if (document.hidden) return
      // Resumed: fire one immediate tick so the user sees fresh data
      // the moment they come back to the tab.
      void tick()
    }
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  function stop(): void {
    polling.value = false
    clearTimer()
    detachVisibilityListener()
  }

  // Drive start/stop from the status ref. Only Stale keeps the interval
  // alive; every other value (including null/undefined) is a settled
  // state for the purposes of this composable.
  if (typeof status !== 'string') {
    stopStatusWatch = watch(
      status,
      (s) => {
        if (s === 'Stale') {
          start()
        } else {
          stop()
        }
      },
      { immediate: true },
    )
  } else if (status === 'Stale') {
    // Plain string status — caller manages start/stop imperatively.
    start()
  }

  onUnmounted(() => {
    stop()
    if (stopStatusWatch) stopStatusWatch()
    stopStatusWatch = null
  })

  return { polling, start, stop }
}
