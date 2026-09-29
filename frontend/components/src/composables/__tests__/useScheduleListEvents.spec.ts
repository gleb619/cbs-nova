import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { notifySchedulesChanged, onSchedulesChanged } from '../useScheduleListEvents'

/**
 * The handler Set lives at module scope, so every test registers inside its own
 * effectScope and stops it in afterEach — that is the only cleanup path the
 * composable offers (onScopeDispose).
 */
const scopes: ReturnType<typeof effectScope>[] = []

function register(handler: () => void) {
  const scope = effectScope()
  scope.run(() => onSchedulesChanged(handler))
  scopes.push(scope)
  return scope
}

describe('useScheduleListEvents', () => {
  afterEach(() => {
    for (const scope of scopes.splice(0)) {
      scope.stop()
    }
  })

  it('calls a registered handler when notified', () => {
    const handler = vi.fn()
    register(handler)

    notifySchedulesChanged()

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('calls every registered handler exactly once', () => {
    const first = vi.fn()
    const second = vi.fn()
    const third = vi.fn()
    register(first)
    register(second)
    register(third)

    notifySchedulesChanged()

    expect(first).toHaveBeenCalledTimes(1)
    expect(second).toHaveBeenCalledTimes(1)
    expect(third).toHaveBeenCalledTimes(1)
  })

  it('calls the same handler once per notify, not once per registration', () => {
    const handler = vi.fn()
    const scopeA = register(handler)
    const scopeB = register(handler)

    notifySchedulesChanged()

    // A Set keyed by function identity dedupes the duplicate registration.
    expect(handler).toHaveBeenCalledTimes(1)
    scopeB.stop()
    scopeA.stop()
  })

  it('is a no-op when nothing is registered', () => {
    expect(() => notifySchedulesChanged()).not.toThrow()
  })

  it('removes the handler when the owning scope is disposed', () => {
    const handler = vi.fn()
    const scope = register(handler)

    scope.stop()
    notifySchedulesChanged()

    expect(handler).not.toHaveBeenCalled()
  })

  it('keeps notifying handlers in scopes that are still alive', () => {
    const disposed = vi.fn()
    const alive = vi.fn()
    const disposedScope = register(disposed)
    register(alive)

    disposedScope.stop()
    notifySchedulesChanged()

    expect(disposed).not.toHaveBeenCalled()
    expect(alive).toHaveBeenCalledTimes(1)
  })

  it('shares one module-level Set across independent registration sites', () => {
    const early = vi.fn()
    const late = vi.fn()

    // Registered in two separate scopes created at different times, but the
    // backing Set is module scoped, so both are notified.
    register(early)
    register(late)

    notifySchedulesChanged()

    expect(early).toHaveBeenCalledTimes(1)
    expect(late).toHaveBeenCalledTimes(1)
  })

  it('notifies a handler registered in one scope to a caller in another scope', () => {
    const handler = vi.fn()
    register(handler)

    effectScope().run(() => {
      notifySchedulesChanged()
    })

    expect(handler).toHaveBeenCalledTimes(1)
  })
})
