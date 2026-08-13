import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useStalePolling } from '../useStalePolling'

type ApiMock = {
  list: ReturnType<typeof vi.fn>
  get: ReturnType<typeof vi.fn>
}

const installApiMock = (overrides: Partial<ApiMock> = {}): ApiMock => {
  const api: ApiMock = {
    list: overrides.list ?? vi.fn(),
    get: overrides.get ?? vi.fn(),
  }
  vi.mocked(useExecutionsApi as never).mockReturnValue(api)
  return api
}

const flush = async () => {
  await vi.advanceTimersByTimeAsync(0)
  await Promise.resolve()
}

describe('useStalePolling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts polling as soon as status is Stale and stops on transition', async () => {
    const api = installApiMock({
      get: vi
        .fn()
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Stale',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        })
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Stale',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        })
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Running',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(true)
    expect(vi.getTimerCount()).toBe(1)
    expect(api.get).not.toHaveBeenCalled()

    // first interval tick — still Stale, polling continues
    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(api.get).toHaveBeenCalledWith('e1')
    expect(polling.value).toBe(true)

    // second tick — still Stale
    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(polling.value).toBe(true)

    // third tick — Running, polling stops
    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(3)
    expect(polling.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)

    // subsequent ticks must NOT fire
    await vi.advanceTimersByTimeAsync(5000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(3)
  })

  it('polls the id of an id-ref (reactive)', async () => {
    const api = installApiMock({
      get: vi.fn().mockResolvedValue({
        id: 'e1',
        status: 'Stale',
        entity: '',
        entityType: 'Process',
        mode: 'PREVIEW',
        startedAt: '',
      }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    const id = ref('e1')

    useStalePolling({ status, id, intervalMs: 1000 })

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledWith('e1')

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(api.get).toHaveBeenLastCalledWith('e1')
  })

  it('does not poll for non-Stale statuses (Pending)', () => {
    const api = installApiMock({ get: vi.fn() })

    const status = ref<ExecutionStatus | null>('Pending')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('does not poll for non-Stale statuses (Completed)', () => {
    const api = installApiMock({ get: vi.fn() })

    const status = ref<ExecutionStatus | null>('Completed')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('does not poll for null status', () => {
    const api = installApiMock({ get: vi.fn() })

    const status = ref<ExecutionStatus | null>(null)
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('stops polling when status transitions to a terminal state', async () => {
    const api = installApiMock({
      get: vi
        .fn()
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Stale',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        })
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Failed',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(1)
    // still Stale, polling continues
    expect(polling.value).toBe(true)
    expect(vi.getTimerCount()).toBe(1)

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)
    // consumer's status ref should now reflect the new value
    expect(status.value).toBe('Failed')
    expect(polling.value).toBe(false)
    expect(vi.getTimerCount()).toBe(0)

    // no more fetches
    await vi.advanceTimersByTimeAsync(5000)
    expect(api.get).toHaveBeenCalledTimes(2)
  })

  it('stops polling when status transitions to Running (back from stale)', async () => {
    const api = installApiMock({
      get: vi
        .fn()
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Stale',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        })
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Running',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    expect(polling.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(polling.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(status.value).toBe('Running')
    expect(polling.value).toBe(false)
  })

  it('stops polling on stop() (clears interval and listener)', async () => {
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    const api = installApiMock({
      get: vi.fn().mockResolvedValue({
        id: 'e1',
        status: 'Stale',
        entity: '',
        entityType: 'Process',
        mode: 'PREVIEW',
        startedAt: '',
      }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    const composable = useStalePolling({ status, id: 'e1', intervalMs: 1000 })
    expect(vi.getTimerCount()).toBe(1)
    expect(composable.polling.value).toBe(true)

    composable.stop()
    expect(vi.getTimerCount()).toBe(0)
    expect(composable.polling.value).toBe(false)
    // visibilitychange listener was detached
    expect(removeSpy).toHaveBeenCalledWith('visibilitychange', expect.any(Function))

    // no further fetches should fire after stop
    await vi.advanceTimersByTimeAsync(5000)
    expect(api.get).toHaveBeenCalledTimes(0)

    removeSpy.mockRestore()
  })

  it('skips ticks while document.hidden is true and resumes on visible', async () => {
    const api = installApiMock({
      get: vi.fn().mockResolvedValue({
        id: 'e1',
        status: 'Stale',
        entity: '',
        entityType: 'Process',
        mode: 'PREVIEW',
        startedAt: '',
      }),
    })

    const status = ref<ExecutionStatus | null>('Stale')
    useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    // first tick (visible)
    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(1)

    // simulate tab hidden
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => true })
    document.dispatchEvent(new Event('visibilitychange'))

    // several ticks while hidden should NOT call api.get
    await vi.advanceTimersByTimeAsync(5000)
    expect(api.get).toHaveBeenCalledTimes(1)

    // simulate tab visible again — should fire one immediate tick
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
    document.dispatchEvent(new Event('visibilitychange'))
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)

    // and ticks resume
    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(3)

    // restore
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => false })
  })

  it('keeps the interval alive on a network error and stops once a fetch succeeds with a non-Stale status', async () => {
    const api = installApiMock({
      get: vi
        .fn()
        // first tick: throws (network blip)
        .mockRejectedValueOnce(new Error('network'))
        // second tick: returns Running, stops
        .mockResolvedValueOnce({
          id: 'e1',
          status: 'Running',
          entity: '',
          entityType: 'Process',
          mode: 'PREVIEW',
          startedAt: '',
        }),
    })

    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    const status = ref<ExecutionStatus | null>('Stale')
    const { polling } = useStalePolling({ status, id: 'e1', intervalMs: 1000 })

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(polling.value).toBe(true)
    expect(errSpy).toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1000)
    await flush()
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(status.value).toBe('Running')
    expect(polling.value).toBe(false)

    errSpy.mockRestore()
  })
})
