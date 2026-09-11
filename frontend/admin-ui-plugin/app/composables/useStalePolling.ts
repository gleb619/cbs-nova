import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useExecutionsApi } from '@cbs/admin-ui-plugin/composables/useExecutionsApi'
import { resolveStalePollMs } from '@cbs/admin-ui-plugin/composables/useStalePollInterval'
import { onUnmounted, type Ref, ref } from 'vue'
import type { ExecutionDetail, ExecutionStatus } from '~/types'
import { createEmitter } from '../utils/createEmitter'
import { extractApiError } from '../utils/extractApiError'
import { useIntervalEmitter } from './useIntervalEmitter'

interface StalePollingEvents {
  transition: { id: string; status: ExecutionStatus }
  [key: string]: unknown
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
 *   const { polling, status, onTransition } = useStalePolling({
 *     status: 'Stale',
 *     id: computed(() => selectedExecution.value?.id ?? ''),
 *   })
 *
 *   onTransition(({ status }) => { ... })
 */
export function useStalePolling(options: {
  status?: Ref<ExecutionStatus | null | undefined> | ExecutionStatus
  id: Ref<string> | string
  intervalMs?: number
}) {
  const { status: statusOption, id } = options
  const intervalMs = resolveStalePollMs(options.intervalMs)

  const api = useExecutionsApi()
  const log = useClientLogger('runtime')
  const emitter = createEmitter<StalePollingEvents>()
  const polling = ref(false)

  // The status is either owned by the caller (a ref) or managed internally.
  const status: Ref<ExecutionStatus | null | undefined> =
    typeof statusOption === 'object' && statusOption !== null && 'value' in statusOption
      ? statusOption
      : ref<ExecutionStatus | null | undefined>(statusOption ?? null)

  const ticker = useIntervalEmitter({ intervalMs, pauseOnHidden: true })

  function readId(): string {
    return typeof id === 'string' ? id : (id.value ?? '')
  }

  async function tick(): Promise<void> {
    const execId = readId()
    if (!execId) return

    let detail: ExecutionDetail
    try {
      detail = await api.get(execId)
    } catch (err) {
      // Network blip — leave the interval alive, surface via console.
      // The host's status ref is unchanged so the consumer keeps
      // seeing Stale until the next successful fetch resolves it.
      log.error('stale poll failed', { id: execId, error: extractApiError(err).message })
      return
    }

    const next = detail?.status
    if (next && next !== 'Stale' && status.value !== next) {
      // Confirmed transition out of Stale. Push the new status into the
      // consumer's ref so the UI re-renders, then stop.
      status.value = next
      stop()
      emitter.emit('transition', { id: execId, status: next })
    }
  }

  const stopTick = ticker.onTick(() => {
    void tick()
  })

  function start(): void {
    if (status.value === 'Stale') {
      polling.value = true
      ticker.start()
    }
  }

  function stop(): void {
    polling.value = false
    ticker.stop()
  }

  // Start immediately when the initial status is Stale; otherwise the caller
  // can drive the poller imperatively via `start()` / `stop()`.
  if (status.value === 'Stale') {
    start()
  }

  onUnmounted(() => {
    stop()
    stopTick()
  })

  return {
    polling,
    status,
    start,
    stop,
    onTransition: (handler: (payload: { id: string; status: ExecutionStatus }) => void) =>
      emitter.on('transition', handler),
  }
}
