import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { $fetch } from 'ofetch'
import { ref } from 'vue'
import type { DashboardStats, DashboardTimeseries, Execution } from '~/types'

/**
 * Dashboard data loader.
 *
 * All requests go through the Nuxt BFF (`/api/v1/...`) — the browser never
 * talks to Spring Boot directly. The stats come from the server-side SQL
 * aggregates endpoint, the trend chart data comes from the timeseries endpoint,
 * and the recent-runs table reuses the executions list endpoint with a small limit.
 */
export function useDashboardStats(recentRunsLimit = 10) {
  const log = useClientLogger('runtime')
  const stats = ref<DashboardStats | null>(null)
  const timeseries = ref<DashboardTimeseries | null>(null)
  const recentRuns = ref<Execution[]>([])
  const loading = ref<boolean>(false)
  const loadingTimeseries = ref<boolean>(false)
  const error = ref<string | null>(null)
  const timeseriesError = ref<string | null>(null)

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [statsResult, runsResult] = await Promise.all([
        $fetch<DashboardStats>('/api/v1/executions/stats'),
        $fetch<{ items?: Execution[] } | Execution[]>('/api/v1/executions', {
          query: { limit: recentRunsLimit },
        }),
      ])
      stats.value = statsResult
      recentRuns.value = Array.isArray(runsResult) ? runsResult : (runsResult.items ?? [])
    } catch (err: unknown) {
      const message = (err as Error | undefined)?.message ?? String(err)
      log.error('failed to load dashboard data', { error: message })
      error.value = 'Failed to load dashboard data. Is the backend reachable?'
    } finally {
      loading.value = false
    }
  }

  async function loadTimeseries(
    windowHours = 24,
    bucketMinutes = 60,
  ): Promise<void> {
    loadingTimeseries.value = true
    timeseriesError.value = null
    try {
      timeseries.value = await $fetch<DashboardTimeseries>(
        '/api/v1/executions/stats/timeseries',
        {
          query: { windowHours, bucketMinutes },
        },
      )
    } catch (err: unknown) {
      const message = (err as Error | undefined)?.message ?? String(err)
      log.error('failed to load dashboard timeseries', { error: message })
      timeseriesError.value = 'Failed to load trend chart data. Is the backend reachable?'
    } finally {
      loadingTimeseries.value = false
    }
  }

  return {
    stats,
    timeseries,
    recentRuns,
    loading,
    loadingTimeseries,
    error,
    timeseriesError,
    load,
    loadTimeseries,
  }
}
