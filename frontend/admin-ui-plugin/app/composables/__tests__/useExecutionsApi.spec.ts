import type { Execution, ExecutionDetail, TransactionExecutionDto } from '@cbs/components/types'
import { beforeEach, describe, expect, it, vi } from 'vitest'
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
    const result: Execution[] | { items?: Execution[]; total?: number } = await api.list({
      status: 'FAILED',
      limit: 10,
    })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', {
      query: { status: 'FAILED', limit: 10 },
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })

  it('list({ offset, limit }) serializes paging args as query params', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0 })

    const api = useExecutionsApi()
    await api.list({ offset: 40, limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', {
      query: { offset: 40, limit: 20 },
    })
  })

  it('list({ offset: 0, limit }) still forwards an explicit zero offset', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0 })

    const api = useExecutionsApi()
    await api.list({ offset: 0, limit: 20 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', {
      query: { offset: 0, limit: 20 },
    })
  })

  it('list(filters, offset, limit) merges filters and paging args in the query', async () => {
    fetchMock.mockResolvedValueOnce({ items: [], total: 0 })

    const api = useExecutionsApi()
    await api.list({ status: 'Completed', entityName: 'foo', offset: 20, limit: 10 })

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions', {
      query: { status: 'Completed', entityName: 'foo', offset: 20, limit: 10 },
    })
  })

  it('get(id) calls GET /api/v1/executions/{id}', async () => {
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

  it('cancel(id) POSTs to /api/v1/executions/{id}/cancel and returns the fresh row', async () => {
    const response: ExecutionDetail = {
      id: 'abc-123',
      entity: 'ent',
      entityType: 'Process',
      mode: 'RUN',
      status: 'Cancelled',
      startedAt: '2025-01-01',
      trace: [],
    }
    fetchMock.mockResolvedValueOnce(response)

    const api = useExecutionsApi()
    const result: ExecutionDetail = await api.cancel('abc-123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/abc-123/cancel', {
      method: 'POST',
    })
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })

  it('getTransactions(id) GETs /api/v1/executions/{id}/transactions', async () => {
    const response: TransactionExecutionDto[] = [
      {
        transactionName: 'apply',
        input: { amount: 100 },
        executedAt: '2026-01-01T00:00:00Z',
      },
    ]
    fetchMock.mockResolvedValueOnce(response)

    const api = useExecutionsApi()
    const result: TransactionExecutionDto[] = await api.getTransactions('abc-123')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/executions/abc-123/transactions')
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(result).toEqual(response)
  })
})
