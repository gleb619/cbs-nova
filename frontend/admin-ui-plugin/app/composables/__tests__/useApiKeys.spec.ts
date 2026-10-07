import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useApiKeys } from '../useApiKeys'

vi.mock('../useClientLogger', () => ({
  useClientLogger: () => ({ error: vi.fn(), info: vi.fn() }),
}))

describe('useApiKeys', () => {
  const fetchMock = vi.mocked($fetch as unknown as import('vitest').Mock)

  beforeEach(() => {
    fetchMock.mockReset()
  })

  it('loads keys from /api/v1/dsl/api-keys', async () => {
    fetchMock.mockResolvedValueOnce([
      { id: 1, label: 'ci', prefix: 'ak_ci', createdAt: '2026-09-01T00:00:00Z' },
    ])

    const { keys, load } = useApiKeys()
    await load()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/api-keys')
    expect(keys.value).toHaveLength(1)
    expect(keys.value[0].label).toBe('ci')
  })

  it('POSTs a trimmed label to create a key and reloads the list', async () => {
    fetchMock.mockResolvedValueOnce({ id: 2, label: 'deploy', prefix: 'ak_dp', key: 'secret' })
    fetchMock.mockResolvedValueOnce([])

    const { create, keys } = useApiKeys()
    const result = await create('  deploy  ')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/dsl/api-keys', {
      method: 'POST',
      body: { label: 'deploy' },
    })
    expect(result.key).toBe('secret')
    expect(keys.value).toEqual([])
  })

  it('DELETEs by id to revoke and reloads the list', async () => {
    fetchMock.mockResolvedValueOnce(undefined)
    fetchMock.mockResolvedValueOnce([
      {
        id: 1,
        label: 'ci',
        prefix: 'ak_ci',
        createdAt: '2026-09-01T00:00:00Z',
        revokedAt: '2026-09-02T00:00:00Z',
      },
    ])

    const { revoke, keys } = useApiKeys()
    await revoke(1)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/dsl/api-keys/1', { method: 'DELETE' })
    expect(keys.value[0].revokedAt).toBeDefined()
  })

  it('surfaces load errors without throwing', async () => {
    fetchMock.mockRejectedValueOnce({ message: 'backend down' })

    const { load, error } = useApiKeys()
    await load()

    expect(error.value).toBe('backend down')
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/dsl/api-keys')
  })
})
