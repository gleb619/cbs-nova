import type { Execution, ExecutionDetail, ExecutionFilters } from '~/types/execution'

export function useExecutions() {
  const executions = ref<Execution[]>([])
  const filters = ref<ExecutionFilters>({})
  const total = ref<number>(0)
  const page = ref<number>(1)
  const loading = ref<boolean>(false)
  const selectedExecution = ref<ExecutionDetail | null>(null)

  let pollHandle: ReturnType<typeof setInterval> | null = null

  const api = useExecutionsApi()

  async function loadExecutions() {
    loading.value = true
    try {
      const result: any = await api.list({ ...filters.value, page: page.value })
      if (Array.isArray(result)) {
        executions.value = result
        total.value = result.length
      } else {
        executions.value = result?.items ?? result?.data ?? []
        total.value = result?.total ?? executions.value.length
      }
    } catch (err) {
      console.error('[useExecutions] loadExecutions failed', err)
      executions.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(id: string) {
    loading.value = true
    try {
      const result: any = await api.get(id)
      selectedExecution.value = result as ExecutionDetail
    } catch (err) {
      console.error('[useExecutions] loadDetail failed', err)
      selectedExecution.value = null
    } finally {
      loading.value = false
    }
  }

  async function applyFilters(f: ExecutionFilters) {
    filters.value = { ...f }
    page.value = 1
    await loadExecutions()
  }

  async function setPage(n: number) {
    page.value = n
    await loadExecutions()
  }

  function startPolling(id: string) {
    stopPolling()
    pollHandle = setInterval(async () => {
      await loadDetail(id)
      if (selectedExecution.value && selectedExecution.value.status !== 'Running') {
        stopPolling()
      }
    }, 3000)
  }

  function stopPolling() {
    if (pollHandle) {
      clearInterval(pollHandle)
      pollHandle = null
    }
  }

  onUnmounted(() => {
    stopPolling()
  })

  return {
    executions,
    filters,
    total,
    page,
    loading,
    selectedExecution,
    loadExecutions,
    loadDetail,
    applyFilters,
    setPage,
    startPolling,
    stopPolling,
  }
}
