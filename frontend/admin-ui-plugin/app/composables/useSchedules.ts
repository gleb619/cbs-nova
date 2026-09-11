import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import {
  type CreateSchedulePayload,
  notifySchedulesChanged,
  type ScheduleSummary,
  unwrapList,
} from '@cbs/components'
import { ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'

export function useSchedules() {
  const log = useClientLogger('schedules')
  const api = useDslApi()

  const schedules = ref<ScheduleSummary[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      const result = await api.listSchedules()
      schedules.value = unwrapList<ScheduleSummary>(result)
    } catch (err) {
      log.error('failed to load schedules', { error: extractApiError(err).message })
      error.value = extractApiError(err).message
      schedules.value = []
    } finally {
      loading.value = false
    }
    notifySchedulesChanged()
  }

  async function create(payload: CreateSchedulePayload) {
    await api.createSchedule(payload as Record<string, unknown>)
    await load()
  }

  async function remove(definition: string) {
    await api.deleteSchedule(definition)
    await load()
  }

  return {
    schedules,
    loading,
    error,
    load,
    create,
    remove,
  }
}
