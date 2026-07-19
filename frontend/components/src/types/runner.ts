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
}
