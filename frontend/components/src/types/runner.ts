export type RunnerMode = 'preview' | 'run' | 'explain'

export type RunnerStatus = 'idle' | 'loading' | 'success' | 'failed' | 'running'

export type CallKind = 'PROCESS' | 'TRANSACTION' | 'HELPER' | 'FUNCTION'

export interface CallNode {
  name: string
  kind: CallKind
  input?: unknown
  output?: unknown
  success: boolean
  children: CallNode[]
  externalCalls: Array<Record<string, unknown>>
}

/**
 * Mirrors the backend `PreviewMetricsSnapshot` record (see
 * `backend/dsl-api/src/main/java/cbs/nova/dsl/PreviewMetricsSnapshot.java`):
 * `executionDurationMs` (long), `memoryUsedBytes` (long),
 * `callCounts` (Map<CallKind, Integer>), `externalCallCounts` (Map<String, Integer>).
 *
 * All numeric fields are nullable to tolerate missing data — the diff view must
 * stay usable when only one side reports metrics.
 */
export interface PreviewMetricsSnapshot {
  executionDurationMs?: number | null
  memoryUsedBytes?: number | null
  callCounts?: Partial<Record<CallKind, number>>
  externalCallCounts?: Record<string, number>
}

export interface DefinitionMeta {
  name: string
  type: string
  inputSchema?: Record<string, unknown>
}

export interface RunnerError {
  message: string
  code?: string
}

export interface RunnerOutput {
  result?: unknown
  metadata?: Record<string, unknown>
  errors?: RunnerError[]
  mermaidDiagram?: string
  description?: string
  executionTrace?: string[]
  workflowId?: string
  astTree?: CallNode
  dryRunLogs?: Array<{ timestamp: string; level: string; logger: string; message: string }>
  metrics?: PreviewMetricsSnapshot
}
