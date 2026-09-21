import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import type { PromotionDefinition, PromotionEnvironment, PromoteResult } from '@cbs/components'
import { ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'

export function usePromotion() {
  const log = useClientLogger('promotion')
  const api = useDslApi()

  const environments = ref<PromotionEnvironment[]>([])
  const environmentsLoading = ref(false)
  const environmentsError = ref<string | null>(null)

  const definitions = ref<PromotionDefinition[]>([])
  const definitionsLoading = ref(false)
  const definitionsError = ref<string | null>(null)

  const preview = ref<PromoteResult | null>(null)
  const previewLoading = ref(false)
  const previewError = ref<string | null>(null)

  const applying = ref(false)
  const applyError = ref<string | null>(null)
  const applyResult = ref<PromoteResult | null>(null)

  async function loadEnvironments() {
    environmentsLoading.value = true
    environmentsError.value = null
    try {
      environments.value = await api.fetchPromotionEnvironments()
    } catch (err) {
      log.error('failed to load promotion environments', {
        error: extractApiError(err).message,
      })
      environmentsError.value = extractApiError(err).message
      environments.value = []
    } finally {
      environmentsLoading.value = false
    }
  }

  async function loadDefinitions(env: string) {
    definitionsLoading.value = true
    definitionsError.value = null
    try {
      definitions.value = await api.fetchPromotionDefinitions(env)
    } catch (err) {
      log.error('failed to load promotion definitions', {
        error: extractApiError(err).message,
      })
      definitionsError.value = extractApiError(err).message
      definitions.value = []
    } finally {
      definitionsLoading.value = false
    }
  }

  async function runPreview(payload: { source: string; target: string; definitions?: string[] }) {
    previewLoading.value = true
    previewError.value = null
    try {
      preview.value = await api.promoteDefinitions(payload, true)
      return preview.value
    } catch (err) {
      log.error('failed to preview promotion', { error: extractApiError(err).message })
      previewError.value = extractApiError(err).message
      preview.value = null
      return null
    } finally {
      previewLoading.value = false
    }
  }

  async function apply(payload: { source: string; target: string; definitions?: string[] }) {
    applying.value = true
    applyError.value = null
    try {
      applyResult.value = await api.promoteDefinitions(payload, false)
      return applyResult.value
    } catch (err) {
      log.error('failed to apply promotion', { error: extractApiError(err).message })
      applyError.value = extractApiError(err).message
      applyResult.value = null
      return null
    } finally {
      applying.value = false
    }
  }

  function reset() {
    preview.value = null
    previewError.value = null
    applyResult.value = null
    applyError.value = null
  }

  return {
    environments,
    environmentsLoading,
    environmentsError,
    definitions,
    definitionsLoading,
    definitionsError,
    preview,
    previewLoading,
    previewError,
    applying,
    applyError,
    applyResult,
    loadEnvironments,
    loadDefinitions,
    runPreview,
    apply,
    reset,
  }
}
