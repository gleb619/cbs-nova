import { useLocalStorageState } from '@cbs/components'
import type { AuditDto, AuditPage, AuditQuery } from '@cbs/components'
import { computed, ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'
import { useClientLogger } from './useClientLogger'
import { useDslApi } from './useDslApi'

const DEFAULT_LIMIT = 50
const MAX_LIMIT = 500

export function clampLimit(limit: number): number {
  return Math.max(1, Math.min(limit, MAX_LIMIT))
}

export function useAuditTrail() {
  const log = useClientLogger('audit')
  const api = useDslApi()

  const actionFilter = useLocalStorageState('audit.filter.action', '')
  const actionInput = ref(actionFilter.value)

  const page = ref<AuditPage | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const offset = ref(0)
  const limit = ref(DEFAULT_LIMIT)

  const entries = computed<AuditDto[]>(() => page.value?.items ?? [])
  const total = computed(() => page.value?.total ?? 0)
  const hasPrev = computed(() => offset.value > 0)
  const hasNext = computed(() => {
    if (!page.value) return false
    return offset.value + entries.value.length < total.value
  })

  async function load(targetOffset: number) {
    if (loading.value) return
    loading.value = true
    error.value = null
    try {
      const query: AuditQuery = {
        offset: Math.max(0, targetOffset),
        limit: clampLimit(limit.value),
      }
      if (actionFilter.value.trim()) query.action = actionFilter.value.trim()
      page.value = await api.fetchAuditTrail(query)
      offset.value = page.value.offset
      limit.value = clampLimit(page.value.limit ?? limit.value)
      log.info('audit trail loaded', { count: entries.value.length, total: total.value })
    } catch (err) {
      const message = extractApiError(err).message
      log.error('failed to load audit trail', { error: message })
      error.value = message
      page.value = null
    } finally {
      loading.value = false
    }
  }

  function applyActionFilter() {
    actionFilter.value = actionInput.value.trim()
    void load(0)
  }

  function clearActionFilter() {
    actionInput.value = ''
    applyActionFilter()
  }

  function goPrev() {
    if (!hasPrev.value) return
    void load(Math.max(0, offset.value - limit.value))
  }

  function goNext() {
    if (!hasNext.value) return
    void load(offset.value + limit.value)
  }

  function refresh() {
    void load(offset.value)
  }

  return {
    entries,
    loading,
    error,
    page,
    total,
    offset,
    limit,
    hasPrev,
    hasNext,
    actionFilter,
    actionInput,
    load,
    applyActionFilter,
    clearActionFilter,
    goPrev,
    goNext,
    refresh,
  }
}
