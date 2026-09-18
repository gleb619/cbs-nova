import { computed, type InjectionKey, inject, type MaybeRefOrGetter, ref, toValue } from 'vue'
import type { JsonSchema } from '../types/jsonSchema'
import { createEmitter } from '../utils/createEmitter'

export type ConstructType = 'Process' | 'Transaction' | 'Helper' | 'Function'
export type ConstructSchemaMode = 'preview' | 'explain'

interface SchemaCacheEntry {
  inputSchema: JsonSchema | null
  outputSchema: JsonSchema | null
  inputType: string | null
  outputType: string | null
  report: unknown | null
}

const cache = new Map<string, SchemaCacheEntry>()
const outputCache = new Map<string, JsonSchema>()
const inFlight = new Map<string, Promise<void>>()

export const DSL_SCHEMA_FETCH_KEY: InjectionKey<(url: string) => Promise<unknown>> = Symbol(
  'cbs-nova:dsl-schema-fetch',
)

export function __resetConstructSchemaCache() {
  cache.clear()
  outputCache.clear()
  inFlight.clear()
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

export interface ConstructSchemaChangeEvent {
  name: string
  type: ConstructType | undefined
}

export interface ConstructSchemaEvents {
  change: ConstructSchemaChangeEvent
}

export function useConstructSchema({
  name: nameRef,
  type: typeRef,
  mode: modeRef,
}: UseConstructSchemaOptions) {
  const name = ref(toValue(nameRef) ?? '')
  const type = ref(toValue(typeRef))
  const mode = computed(() => toValue(modeRef) ?? 'preview')

  const inputSchema = ref<JsonSchema | null>(null)
  const outputSchema = ref<JsonSchema | null>(null)
  const inputType = ref<string | null>(null)
  const outputType = ref<string | null>(null)
  const report = ref<unknown | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const schemaFetch = inject(DSL_SCHEMA_FETCH_KEY, null)

  const hasKnownEndpoint = computed(
    () =>
      type.value === 'Process' ||
      type.value === 'Transaction' ||
      type.value === 'Helper' ||
      type.value === 'Function',
  )

  function cacheKey() {
    return `${mode.value}:${type.value ?? 'unknown'}:${name.value}`
  }

  function endpoint() {
    const base = `/api/v1/dsl/schemas/${encodeURIComponent(name.value)}`
    return mode.value === 'preview' ? base : `${base}?mode=${mode.value}`
  }

  function emptyEntry(): SchemaCacheEntry {
    return {
      inputSchema: null,
      outputSchema: null,
      inputType: null,
      outputType: null,
      report: null,
    }
  }

  function applyEntry(entry: SchemaCacheEntry) {
    inputSchema.value = entry.inputSchema
    outputSchema.value = entry.outputSchema
    inputType.value = entry.inputType
    outputType.value = entry.outputType
    report.value = entry.report
    error.value = null
  }

  async function load() {
    if (!name.value) {
      applyEntry(emptyEntry())
      return
    }
    if (!hasKnownEndpoint.value) {
      applyEntry(emptyEntry())
      return
    }
    if (!schemaFetch) {
      applyEntry(emptyEntry())
      error.value = 'Schema fetcher is not provided. Register the DSL schema fetch plugin.'
      loading.value = false
      return
    }
    const key = cacheKey()
    const cached = cache.get(key)
    if (cached) {
      applyEntry(cached)
      return
    }
    const pending = inFlight.get(key)
    if (pending) {
      loading.value = true
      try {
        await pending
      } finally {
        loading.value = false
      }
      const after = cache.get(key)
      if (after) {
        applyEntry(after)
      }
      return
    }
    loading.value = true
    applyEntry(emptyEntry())
    const fetchPromise = (async () => {
      try {
        const response = (await schemaFetch(endpoint())) as {
          inputSchema?: JsonSchema | null
          outputSchema?: JsonSchema | null
          inputType?: string | null
          outputType?: string | null
        }
        const entry: SchemaCacheEntry = {
          inputSchema: response?.inputSchema ?? null,
          outputSchema: response?.outputSchema ?? null,
          inputType: response?.inputType ?? null,
          outputType: response?.outputType ?? null,
          report: mode.value === 'explain' ? response : null,
        }
        if (entry.inputSchema || entry.outputSchema || entry.report) {
          cache.set(key, entry)
        }
        applyEntry(entry)
      } catch (err) {
        applyEntry(emptyEntry())
        error.value =
          (err as { statusMessage?: string; message?: string }).statusMessage ??
          (err as Error).message ??
          'Failed to load schema'
      } finally {
        inFlight.delete(key)
      }
    })()
    inFlight.set(key, fetchPromise)
    try {
      await fetchPromise
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    cache.clear()
    outputCache.clear()
  }

  const events = createEmitter<ConstructSchemaEvents>()
  events.on('change', ({ name: nextName, type: nextType }) => {
    name.value = nextName
    type.value = nextType
    load()
  })

  load()

  const schema = computed(() => inputSchema.value)

  return {
    schema,
    inputSchema: schema,
    outputSchema: computed(() => outputSchema.value),
    inputType: computed(() => inputType.value),
    outputType: computed(() => outputType.value),
    report: computed(() => report.value),
    hasReport: computed(() => report.value != null),
    loading: computed(() => loading.value),
    error: computed(() => error.value),
    hasSchema: computed(() => hasUsefulSchema(inputSchema.value)),
    hasOutputSchema: computed(() => hasUsefulSchema(outputSchema.value)),
    refresh: load,
    invalidate,
    events,
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

export interface UseConstructSchemaOptions {
  name: MaybeRefOrGetter<string>
  type?: MaybeRefOrGetter<ConstructType | undefined>
  mode?: MaybeRefOrGetter<ConstructSchemaMode | undefined>
}
