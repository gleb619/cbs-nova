import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Execution, ExecutionDetail } from '@cbs/components/types'
import { useExecutionsApi } from '../useExecutionsApi'

describe('useExecutionsApi', () => {
  const fetchMock = vi.mocked($fetch)

  beforeEach(() => {
    fetchMock.mockReset()
    fetchMock.mockResolvedValue(undefined)
  })

  it('list() calls GET /api/v1/executions with no query', async () => {
    const response: Execution[] = []
    fetchMock.mockResolvedValueOnce(response)

    const api = useExecutionsApi()
    const result: Execution[] | { items?: Execution[]; total?: number } = await api.list()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })

  it('list(filters) forwards filters as query verbatim', async () => {
    const execution: Execution = {
      id: 'e1',
      entity: 'ent',
      entityType: 'Function',
      mode: 'RUN',
      status: 'Failed',
      startedAt: '2025-01-01',
    }
    const response = { items: [execution], total: 1 }
    fetchMock.mockResolvedValueOnce(response)

    const api = useExecutionsApi()
    const result: Execution[] | { items?: Execution[]; total?: number } =
      await api.list({ status: 'FAILED', limit: 10 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', {
      query: { status: 'FAILED', limit: 10 },
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })

  it("get(id) calls GET /api/v1/executions/{id}", async () => {
    const response: ExecutionDetail = {
      id: 'abc-123',
      entity: 'ent',
      entityType: 'Process',
      mode: 'PREVIEW',
      status: 'Completed',
      startedAt: '2025-01-01',
      trace: [],
    }
    fetchMock.mockResolvedValueOnce(response)

    const api = useExecutionsApi()
    const result: ExecutionDetail = await api.get('abc-123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/abc-123')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })
})
