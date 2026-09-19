import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import {
  type DryRunLogLine,
  type UseDryRunLogStreamReturn,
  useDryRunLogStream,
} from '../useDryRunLogStream'

const sources = new Map<string, MockEventSource>()

class MockEventSource implements EventSource {
  static readonly CONNECTING = 0 as const
  static readonly OPEN = 1 as const
  static readonly CLOSED = 2 as const

  readonly CONNECTING = MockEventSource.CONNECTING
  readonly OPEN = MockEventSource.OPEN
  readonly CLOSED = MockEventSource.CLOSED

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

function mountStream() {
  let result: UseDryRunLogStreamReturn | undefined
  const Comp = defineComponent({
    setup() {
      result = useDryRunLogStream()
      return () => h('div')
    },
  })
  const wrapper = mount(Comp)
  return { wrapper, result: () => result! }
}

describe('useDryRunLogStream', () => {
  let harness: ReturnType<typeof mountStream> | null = null

  beforeEach(() => {
    sources.clear()
    ;(globalThis as unknown as { EventSource: typeof EventSource }).EventSource =
      MockEventSource as unknown as typeof EventSource
  })

  afterEach(() => {
    harness?.wrapper.unmount()
    harness = null
    sources.clear()
  })

  it('opens an EventSource keyed by the trace id', () => {
    harness = mountStream()
    harness.result().connect('trace-1')

    expect(sources.size).toBe(1)
    expect(sources.get('/api/v1/dsl/dry-run/trace-1/logs')).toBeDefined()
    expect(harness.result().status.value).toBe('connecting')
  })

  it('emits ISO-timestamped log lines from SSE payloads', () => {
    harness = mountStream()
    const lines: DryRunLogLine[] = []
    harness.result().onLogLine((line) => lines.push(line))
    harness.result().connect('trace-1')

    const source = sources.get('/api/v1/dsl/dry-run/trace-1/logs')
    source?.simulateOpen()
    expect(harness.result().status.value).toBe('open')

    source?.simulateMessage({
      level: 'INFO',
      message: 'live line',
      timestampMillis: 1_752_900_000_000,
      mdc: {},
      runId: 'trace-1',
    })

    expect(lines).toHaveLength(1)
    expect(lines[0].level).toBe('INFO')
    expect(lines[0].message).toBe('live line')
    expect(lines[0].timestamp).toBe(new Date(1_752_900_000_000).toISOString())
  })

  it('replaces the connection when connect is called with a new trace id', () => {
    harness = mountStream()
    harness.result().connect('trace-1')
    harness.result().connect('trace-2')

    expect(sources.get('/api/v1/dsl/dry-run/trace-1/logs')).toBeUndefined()
    expect(sources.get('/api/v1/dsl/dry-run/trace-2/logs')).toBeDefined()
  })

  it('close() shuts the stream down', () => {
    harness = mountStream()
    harness.result().connect('trace-1')
    harness.result().close()

    expect(sources.size).toBe(0)
    expect(harness.result().status.value).toBe('idle')
  })

  it('emits an error and closes when the stream fails', () => {
    harness = mountStream()
    const onError = vi.fn()
    harness.result().onError(onError)
    harness.result().connect('trace-1')

    sources.get('/api/v1/dsl/dry-run/trace-1/logs')?.simulateError()

    expect(onError).toHaveBeenCalledTimes(1)
    expect(harness.result().status.value).toBe('error')
    expect(sources.size).toBe(0)
  })

  it('emits an error immediately when EventSource is unsupported (SSR fallback)', () => {
    const original = globalThis.EventSource
    // @ts-expect-error simulate SSR
    delete globalThis.EventSource
    try {
      harness = mountStream()
      const onError = vi.fn()
      harness.result().onError(onError)

      expect(harness.result().status.value).toBe('error')
      harness.result().connect('trace-1')
      expect(sources.size).toBe(0)
    } finally {
      ;(globalThis as unknown as { EventSource: typeof EventSource }).EventSource =
        original ?? (MockEventSource as unknown as typeof EventSource)
    }
  })
})
