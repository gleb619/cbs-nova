export type EventHandler<P> = (payload: P) => void

export interface Emitter<Events extends Record<string, unknown>> {
  on<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): () => void
  off<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): void
  emit<K extends keyof Events>(event: K, payload?: Events[K]): void
  once<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): () => void
}

export function createEmitter<
  Events extends Record<string, unknown> = Record<string, unknown>,
>(): Emitter<Events> {
  const handlers = new Map<string, Set<EventHandler<unknown>>>()

  function getSet(event: string): Set<EventHandler<unknown>> {
    let set = handlers.get(event)
    if (!set) {
      set = new Set()
      handlers.set(event, set)
    }
    return set
  }

  function on<K extends keyof Events>(
    event: K,
    handler: EventHandler<Events[K]>,
  ): () => void {
    const set = getSet(event as string)
    const wrapped = handler as EventHandler<unknown>
    set.add(wrapped)
    return () => {
      set.delete(wrapped)
    }
  }

  function off<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): void {
    getSet(event as string).delete(handler as EventHandler<unknown>)
  }

  function emit<K extends keyof Events>(event: K, payload?: Events[K]): void {
    const set = handlers.get(event as string)
    if (!set) return
    const copy = Array.from(set)
    for (const handler of copy) {
      handler(payload)
    }
  }

  function once<K extends keyof Events>(
    event: K,
    handler: EventHandler<Events[K]>,
  ): () => void {
    let dispose: (() => void) | null = null
    const wrapper: EventHandler<Events[K]> = (payload) => {
      dispose?.()
      handler(payload)
    }
    dispose = on(event, wrapper)
    return dispose
  }

  return { on, off, emit, once }
}
