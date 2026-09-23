import { onUnmounted, type Ref, ref } from 'vue'
import { createEmitter } from '../utils/createEmitter'

export interface IntervalEmitterEvents {
  /** Fired on every visible tick. */
  tick: undefined
  /** Fired once when the tab becomes visible again after being hidden. */
  resume: undefined
  [key: string]: unknown
}

export interface UseIntervalEmitterOptions {
  /** Interval in milliseconds; can be a reactive ref. */
  intervalMs: number | Ref<number>
  /** Pause ticks while the document is hidden and emit a resume tick on return. Default true. */
  pauseOnHidden?: boolean
}

export interface UseIntervalEmitterReturn {
  /** True while the ticker is running. */
  running: Ref<boolean>
  /** Start (or restart) ticking. */
  start: () => void
  /** Stop ticking and detach listeners. */
  stop: () => void
  /** Change the interval and restart if already running. */
  setIntervalMs: (ms: number) => void
  /** Listen to visible ticks. */
  onTick: (handler: () => void) => () => void
  /** Listen to resume events when the tab becomes visible. */
  onResume: (handler: () => void) => () => void
}

/**
 * Generic interval-driven event emitter.
 *
 * The timer itself is the only polling primitive in the app. Everything
 * else reacts to the `tick`/`resume` events, so consumers stay decoupled
 * from setInterval and the Page Visibility API.
 */
export function useIntervalEmitter(options: UseIntervalEmitterOptions): UseIntervalEmitterReturn {
  const intervalMsRef =
    typeof options.intervalMs === 'number' ? ref(options.intervalMs) : options.intervalMs

  const emitter = createEmitter<IntervalEmitterEvents>()
  const running = ref(false)
  let interval: ReturnType<typeof setInterval> | null = null
  let visibilityHandler: (() => void) | null = null

  function tick(): void {
    emitter.emit('tick')
  }

  function start(): void {
    if (interval !== null) return
    if (typeof document === 'undefined') return

    running.value = true
    interval = setInterval(() => {
      if (typeof document !== 'undefined' && document.hidden) return
      tick()
    }, intervalMsRef.value)

    if (options.pauseOnHidden !== false) {
      visibilityHandler = () => {
        if (!interval || document.hidden) return
        tick()
        emitter.emit('resume')
      }
      document.addEventListener('visibilitychange', visibilityHandler)
    }
  }

  function stop(): void {
    running.value = false
    if (interval !== null) {
      clearInterval(interval)
      interval = null
    }
    if (visibilityHandler && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', visibilityHandler)
    }
    visibilityHandler = null
  }

  function setIntervalMs(ms: number): void {
    intervalMsRef.value = ms
    if (interval !== null) {
      stop()
      start()
    }
  }

  onUnmounted(() => {
    stop()
  })

  return {
    running,
    start,
    stop,
    setIntervalMs,
    onTick: (handler) => emitter.on('tick', handler),
    onResume: (handler) => emitter.on('resume', handler),
  }
}
