import { onScopeDispose } from 'vue'
import type { TransactionExecutionDto } from '../types/execution'

type TransactionSelectionHandler = (tx: TransactionExecutionDto) => void

const handlers = new Set<TransactionSelectionHandler>()

export function selectTransaction(tx: TransactionExecutionDto) {
  for (const handler of handlers) {
    handler(tx)
  }
}

export function onTransactionSelected(handler: TransactionSelectionHandler) {
  handlers.add(handler)
  onScopeDispose(() => handlers.delete(handler))
}
