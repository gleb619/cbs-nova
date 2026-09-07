import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useConstructSchema } from '../useConstructSchema'

let fetchMock: ReturnType<typeof vi.fn>

function waitForNextTick() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('useConstructSchema', () => {
  beforeEach(() => {
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
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/processes/Demo')
    expect(loading.value).toBe(false)
    expect(schema.value).toBeTruthy()
  })

  it('fetches transaction schema when type is Transaction', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: {} } })
    const { schema } = useConstructSchema({ name: 'Tx', type: 'Transaction' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/transactions/Tx')
    expect(schema.value).toBeTruthy()
  })

  it('returns null schema for unsupported types', async () => {
    const { schema, loading } = useConstructSchema({ name: 'H', type: 'Helper' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(schema.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it('caches schema by name and type', async () => {
    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: { name: { type: 'string' } } } })
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
    const first = useConstructSchema({ name: 'Demo', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    fetchMock.mockClear()

    fetchMock.mockResolvedValue({ inputSchema: { type: 'object', properties: { x: { type: 'number' } } } })
    const second = useConstructSchema({ name: 'Other', type: 'Process' })
    await waitForNextTick()
    await waitForNextTick()
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/processes/Other')
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
