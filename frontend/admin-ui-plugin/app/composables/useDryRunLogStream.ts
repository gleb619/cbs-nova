import { onUnmounted, type Ref, ref } from 'vue'
import { createEmitter } from '../utils/createEmitter'

/** Payload shape of one backend `DryRunLogEvent` SSE row. */
export interface DryRunLogStreamEvent {
  level: string
  message: string
  timestampMillis: number
  mdc: Record<string, string>
  runId: string | null
}

/** Row shape rendered by `DryRunLogsTab` — mirrors `RunnerOutput['dryRunLogs']` items. */
export interface DryRunLogLine {
  timestamp: string
  level: string
  logger: string
  message: string
}

export interface UseDryRunLogStreamReturn {
  status: Ref<'idle' | 'connecting' | 'open' | 'error'>
  /** Open (or replace) the SSE stream for the given trace/run id. No-op when unsupported. */
  connect: (traceId: string) => void
  /** Close the stream and return to idle. */
  close: () => void
  onLogLine: (handler: (line: DryRunLogLine) => void) => () => void
  onOpen: (handler: () => void) => () => void
  onError: (handler: (err?: Event) => void) => () => void
}

/**
 * Live dry-run (preview/explain) log stream, keyed by the trace/run id the caller sent as
 * `X-Request-Id` on the preview/explain request. Single connection — one preview runs at a
 * time. When SSE is unavailable (SSR, old browsers) `status` flips to `error` immediately so
 * consumers fall back to the inline logs in the final response.
 */
export function useDryRunLogStream(): UseDryRunLogStreamReturn {
  const log = typeof console !== 'undefined' ? console : null
  const emitter = createEmitter<{
    line: DryRunLogLine
    open: undefined
    error: Event | undefined
  }>()
  const status = ref<'idle' | 'connecting' | 'open' | 'error'>('idle')
  const supported = typeof EventSource !== 'undefined' && typeof document !== 'undefined'

  let source: EventSource | null = null
  let currentTraceId: string | null = null

  // When SSE is unsupported (SSR, old browsers), surface an error so
  // consumers immediately fall back to the final inline logs.
  if (!supported) {
    status.value = 'error'
    if (typeof setTimeout !== 'undefined') {
      setTimeout(() => emitter.emit('error', undefined), 0)
    }
  }

  function open(traceId: string): void {
    if (!supported) return
    try {
      const url = `/api/v1/dsl/dry-run/${encodeURIComponent(traceId)}/logs`
      const next = new EventSource(url)
      source = next
      status.value = 'connecting'

      next.onopen = () => {
        status.value = 'open'
        emitter.emit('open')
      }

      next.onmessage = (event: MessageEvent) => {
        try {
          const payload = JSON.parse(event.data) as DryRunLogStreamEvent
          emitter.emit('line', {
            timestamp: new Date(payload.timestampMillis).toISOString(),
            level: typeof payload.level === 'string' ? payload.level : '',
            logger: '',
            message: typeof payload.message === 'string' ? payload.message : '',
          })
        } catch (err) {
          log?.warn('dry-run log event parse failed', { data: event.data, error: String(err) })
        }
      }

      next.onerror = (err) => {
        status.value = 'error'
        emitter.emit('error', err)
        if (source === next) {
          source = null
        }
        currentTraceId = null
        try {
          next.close()
        } catch (_) {
          // ignore
        }
      }
    } catch (err) {
      status.value = 'error'
      emitter.emit('error', undefined)
    }
  }

  function connect(traceId: string): void {
    if (!supported || !traceId) return
    if (currentTraceId === traceId && source) return
    close()
    currentTraceId = traceId
    open(traceId)
  }

  function close(): void {
    currentTraceId = null
    const current = source
    source = null
    if (current) {
      try {
        current.close()
      } catch (_) {
        // ignore
      }
    }
    status.value = 'idle'
  }

  onUnmounted(close)

  return {
    status,
    connect,
    close,
    onLogLine: (handler) => emitter.on('line', handler),
    onOpen: (handler) => emitter.on('open', handler),
    onError: (handler) => emitter.on('error', handler),
  }
}
