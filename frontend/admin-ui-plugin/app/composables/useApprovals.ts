import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import type { ChangeRequest, ChangeRequestStatus } from '@cbs/components'
import { ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'

/**
 * T568 — DSL publish change-request approvals.
 *
 * Thin state wrapper around the `/api/dsl/change-requests*` endpoints (see
 * useDslApi): keeps the item list reactive, re-loads after every mutation and
 * surfaces failures through `error` (extractApiError message).
 */
export function useApprovals() {
  const log = useClientLogger('approvals')
  const api = useDslApi()

  const items = ref<ChangeRequest[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load(definitionName?: string, status?: ChangeRequestStatus) {
    loading.value = true
    error.value = null
    try {
      items.value = await api.fetchChangeRequests({ definitionName, status })
    } catch (err) {
      log.error('failed to load change requests', { error: extractApiError(err).message })
      error.value = extractApiError(err).message
      items.value = []
    } finally {
      loading.value = false
    }
  }

  async function submit(name: string) {
    await api.submitChangeRequest(name)
    await load()
  }

  async function approve(id: number, comment?: string) {
    await api.approveChangeRequest(id, comment)
    await load()
  }

  async function reject(id: number, comment: string) {
    await api.rejectChangeRequest(id, comment)
    await load()
  }

  return {
    items,
    loading,
    error,
    load,
    submit,
    approve,
    reject,
  }
}
