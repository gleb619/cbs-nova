export type ExecutionStatus =
  | 'Pending'
  | 'Running'
  | 'Completed'
  | 'Failed'
  | 'Compensated'
  | 'Stale'
export type ExecutionMode = 'PREVIEW' | 'RUN' | 'EXPLAIN'
export type StepType = 'Process' | 'Transaction' | 'Function' | 'Helper'

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

export interface ExecutionFilters {
  status?: ExecutionStatus
  mode?: ExecutionMode
  entityType?: StepType
  entityName?: string
  from?: string
  to?: string
  correlationId?: string
}
