import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../useDslApi', () => {
  const fetchChangeRequests = vi.fn()
  const submitChangeRequest = vi.fn()
  const approveChangeRequest = vi.fn()
  const rejectChangeRequest = vi.fn()
  return {
    useDslApi: () => ({
      fetchChangeRequests,
      submitChangeRequest,
      approveChangeRequest,
      rejectChangeRequest,
    }),
  }
})

vi.mock('../useClientLogger', () => {
  const error = vi.fn()
  const info = vi.fn()
  const warn = vi.fn()
  return {
    useClientLogger: () => ({ error, info, warn }),
  }
})

// eslint-disable-next-line import/first
import { useApprovals } from '../useApprovals'
// eslint-disable-next-line import/first
import * as loggerModule from '../useClientLogger'
// eslint-disable-next-line import/first
import * as apiModule from '../useDslApi'

type ApiMock = {
  fetchChangeRequests: ReturnType<typeof vi.fn>
  submitChangeRequest: ReturnType<typeof vi.fn>
  approveChangeRequest: ReturnType<typeof vi.fn>
  rejectChangeRequest: ReturnType<typeof vi.fn>
}

type LoggerMock = {
  error: ReturnType<typeof vi.fn>
  info: ReturnType<typeof vi.fn>
  warn: ReturnType<typeof vi.fn>
}

const getApiMocks = (): ApiMock =>
  (apiModule as unknown as { useDslApi: () => ApiMock }).useDslApi()

const getLoggerMocks = (): LoggerMock =>
  (loggerModule as unknown as { useClientLogger: () => LoggerMock }).useClientLogger()

function changeRequest(id: number) {
  return {
    id,
    definitionName: `def-${id}`,
    draftContent: '{}',
    requestedBy: 'user-1',
    requestedAt: '2026-09-19T00:00:00Z',
    status: 'PENDING' as const,
    approvedBy: null,
    approvedAt: null,
    comment: null,
  }
}

describe('useApprovals', () => {
  beforeEach(() => {
    const api = getApiMocks()
    api.fetchChangeRequests.mockReset()
    api.submitChangeRequest.mockReset()
    api.approveChangeRequest.mockReset()
    api.rejectChangeRequest.mockReset()
    const logger = getLoggerMocks()
    logger.error.mockReset()
    logger.info.mockReset()
    logger.warn.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  it('initial state: empty items, loading false, error null', () => {
    const { items, loading, error } = useApprovals()

    expect(items.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load() populates items and toggles loading', async () => {
    const items = [changeRequest(1), changeRequest(2)]
    let resolve: (v: unknown) => void = () => {}
    getApiMocks().fetchChangeRequests.mockReturnValue(
      new Promise<unknown>((r) => {
        resolve = r
      }),
    )

    const { items: result, loading, error, load } = useApprovals()

    const p = load()
    expect(loading.value).toBe(true)

    resolve(items)
    await p

    expect(getApiMocks().fetchChangeRequests).toHaveBeenCalledWith({
      definitionName: undefined,
      status: undefined,
    })
    expect(result.value).toEqual(items)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('load(definitionName, status) forwards the filters', async () => {
    getApiMocks().fetchChangeRequests.mockResolvedValue([])

    const { load } = useApprovals()
    await load('LoanDsl', 'PENDING')

    expect(getApiMocks().fetchChangeRequests).toHaveBeenCalledWith({
      definitionName: 'LoanDsl',
      status: 'PENDING',
    })
  })

  it('load() on rejection sets error, clears items and logs error once', async () => {
    const api = getApiMocks()
    const logger = getLoggerMocks()
    api.fetchChangeRequests.mockRejectedValue(new Error('boom'))

    const { items, loading, error, load } = useApprovals()
    await load()

    expect(error.value).toBe('boom')
    expect(items.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(logger.error).toHaveBeenCalledTimes(1)
    expect(logger.error).toHaveBeenCalledWith('failed to load change requests', { error: 'boom' })
  })

  it('submit(name) calls submitChangeRequest then reloads', async () => {
    const api = getApiMocks()
    api.fetchChangeRequests.mockResolvedValue([])

    const { submit } = useApprovals()
    await submit('LoanDsl')

    expect(api.submitChangeRequest).toHaveBeenCalledWith('LoanDsl')
    expect(api.fetchChangeRequests).toHaveBeenCalled()
    const submitOrder = api.submitChangeRequest.mock.invocationCallOrder[0]
    const listOrder = api.fetchChangeRequests.mock.invocationCallOrder[0]
    expect(submitOrder).toBeLessThan(listOrder)
  })

  it('approve(id, comment?) calls approveChangeRequest then reloads', async () => {
    const api = getApiMocks()
    api.fetchChangeRequests.mockResolvedValue([])

    const { approve } = useApprovals()
    await approve(7, 'ship it')

    expect(api.approveChangeRequest).toHaveBeenCalledWith(7, 'ship it')
    expect(api.fetchChangeRequests).toHaveBeenCalled()
    const approveOrder = api.approveChangeRequest.mock.invocationCallOrder[0]
    const listOrder = api.fetchChangeRequests.mock.invocationCallOrder[0]
    expect(approveOrder).toBeLessThan(listOrder)
  })

  it('reject(id, comment) calls rejectChangeRequest then reloads', async () => {
    const api = getApiMocks()
    api.fetchChangeRequests.mockResolvedValue([])

    const { reject } = useApprovals()
    await reject(7, 'nope')

    expect(api.rejectChangeRequest).toHaveBeenCalledWith(7, 'nope')
    expect(api.fetchChangeRequests).toHaveBeenCalled()
    const rejectOrder = api.rejectChangeRequest.mock.invocationCallOrder[0]
    const listOrder = api.fetchChangeRequests.mock.invocationCallOrder[0]
    expect(rejectOrder).toBeLessThan(listOrder)
  })
})
