import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import type {
  CreateNotificationRulePayload,
  NotificationFireLogPage,
  NotificationRule,
  NotificationTestPayload,
  NotificationTestResult,
} from '@cbs/components'
import { ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'

const FIRE_LOG_PAGE_SIZE = 25

export function useNotifications() {
  const log = useClientLogger('notifications')
  const api = useDslApi()

  const rules = ref<NotificationRule[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const fireLog = ref<NotificationFireLogPage | null>(null)
  const fireLogLoading = ref(false)
  const fireLogError = ref<string | null>(null)

  const testing = ref(false)
  const testError = ref<string | null>(null)
  const testResult = ref<NotificationTestResult | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      const result = await api.fetchNotificationRules()
      rules.value = result.items
    } catch (err) {
      log.error('failed to load notification rules', { error: extractApiError(err).message })
      error.value = extractApiError(err).message
      rules.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadFireLog(offset = 0, ruleId?: string) {
    fireLogLoading.value = true
    fireLogError.value = null
    try {
      fireLog.value = await api.fetchNotificationFireLog({
        offset,
        limit: FIRE_LOG_PAGE_SIZE,
        ruleId,
      })
    } catch (err) {
      log.error('failed to load notification fire log', { error: extractApiError(err).message })
      fireLogError.value = extractApiError(err).message
      fireLog.value = null
    } finally {
      fireLogLoading.value = false
    }
  }

  async function create(payload: CreateNotificationRulePayload) {
    await api.createNotificationRule(payload)
    await load()
  }

  async function update(id: number, payload: CreateNotificationRulePayload) {
    await api.updateNotificationRule(id, payload)
    await load()
  }

  async function toggleEnabled(id: number, enabled: boolean) {
    await api.setNotificationRuleEnabled(id, enabled)
    await load()
  }

  async function remove(id: number) {
    await api.deleteNotificationRule(id)
    await load()
  }

  async function test(payload: NotificationTestPayload) {
    testing.value = true
    testError.value = null
    try {
      testResult.value = await api.testNotificationRules(payload)
    } catch (err) {
      log.error('failed to test notification rules', { error: extractApiError(err).message })
      testError.value = extractApiError(err).message
      testResult.value = null
    } finally {
      testing.value = false
    }
  }

  return {
    rules,
    loading,
    error,
    fireLog,
    fireLogLoading,
    fireLogError,
    testing,
    testError,
    testResult,
    load,
    loadFireLog,
    create,
    update,
    toggleEnabled,
    remove,
    test,
  }
}
