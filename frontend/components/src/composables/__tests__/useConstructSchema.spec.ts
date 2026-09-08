import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import {
  __resetConstructSchemaCache,
  generateFakeValue,
  useConstructSchema,
} from '../useConstructSchema'

let fetchMock: ReturnType<typeof vi.fn>

function waitForNextTick() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('useConstructSchema', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
    fetchMock = vi.fn()
    vi.stubGlobal('$fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('fetches process schema on mount', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const { schema, loading } = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Demo')
    expect(loading.value).toBe(false)
    expect(schema.value).toBeTruthy()
  })

  it('fetches transaction schema when type is Transaction', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const { schema } = useConstructSchema({ name: 'Tx', type: 'Transaction' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Tx')
    expect(schema.value).toBeTruthy()
  })

  it('returns null schema for unsupported types', async () => {
    const { schema, loading } = useConstructSchema({ name: 'H' })
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
    const { schema } = useConstructSchema({ name: 'HelperA', type: 'Helper' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/HelperA')
    expect(schema.value).toBeTruthy()
  })

  it('fetches function schema from new schema endpoint', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'string' } })
    const { schema } = useConstructSchema({ name: 'FnA', type: 'Function' })
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
    const { inputSchema, outputSchema } = useConstructSchema({ name: 'Demo', type: 'Process' })
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
    const { inputType, outputType } = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(inputType.value).toBe('BatchIn')
    expect(outputType.value).toBe('BatchOut')
  })

  it('caches schema by name and type', async () => {
    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { name: { type: 'string' } } },
    })
    const first = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(first.schema.value).toBeTruthy()
    fetchMock.mockClear()

    const second = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    expect(second.schema.value).toBeTruthy()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('cache miss when name or type differs', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const _first = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    fetchMock.mockClear()

    fetchMock.mockResolvedValue({
      inputSchema: { type: 'object', properties: { x: { type: 'number' } } },
    })
    const _second = useConstructSchema({ name: 'Other', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/schemas/Other')
  })

  it('reacts to name change', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const currentName = ref('Alpha')
    const { schema } = useConstructSchema({ name: currentName, type: 'Process' })
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
    const { schema } = useConstructSchema({ name: 'Demo', type: currentType })
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
    const { schema } = useConstructSchema({ name: 'Demo', type: currentType })
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
    const { error, schema, loading } = useConstructSchema({ name: 'Bad', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(loading.value).toBe(false)
    expect(error.value).toContain('Network error')
    expect(schema.value).toBeNull()
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
