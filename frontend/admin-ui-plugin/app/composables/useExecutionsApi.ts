import type { Execution, ExecutionDetail } from '~/types'

export function useExecutionsApi() {
  async function list(
    filters?: Record<string, unknown>,
  ): Promise<Execution[] | { items?: Execution[]; total?: number }> {
    if (filters) {
      return $fetch('/api/v1/executions', { query: filters }) as
        | Execution[]
        | { items?: Execution[]; total?: number }
    }
    return $fetch('/api/v1/executions') as
      | Execution[]
      | { items?: Execution[]; total?: number }
  }

  async function get(id: string): Promise<ExecutionDetail> {
    return $fetch(`/api/v1/executions/${id}`) as ExecutionDetail
  }

  return { list, get }
}
