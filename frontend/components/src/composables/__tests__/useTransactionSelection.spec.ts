import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import type { TransactionExecutionDto } from '../../types/execution'
import { onTransactionSelected, selectTransaction } from '../useTransactionSelection'

/**
 * Unlike the plan's original "selection state/toggle" description, the shipped
 * composable is a module-scope event bus: onTransactionSelected registers,
 * selectTransaction dispatches. The handler Set is module scoped and the only
 * cleanup path is onScopeDispose, so every test registers in its own
 * effectScope and stops it in afterEach.
 */
const scopes: ReturnType<typeof effectScope>[] = []

function register(handler: (tx: TransactionExecutionDto) => void) {
  const scope = effectScope()
  scope.run(() => onTransactionSelected(handler))
  scopes.push(scope)
  return scope
}

function tx(overrides: Partial<TransactionExecutionDto> = {}): TransactionExecutionDto {
  return {
    transactionName: 'transfer',
    executedAt: '2026-01-01T00:00:00Z',
    status: 'SUCCESS',
    startedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

describe('useTransactionSelection', () => {
  afterEach(() => {
    for (const scope of scopes.splice(0)) {
      scope.stop()
    }
  })

  it('is a no-op when no handler is registered', () => {
    // Every other test disposes its scope in afterEach, so the module-level Set
    // is empty here — a bare dispatch must not throw.
    expect(() => selectTransaction(tx())).not.toThrow()
  })

  it('dispatches the transaction to a registered handler', () => {
    const handler = vi.fn()
    const transaction = tx({ transactionName: 'settlement' })
    register(handler)

    selectTransaction(transaction)

    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler).toHaveBeenCalledWith(transaction)
  })

  it('dispatches to every registered handler exactly once', () => {
    const first = vi.fn()
    const second = vi.fn()
    register(first)
    register(second)

    const transaction = tx()
    selectTransaction(transaction)

    expect(first).toHaveBeenCalledWith(transaction)
    expect(second).toHaveBeenCalledWith(transaction)
  })

  it('calls a handler once per selectTransaction, not once per registration', () => {
    const handler = vi.fn()
    const scopeA = register(handler)
    const scopeB = register(handler)

    selectTransaction(tx())

    // A Set keyed by function identity dedupes the duplicate registration.
    expect(handler).toHaveBeenCalledTimes(1)
    scopeB.stop()
    scopeA.stop()
  })

  it('forwards the full payload including optional fields', () => {
    const handler = vi.fn()
    register(handler)

    const transaction = tx({
      input: { account: 'ACC-1' },
      finishedAt: '2026-01-01T00:00:05Z',
      duration: 5000,
      error: 'timeout',
      status: 'FAILED',
    })

    selectTransaction(transaction)

    expect(handler.mock.calls[0]?.[0]).toEqual(transaction)
  })

  it('stops dispatching to a handler once its scope is disposed', () => {
    const handler = vi.fn()
    const scope = register(handler)

    scope.stop()
    selectTransaction(tx())

    expect(handler).not.toHaveBeenCalled()
  })

  it('keeps dispatching to handlers in scopes that are still alive', () => {
    const disposed = vi.fn()
    const alive = vi.fn()
    const disposedScope = register(disposed)
    register(alive)

    disposedScope.stop()
    const transaction = tx()
    selectTransaction(transaction)

    expect(disposed).not.toHaveBeenCalled()
    expect(alive).toHaveBeenCalledWith(transaction)
  })

  it('shares one module-level handler Set across independent registration sites', () => {
    const early = vi.fn()
    const late = vi.fn()

    register(early)
    register(late)

    const transaction = tx({ transactionName: 'audit' })
    selectTransaction(transaction)

    expect(early).toHaveBeenCalledWith(transaction)
    expect(late).toHaveBeenCalledWith(transaction)
  })

  it('dispatches to a handler registered by a different scope than the caller', () => {
    const handler = vi.fn()
    register(handler)

    effectScope().run(() => {
      selectTransaction(tx())
    })

    expect(handler).toHaveBeenCalledTimes(1)
  })
})
