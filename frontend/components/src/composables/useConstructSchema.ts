import { computed, ref, watch } from 'vue'
import type { JsonSchema } from '../types/jsonSchema'
export type ConstructType = 'Process' | 'Transaction' | 'Helper' | 'Function'
const cache = new Map<string, JsonSchema>()
export interface UseConstructSchemaOptions {
  name: string
  type?: ConstructType
}
export function __resetConstructSchemaCache() {
  cache.clear()
}
export function useConstructSchema({ name, type }: UseConstructSchemaOptions) {
  const schema = ref<JsonSchema | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const hasKnownEndpoint = computed(() => type === 'Process' || type === 'Transaction')
  function cacheKey() {
    return `${type ?? 'unknown'}:${name}`
  }
  function endpoint() {
    switch (type) {
      case 'Process':
        return `/api/v1/dsl/processes/${encodeURIComponent(name)}`
      case 'Transaction':
        return `/api/v1/dsl/transactions/${encodeURIComponent(name)}`
      default:
        return null
    }
  }
  async function load() {
    if (!name) {
      schema.value = null
      error.value = null
      return
    }
    if (!hasKnownEndpoint.value) {
      schema.value = null
      error.value = null
      return
    }
    const key = cacheKey()
    const cached = cache.get(key)
    if (cached) {
      schema.value = cached
      error.value = null
      return
    }
    loading.value = true
    error.value = null
    schema.value = null
    try {
      const url = endpoint()
      if (!url) {
        schema.value = null
        return
      }
      const detail = (await $fetch(url)) as { inputSchema?: JsonSchema | null }
      const s = detail?.inputSchema ?? null
      if (s) {
        cache.set(key, s)
      }
      schema.value = s
    } catch (err) {
      error.value = (err as { statusMessage?: string; message?: string }).statusMessage ?? (err as Error).message ?? 'Failed to load schema'
      schema.value = null
    } finally {
      loading.value = false
    }
  }
  function invalidate() {
    cache.clear()
  }
  watch(
    [() => name, () => type],
    () => {
      load()
    },
    { immediate: true },
  )
  return {
    schema: computed(() => schema.value),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    hasSchema: computed(() => {
      const s = schema.value
      if (!s) return false
      if (s.type === 'object') {
        return !!(s.properties && Object.keys(s.properties).length > 0)
      }
      if (s.type === 'array') {
        return !!s.items
      }
      return true
    }),
    refresh: load,
    invalidate,
  }
}
