import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount } from '@vue/test-utils'
import {
  __resetConstructSchemaCache,
  DSL_SCHEMA_FETCH_KEY,
  generateFakeValue,
  useConstructSchema,
} from '../useConstructSchema'

let fetchMock: ReturnType<typeof vi.fn>

function waitForNextTick() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

function mountUseConstructSchema(
  options: Parameters<typeof useConstructSchema>[0],
  providedFetch: typeof fetchMock = fetchMock,
) {
  let result: ReturnType<typeof useConstructSchema> | undefined
  const Comp = defineComponent({
    setup() {
      result = useConstructSchema(options)
      return () => h('div')
    },
  })
  mount(Comp, {
    global: {
      provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: providedFetch },
    },
  })
  return result as ReturnType<typeof useConstructSchema>
}

describe('useConstructSchema', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
    fetchMock = vi.fn()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('fetches process schema on mount', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const { schema, loading } = mountUseConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Demo')
    expect(loading.value).toBe(false)
    expect(schema.value).toBeTruthy()
  })

  it('fetches transaction schema when type is Transaction', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const { schema } = mountUseConstructSchema({ name: 'Tx', type: 'Transaction' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Tx')
    expect(schema.value).toBeTruthy()
  })

  it('returns null schema for unsupported types', async () => {
    const { schema, loading } = mountUseConstructSchema({ name: 'H' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(schema.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it('fetches helper schema from new schema endpoint', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { greeting: { type: 'string' } } },
    })
    const { schema } = mountUseConstructSchema({ name: 'HelperA', type: 'Helper' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/HelperA')
    expect(schema.value).toBeTruthy()
  })

  it('fetches function schema from new schema endpoint', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'string' } })
    const { schema } = mountUseConstructSchema({ name: 'FnA', type: 'Function' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/FnA')
    expect(schema.value).toBeTruthy()
  })

  it('exposes output schema', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { name: { type: 'string' } } },
      outputSchema: { type: 'object', properties: { id: { type: 'number' } } },
    })
    const { inputSchema, outputSchema } = mountUseConstructSchema({
      name: 'Demo',
      type: 'Process',
    })
    await waitForNextTick()
    await waitForNextTick()
    expect(inputSchema.value).toBeTruthy()
    expect(outputSchema.value).toBeTruthy()
    expect(outputSchema.value?.properties).toHaveProperty('id')
  })

  it('exposes input and output type names', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { name: { type: 'string' } } },
      outputSchema: { type: 'object', properties: { id: { type: 'number' } } },
      inputType: 'BatchIn',
      outputType: 'BatchOut',
    })
    const { inputType, outputType } = mountUseConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(inputType.value).toBe('BatchIn')
    expect(outputType.value).toBe('BatchOut')
  })

  it('caches schema by name and type', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { name: { type: 'string' } } },
    })
    const first = mountUseConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(first.schema.value).toBeTruthy()
    fetchMock.mockClear()

    const second = mountUseConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    expect(second.schema.value).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('cache miss when name or type differs', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const _first = mountUseConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    fetchMock.mockClear()

    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { x: { type: 'number' } } },
    })
    const _second = mountUseConstructSchema({ name: 'Other', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Other')
  })

  it('reacts to name change', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const currentName = ref('Alpha')
    const { schema } = mountUseConstructSchema({ name: currentName, type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Alpha')

    fetchMock.mockClear()
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { beta: { type: 'string' } } },
    })
    currentName.value = 'Beta'
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Beta')
    expect(schema.value?.properties).toHaveProperty('beta')
  })

  it('reacts to type change', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const currentType = ref<'Process' | 'Helper'>('Process')
    const { schema } = mountUseConstructSchema({ name: 'Demo', type: currentType })
    await waitForNextTick()
    await waitForNextTick()
    expect(schema.value).toBeTruthy()

    fetchMock.mockClear()
    currentType.value = 'Helper'
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Demo')
    expect(schema.value).toBeTruthy()
  })

  it('stops fetching when type becomes unsupported', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const currentType = ref<'Process' | 'Unknown'>('Process')
    const { schema } = mountUseConstructSchema({ name: 'Demo', type: currentType })
    await waitForNextTick()
    await waitForNextTick()
    expect(schema.value).toBeTruthy()

    fetchMock.mockClear()
    currentType.value = 'Unknown'
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(schema.value).toBeNull()
  })

  it('exposes error when fetch fails', async () => {
    fetchMock.mockRejectedValue({ statusMessage: 'Network error' })
    const { error, schema, loading } = mountUseConstructSchema({ name: 'Bad', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(loading.value).toBe(false)
    expect(error.value).toContain('Network error')
    expect(schema.value).toBeNull()
  })

  it('exposes error and does not throw when schema fetcher is missing', async () => {
    const { error, schema, loading } = mountUseConstructSchema(
      { name: 'Demo', type: 'Process' },
      null as unknown as typeof fetchMock,
    )
    await waitForNextTick()
    await waitForNextTick()
    expect(loading.value).toBe(false)
    expect(error.value).toContain('Schema fetcher is not provided')
    expect(schema.value).toBeNull()
  })

  it('dedupes concurrent calls with the same name and type into one fetch', async () => {
    let resolveFetch: (value: unknown) => void = () => {}
    fetchMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFetch = resolve
        }),
    )
    const results: ReturnType<typeof useConstructSchema>[] = []
    const Parent = defineComponent({
      setup() {
        results.push(useConstructSchema({ name: 'BatchProcessing', type: 'Process' }))
        results.push(useConstructSchema({ name: 'BatchProcessing', type: 'Process' }))
        return () => h('div')
      },
    })
    mount(Parent, {
      global: { provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock } },
    })
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/BatchProcessing')

    resolveFetch({
      inputSchema: { type: 'object', properties: { x: { type: 'number' } } },
      outputSchema: { type: 'object', properties: { y: { type: 'number' } } },
    })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(results[0].schema.value).toBeTruthy()
    expect(results[1].schema.value).toBeTruthy()
    expect(results[1].outputSchema.value).toBeTruthy()
  })

  it('does not refetch after a concurrent call resolves (cache hit on next mount)', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { x: { type: 'number' } } },
    })
    const Parent = defineComponent({
      setup() {
        useConstructSchema({ name: 'Cached', type: 'Process' })
        useConstructSchema({ name: 'Cached', type: 'Process' })
        return () => h('div')
      },
    })
    mount(Parent, {
      global: { provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock } },
    })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    mountUseConstructSchema({ name: 'Cached', type: 'Process' })
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('generateFakeValue', () => {
  it('returns default when present', () => {
    expect(generateFakeValue({ type: 'string', default: 'provided' })).toBe('provided')
  })

  it('picks first enum value', () => {
    expect(generateFakeValue({ type: 'string', enum: ['one', 'two'] })).toBe('one')
  })

  it('generates per type', () => {
    expect(generateFakeValue({ type: 'string' }, 'name')).toBe('Test Name')
    expect(generateFakeValue({ type: 'number' })).toBe(42)
    expect(generateFakeValue({ type: 'boolean' })).toBe(true)
    expect(generateFakeValue({ type: 'null' })).toBeNull()
  })

  it('generates nested object', () => {
    const schema = {
      type: 'object',
      properties: {
        user: {
          type: 'object',
          properties: {
            email: { type: 'string' },
          },
        },
      },
    }
    expect(generateFakeValue(schema)).toEqual({
      user: { email: 'test@example.com' },
    })
  })

  it('generates array with one fake item', () => {
    const schema = { type: 'array', items: { type: 'number' } }
    expect(generateFakeValue(schema, 'items')).toEqual([42])
  })

  it('falls back to empty object for unknown type', () => {
    expect(generateFakeValue({})).toEqual({})
  })
})
