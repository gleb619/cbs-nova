import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { useExecutionEvents, type UseExecutionEventsReturn } from '../useExecutionEvents'

const sources = new Map<string, MockEventSource>()

class MockEventSource implements EventSource {
  static readonly CONNECTING = 0 as const
  static readonly OPEN = 1 as const
  static readonly CLOSED = 2 as const

  readonly url: string
  readonly withCredentials = false
  readyState: number = MockEventSource.CONNECTING

  onopen: ((this: EventSource, ev: Event) => unknown) | null = null
  onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null

  constructor(url: string) {
    this.url = url
    sources.set(url, this)
  }

  addEventListener(): void {}
  removeEventListener(): void {}
  dispatchEvent(): boolean {
    return true
  }

  simulateOpen(): void {
    this.readyState = MockEventSource.OPEN
    this.onopen?.(new Event('open'))
  }

  simulateMessage(data: unknown): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(data) }))
  }

  simulateError(): void {
    this.readyState = MockEventSource.CLOSED
    this.onerror?.(new Event('error'))
  }

  close(): void {
    this.readyState = MockEventSource.CLOSED
    sources.delete(this.url)
  }
}

const flush = async () => {
  await vi.advanceTimersByTimeAsync(0)
  await Promise.resolve()
}

function mountEvents(ids: Ref<Set<string>>) {
  let result: UseExecutionEventsReturn | undefined
  const Comp = defineComponent({
    setup() {
      result = useExecutionEvents({ ids })
      return () => h('div')
    },
  })
  const wrapper = mount(Comp)
  return { wrapper, result: () => result!, ids }
}

describe('useExecutionEvents', () => {
  let harness: ReturnType<typeof mountEvents> | null = null

  beforeEach(() => {
    vi.useFakeTimers()
    sources.clear()
    ;(globalThis as unknown as { EventSource: typeof EventSource }).EventSource =
      MockEventSource as unknown as typeof EventSource
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      get: () => false,
      set: () => {},
    })
  })

  afterEach(async () => {
    harness?.wrapper.unmount()
    harness = null
    sources.clear()
    await vi.runOnlyPendingTimersAsync()
    vi.useRealTimers()
  })

  it('opens an EventSource for each in-flight id', async () => {
    const ids = ref<Set<string>>(new Set(['e1', 'e2']))
    harness = mountEvents(ids)
    await flush()

    expect(sources.size).toBe(2)
    expect(Array.from(sources.values()).map((s) => s.url)).toEqual(
      expect.arrayContaining([
        '/api/v1/executions/e1/events',
        '/api/v1/executions/e2/events',
      ]),
    )
  })

  it('emits parsed execution events', async () => {
    const ids = ref<Set<string>>(new Set(['e1']))
    harness = mountEvents(ids)
    const { result } = harness
    const handler = vi.fn()
    result().onExecutionEvent(handler)
    await flush()

    const source = sources.get('/api/v1/executions/e1/events')
    source?.simulateOpen()
    source?.simulateMessage({ id: 'e1', status: 'Running', timestamp: '2025-01-01T00:00:00Z' })

    await flush()

    expect(result().status.value).toBe('open')
    expect(handler).toHaveBeenCalledWith({
      id: 'e1',
      status: 'Running',
      timestamp: '2025-01-01T00:00:00Z',
    })
  })

  it('closes and reopens the connection when the id set changes', async () => {
    const ids = ref<Set<string>>(new Set(['e1']))
    harness = mountEvents(ids)
    await flush()

    const first = sources.get('/api/v1/executions/e1/events')
    expect(first).toBeDefined()

    ids.value = new Set(['e2'])
    await flush()

    expect(first?.readyState).toBe(MockEventSource.CLOSED)
    expect(sources.has('/api/v1/executions/e1/events')).toBe(false)
    expect(sources.has('/api/v1/executions/e2/events')).toBe(true)
  })

  it('schedules a reconnect after an error and emits the error', async () => {
    const ids = ref<Set<string>>(new Set(['e1']))
    harness = mountEvents(ids)
    const { result } = harness
    const errorHandler = vi.fn()
    result().onError(errorHandler)
    await flush()

    const first = sources.get('/api/v1/executions/e1/events')
    first?.simulateError()
    await flush()

    expect(result().status.value).toBe('error')
    expect(errorHandler).toHaveBeenCalled()
    expect(first?.readyState).toBe(MockEventSource.CLOSED)

    await vi.advanceTimersByTimeAsync(2000)
    await flush()

    expect(sources.has('/api/v1/executions/e1/events')).toBe(true)
  })

  it('pauses connections while hidden and resumes on visible', async () => {
    let hidden = false
    Object.defineProperty(document, 'hidden', {
      configurable: true,
      get: () => hidden,
      set: (v: boolean) => {
        hidden = v
      },
    })

    const ids = ref<Set<string>>(new Set(['e1']))
    harness = mountEvents(ids)
    await flush()

    const source = sources.get('/api/v1/executions/e1/events')
    expect(source).toBeDefined()

    hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
    await flush()

    expect(source?.readyState).toBe(MockEventSource.CLOSED)

    hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
    await flush()

    expect(sources.has('/api/v1/executions/e1/events')).toBe(true)
  })

  it('does nothing when EventSource is not available', async () => {
    ;(globalThis as unknown as { EventSource?: typeof EventSource }).EventSource = undefined
    const ids = ref<Set<string>>(new Set(['e1']))
    harness = mountEvents(ids)
    const { result } = harness
    const handler = vi.fn()
    result().onError(handler)
    await flush()

    expect(result().status.value).toBe('error')
    expect(handler).toHaveBeenCalled()
  })
})
