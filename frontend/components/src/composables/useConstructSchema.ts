import { computed, type MaybeRefOrGetter, ref, toValue, watch } from 'vue'
import type { JsonSchema } from '../types/jsonSchema'

export type ConstructType = 'Process' | 'Transaction' | 'Helper' | 'Function'

const cache = new Map<string, JsonSchema>()
const outputCache = new Map<string, JsonSchema>()

export interface UseConstructSchemaOptions {
  name: MaybeRefOrGetter<string>
  type: MaybeRefOrGetter<ConstructType | undefined>
}

export function __resetConstructSchemaCache() {
  cache.clear()
  outputCache.clear()
}

export function generateFakeValue(
  schema: JsonSchema | null | undefined,
  fieldName = 'value',
): unknown {
  if (!schema || typeof schema !== 'object') return undefined
  if (schema.default !== undefined) return schema.default
  if (schema.enum && schema.enum.length > 0) return schema.enum[0]

  switch (schema.type) {
    case 'string':
      return fakeString(fieldName)
    case 'number':
      return 42
    case 'boolean':
      return true
    case 'null':
      return null
    case 'array': {
      if (schema.items) {
        return [generateFakeValue(schema.items, `${fieldName}Item`)]
      }
      return []
    }
    case 'object': {
      const result: Record<string, unknown> = {}
      if (schema.properties) {
        for (const [key, propSchema] of Object.entries(schema.properties)) {
          result[key] = generateFakeValue(propSchema, key)
        }
      }
      return result
    }
    default:
      return {}
  }
}

function fakeString(name: string): string {
  const lower = name.toLowerCase()
  if (lower.includes('email')) return 'test@example.com'
  if (lower.includes('phone')) return '+1-555-123-4567'
  if (lower.includes('name')) return 'Test Name'
  if (lower.includes('url') || lower.includes('link')) return 'https://example.com'
  return `fake-${name}`
}

export function useConstructSchema({ name: nameRef, type: typeRef }: UseConstructSchemaOptions) {
  const name = computed(() => toValue(nameRef) ?? '')
  const type = computed(() => toValue(typeRef))

  const inputSchema = ref<JsonSchema | null>(null)
  const outputSchema = ref<JsonSchema | null>(null)
  const inputType = ref<string | null>(null)
  const outputType = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const hasKnownEndpoint = computed(
    () =>
      type.value === 'Process' ||
      type.value === 'Transaction' ||
      type.value === 'Helper' ||
      type.value === 'Function',
  )

  function cacheKey() {
    return `${type.value ?? 'unknown'}:${name.value}`
  }

  function endpoint() {
    return `/api/v1/dsl/schemas/${encodeURIComponent(name.value)}`
  }

  async function load() {
    if (!name.value) {
      inputSchema.value = null
      outputSchema.value = null
      inputType.value = null
      outputType.value = null
      error.value = null
      return
    }
    if (!hasKnownEndpoint.value) {
      inputSchema.value = null
      outputSchema.value = null
      inputType.value = null
      outputType.value = null
      error.value = null
      return
    }
    const key = cacheKey()
    const cached = cache.get(key)
    if (cached) {
      inputSchema.value = cached.inputSchema
      outputSchema.value = cached.outputSchema
      inputType.value = cached.inputType
      outputType.value = cached.outputType
      error.value = null
      return
    }
    loading.value = true
    error.value = null
    inputSchema.value = null
    outputSchema.value = null
    inputType.value = null
    outputType.value = null
    try {
      const response = (await $fetch(endpoint())) as {
        inputSchema?: JsonSchema | null
        outputSchema?: JsonSchema | null
        inputType?: string | null
        outputType?: string | null
      }
      const inSchema = response?.inputSchema ?? null
      const outSchema = response?.outputSchema ?? null
      const inType = response?.inputType ?? null
      const outType = response?.outputType ?? null
      if (inSchema || outSchema) {
        cache.set(key, {
          inputSchema: inSchema,
          outputSchema: outSchema,
          inputType: inType,
          outputType: outType,
        })
      }
      inputSchema.value = inSchema
      outputSchema.value = outSchema
      inputType.value = inType
      outputType.value = outType
    } catch (err) {
      error.value =
        (err as { statusMessage?: string; message?: string }).statusMessage ??
        (err as Error).message ??
        'Failed to load schema'
      inputSchema.value = null
      outputSchema.value = null
      inputType.value = null
      outputType.value = null
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    cache.clear()
    outputCache.clear()
  }

  watch(
    [name, type],
    () => {
      load()
    },
    { immediate: true },
  )

  const schema = computed(() => inputSchema.value)

  return {
    schema,
    inputSchema: schema,
    outputSchema: computed(() => outputSchema.value),
    inputType: computed(() => inputType.value),
    outputType: computed(() => outputType.value),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    hasSchema: computed(() => hasUsefulSchema(inputSchema.value)),
    hasOutputSchema: computed(() => hasUsefulSchema(outputSchema.value)),
    refresh: load,
    invalidate,
  }
}

function hasUsefulSchema(s: JsonSchema | null): boolean {
  if (!s) return false
  if (s.type === 'object') {
    return !!(s.properties && Object.keys(s.properties).length > 0)
  }
  if (s.type === 'array') {
    return !!s.items
  }
  return true
}
