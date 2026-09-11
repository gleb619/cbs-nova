import { onScopeDispose } from 'vue'

type SchedulesChangedHandler = () => void

const handlers = new Set<SchedulesChangedHandler>()

export function notifySchedulesChanged() {
  for (const handler of handlers) {
    handler()
  }
}

export function onSchedulesChanged(handler: SchedulesChangedHandler) {
  handlers.add(handler)
  onScopeDispose(() => handlers.delete(handler))
}
