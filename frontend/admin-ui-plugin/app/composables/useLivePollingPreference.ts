import { useLocalStorageState } from '@cbs/components/composables'
import type { Ref } from 'vue'

export interface UseLivePollingPreferenceParams {
  /** Storage key prefix. Final keys are `${storagePrefix}.enabled` and `${storagePrefix}.intervalMs`. */
  storagePrefix: string
  /** Default interval used when no value is persisted yet. */
  defaultIntervalMs: number
  /** Default value for the enabled flag when nothing is persisted yet. */
  defaultEnabled: boolean
  /**
   * Predicate evaluated before polling is (re-)started. Pass `() => true` for pages
   * that should poll unconditionally; pass a status-derived predicate (e.g. the
   * detail page's "is the execution Running?") to gate polling on backend state.
   */
  isActive: () => boolean
  /** Starts polling at the given interval (in milliseconds). */
  start: (intervalMs: number) => void
  /** Stops polling. Safe to call when polling is already stopped. */
  stop: () => void
}

export interface UseLivePollingPreferenceReturn {
  enabled: Ref<boolean>
  intervalMs: Ref<number>
  /** User toggled the checkbox. Persists the new value, then start/stop accordingly. */
  onToggle: (enabled: boolean) => void
  /** User changed the interval select. No-op when disabled or inactive; otherwise restarts polling. */
  onIntervalChange: () => void
}

/**
 * Shared preference + control surface for pages that offer a user-controllable
 * "live polling" toggle backed by `useLocalStorageState`.
 *
 * The composable does not own the polling lifecycle (mount/unmount); callers
 * are expected to invoke `start`/`stop` themselves when they take over the
 * page. On the toggle path, `enabled` flips first, then the polling state is
 * reconciled: start when enabled AND active, stop otherwise. On the interval
 * change path, polling is restarted only when both flags are true.
 */
export function useLivePollingPreference(
  params: UseLivePollingPreferenceParams,
): UseLivePollingPreferenceReturn {
  const { storagePrefix, defaultIntervalMs, defaultEnabled, isActive, start, stop } = params

  const enabled = useLocalStorageState<boolean>(`${storagePrefix}.enabled`, defaultEnabled)
  const intervalMs = useLocalStorageState<number>(`${storagePrefix}.intervalMs`, defaultIntervalMs)

  function onToggle(next: boolean) {
    enabled.value = next
    if (next && isActive()) {
      start(intervalMs.value)
    } else {
      stop()
    }
  }

  function onIntervalChange() {
    if (!enabled.value) return
    if (!isActive()) return
    stop()
    start(intervalMs.value)
  }

  return { enabled, intervalMs, onToggle, onIntervalChange }
}