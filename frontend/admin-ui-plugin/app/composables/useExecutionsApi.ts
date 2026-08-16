import type { Execution, ExecutionDetail } from '~/types'

export function useExecutionsApi() {
  async function list(
    params?: Record<string, unknown>,
  ): Promise<Execution[] | { items?: Execution[]; total?: number }> {
    if (params && Object.keys(params).length > 0) {
      return $fetch('/api/v1/executions', { query: params }) as
        | Execution[]
        | { items?: Execution[]; total?: number }
    }
    return $fetch('/api/v1/executions') as Execution[] | { items?: Execution[]; total?: number }
  }

  async function get(id: string): Promise<ExecutionDetail> {
    return $fetch(`/api/v1/executions/${id}`) as ExecutionDetail
  }

  return { list, get }
}
