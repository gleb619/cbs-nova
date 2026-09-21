import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import type { Execution, ExecutionDetail, TransactionExecutionDto } from '~/types'
import {
  cancelExecution as bffCancelExecution,
  getExecution as bffGetExecution,
  getExecutionTransactions as bffGetExecutionTransactions,
  listExecutions as bffListExecutions,
} from './generated/useBffApi'

export function useExecutionsApi() {
  const log = useClientLogger('runtime')

  async function list(
    params?: Record<string, unknown>,
  ): Promise<Execution[] | { items?: Execution[]; total?: number }> {
    log.debug('fetching executions', { params })
    if (params && Object.keys(params).length > 0) {
      return (await bffListExecutions({ query: params })) as
        | Execution[]
        | { items?: Execution[]; total?: number }
    }
    return (await bffListExecutions()) as Execution[] | { items?: Execution[]; total?: number }
  }

  async function get(id: string): Promise<ExecutionDetail> {
    log.debug('fetching execution detail', { id })
    return (await bffGetExecution(id)) as ExecutionDetail
  }

  async function cancel(id: string): Promise<ExecutionDetail> {
    log.info('cancelling execution', { id })
    return (await bffCancelExecution(id)) as ExecutionDetail
  }

  async function getTransactions(id: string): Promise<TransactionExecutionDto[]> {
    log.debug('fetching execution transactions', { id })
    return (await bffGetExecutionTransactions(id)) as TransactionExecutionDto[]
  }

  return { list, get, cancel, getTransactions }
}
