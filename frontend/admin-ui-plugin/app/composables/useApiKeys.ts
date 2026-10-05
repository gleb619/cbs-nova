import { $fetch } from 'ofetch'
import { ref } from 'vue'
import { extractApiError } from '../utils/extractApiError'
import type { ApiKey, CreatedApiKey } from '@cbs/components'

export function useApiKeys() {
  const keys = ref<ApiKey[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const revokingId = ref<number | null>(null)

  async function load() {
    loading.value = true
    error.value = null
    try {
      keys.value = (await $fetch('/api/v1/dsl/api-keys')) as ApiKey[]
    } catch (err) {
      error.value = extractApiError(err).message
      keys.value = []
    } finally {
      loading.value = false
    }
  }

  async function create(label: string): Promise<CreatedApiKey> {
    const created = (await $fetch('/api/v1/dsl/api-keys', {
      method: 'POST',
      body: { label: label.trim() },
    })) as CreatedApiKey
    await load()
    return created
  }

  async function revoke(id: number) {
    revokingId.value = id
    try {
      await $fetch(`/api/v1/dsl/api-keys/${id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      error.value = extractApiError(err).message
      throw err
    } finally {
      revokingId.value = null
    }
  }

  return {
    keys,
    loading,
    error,
    revokingId,
    load,
    create,
    revoke,
  }
}
