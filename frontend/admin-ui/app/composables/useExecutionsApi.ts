export function useExecutionsApi() {
  async function list(filters?: Record<string, unknown>) {
    return $fetch('/api/v1/executions', { query: filters })
  }

  async function get(id: string) {
    return $fetch(`/api/v1/executions/${id}`)
  }

  return { list, get }
}
