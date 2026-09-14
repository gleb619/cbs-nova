export type ExecutionStatus =
  | 'Pending'
  | 'Running'
  | 'Completed'
  | 'Failed'
  | 'Compensated'
  | 'Stale'
  | 'Cancelled'
  | 'SUCCESS'
  | 'FAILED'
  | 'RUNNING'
export type ExecutionMode = 'PREVIEW' | 'RUN' | 'EXPLAIN'
export type StepType = 'Process' | 'Transaction' | 'Function' | 'Helper'

export type TransactionExecutionStatus = 'SUCCESS' | 'FAILED' | 'COMPENSATED'

export interface Execution {
  id: string
  entity: string
  entityType: StepType
  mode: ExecutionMode
  status: ExecutionStatus
  startedAt: string
  completedAt?: string
  duration?: number
  retries?: number
  triggeredBy?: string
  correlationId?: string
  workflowId?: string
}

export interface TraceStep {
  id: string
  parentId?: string
  stepType: StepType
  name: string
  status: ExecutionStatus
  startedAt?: string
  duration?: number
  retries?: number
  input?: unknown
  output?: unknown
  error?: string
  isCompensation?: boolean
}

export interface ExecutionDetail extends Execution {
  trace?: TraceStep[]
  input?: unknown
  output?: unknown
  metadata?: Record<string, unknown>
  logs?: Array<{ timestamp: string; step?: string; severity: string; message: string }>
  errors?: Array<{ message: string; code?: string; stackTrace?: string }>
  mermaidDiagram?: string
}

export interface TransactionExecutionDto {
  transactionName: string
  input?: unknown
  executedAt: string
  /** Transaction lifecycle status. Always present after the T326 backend enrichment. */
  status: TransactionExecutionStatus
  /** ISO timestamp when the transaction started. */
  startedAt: string
  /** ISO timestamp when the transaction finished. Optional while the transaction is still in-flight. */
  finishedAt?: string
  /** Duration in milliseconds. Optional; derived from start/end if absent. */
  duration?: number
  /** Error message for FAILED / COMPENSATED records. */
  error?: string
}

export interface ExecutionFilters {
  status?: ExecutionStatus
  mode?: ExecutionMode
  entityType?: StepType
  entityName?: string
  from?: string
  to?: string
  correlationId?: string
}
