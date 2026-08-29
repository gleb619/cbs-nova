import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { $fetch } from 'ofetch'
import type { Execution, ExecutionDetail } from '~/types'

export function useExecutionsApi() {
  const log = useClientLogger('runtime')

  async function list(
    params?: Record<string, unknown>,
  ): Promise<Execution[] | { items?: Execution[]; total?: number }> {
    log.debug('fetching executions', { params })
    if (params && Object.keys(params).length > 0) {
      return $fetch('/api/v1/executions', { query: params }) as
        | Execution[]
        | { items?: Execution[]; total?: number }
    }
    return $fetch('/api/v1/executions') as Execution[] | { items?: Execution[]; total?: number }
  }

  async function get(id: string): Promise<ExecutionDetail> {
    log.debug('fetching execution detail', { id })
    return $fetch(`/api/v1/executions/${id}`) as ExecutionDetail
  }

  async function cancel(id: string): Promise<ExecutionDetail> {
    log.info('cancelling execution', { id })
    return $fetch(`/api/v1/executions/${id}/cancel`, { method: 'POST' }) as ExecutionDetail
  }

  return { list, get, cancel }
}
