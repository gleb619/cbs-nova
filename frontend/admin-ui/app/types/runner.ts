export type RunnerMode = 'preview' | 'run' | 'explain'

export type RunnerStatus = 'idle' | 'loading' | 'success' | 'failed' | 'running'

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
}