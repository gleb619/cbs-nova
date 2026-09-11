import { computed, onUnmounted, ref, watch, type Ref } from 'vue'
import type { ExecutionStatus } from '~/types'
import { createEmitter } from '../utils/createEmitter'

export interface ExecutionEvent {
  id: string
  status: ExecutionStatus
  timestamp: string
}

export interface UseExecutionEventsOptions {
  ids: Ref<Set<string>> | Ref<readonly string[]> | Ref<string[]>
}

export interface UseExecutionEventsReturn {
  status: Ref<'idle' | 'connecting' | 'open' | 'error'>
  onExecutionEvent: (handler: (event: ExecutionEvent) => void) => () => void
  onOpen: (handler: () => void) => () => void
  onError: (handler: (err?: Event) => void) => () => void
}

interface Connection {
  source: EventSource | null
  timer: ReturnType<typeof setTimeout> | null
  attempts: number
}

export function useExecutionEvents(options: UseExecutionEventsOptions): UseExecutionEventsReturn {
  const idsRef = options.ids
  const log = typeof console !== 'undefined' ? console : null
  const emitter = createEmitter<{
    event: ExecutionEvent
    open: undefined
    error: Event | undefined
  }>()
  const status = ref<'idle' | 'connecting' | 'open' | 'error'>('idle')
  const supported = typeof EventSource !== 'undefined' && typeof document !== 'undefined'

  // When SSE is unsupported (SSR, old browsers), surface an error so
  // consumers immediately fall back to polling instead of waiting forever.
  if (!supported) {
    status.value = 'error'
    if (typeof setTimeout !== 'undefined') {
      setTimeout(() => emitter.emit('error', undefined), 0)
    }
  }

  const connections = new Map<string, Connection>()
  let paused = false
  let visibilityHandler: (() => void) | null = null

  function desiredIds(): Set<string> {
    const raw = idsRef.value
    const arr = raw instanceof Set ? Array.from(raw) : Array.from(raw ?? [])
    return new Set(arr.filter((id): id is string => typeof id === 'string' && id.length > 0))
  }

  function open(id: string, conn: Connection): void {
    if (!supported || paused) return
    try {
      const url = `/api/v1/executions/${encodeURIComponent(id)}/events`
      const source = new EventSource(url)
      conn.source = source
      conn.attempts = 0
      status.value = 'connecting'

      source.onopen = () => {
        conn.attempts = 0
        status.value = 'open'
        emitter.emit('open')
      }

      source.onmessage = (event: MessageEvent) => {
        try {
          const payload = JSON.parse(event.data) as ExecutionEvent
          emitter.emit('event', payload)
        } catch (err) {
          log?.warn('execution event parse failed', { data: event.data, error: String(err) })
        }
      }

      source.onerror = (err) => {
        status.value = 'error'
        emitter.emit('error', err)
        close(id)
        if (!paused) {
          const c = connections.get(id)
          if (c) {
            c.attempts++
            const delay = Math.min(1000 * 2 ** c.attempts, 30000)
            c.timer = setTimeout(() => {
              c.timer = null
              open(id, c)
            }, delay)
          }
        }
      }
    } catch (err) {
      status.value = 'error'
      emitter.emit('error', undefined)
    }
  }

  function close(id: string): void {
    const conn = connections.get(id)
    if (!conn) return
    if (conn.timer) {
      clearTimeout(conn.timer)
      conn.timer = null
    }
    try {
      conn.source?.close()
    } catch (_) {
      // ignore
    }
    conn.source = null
  }

  function remove(id: string): void {
    close(id)
    connections.delete(id)
  }

  function connect(id: string): void {
    if (!supported || paused) return
    if (connections.has(id)) return
    const conn: Connection = { source: null, timer: null, attempts: 0 }
    connections.set(id, conn)
    open(id, conn)
  }

  function sync(): void {
    const desired = desiredIds()
    for (const id of Array.from(connections.keys())) {
      if (!desired.has(id)) remove(id)
    }
    for (const id of desired) {
      if (!connections.has(id)) connect(id)
    }
    if (desired.size === 0 && status.value !== 'error') {
      status.value = 'idle'
    }
  }

  const stopWatch = watch(
    () => Array.from(desiredIds()).sort(),
    sync,
    { immediate: true },
  )

  function onVisibilityChange(): void {
    if (typeof document === 'undefined') return
    paused = document.hidden
    if (paused) {
      for (const id of Array.from(connections.keys())) remove(id)
    } else {
      sync()
    }
  }

  if (supported) {
    visibilityHandler = onVisibilityChange
    document.addEventListener('visibilitychange', visibilityHandler)
  }

  onUnmounted(() => {
    stopWatch()
    for (const id of Array.from(connections.keys())) remove(id)
    if (visibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', visibilityHandler)
    }
  })

  return {
    status,
    onExecutionEvent: (handler) => emitter.on('event', handler),
    onOpen: (handler) => emitter.on('open', handler),
    onError: (handler) => emitter.on('error', handler),
  }
}
