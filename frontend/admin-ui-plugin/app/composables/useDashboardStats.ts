import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { $fetch } from 'ofetch'
import { ref } from 'vue'
import type { DashboardStats, Execution } from '~/types'

/**
 * Dashboard data loader.
 *
 * Both requests go through the Nuxt BFF (`/api/v1/...`) — the browser never
 * talks to Spring Boot directly. The stats come from the server-side SQL
 * aggregates (never client-side counting over a clamped list page), and the
 * recent-runs table reuses the executions list endpoint with a small limit.
 */
export function useDashboardStats(recentRunsLimit = 10) {
  const log = useClientLogger('runtime')
  const stats = ref<DashboardStats | null>(null)
  const recentRuns = ref<Execution[]>([])
  const loading = ref<boolean>(false)
  const error = ref<string | null>(null)

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

  return { stats, recentRuns, loading, error, load }
}
