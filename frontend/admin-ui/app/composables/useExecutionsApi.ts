export function useExecutionsApi() {
  async function list() {
    return $fetch('/api/v1/executions')
  }

  async function get(id: string) {
    return $fetch(`/api/v1/executions/${id}`)
  }

  return { list, get }
}
