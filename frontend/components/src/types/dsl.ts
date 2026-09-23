export type ConstructType = 'Process' | 'Transaction' | 'Function' | 'Helper'
export type ConstructStatus = 'Draft' | 'Valid' | 'Invalid' | 'Published' | 'Modified'

export interface DslConstruct {
  name: string
  type: ConstructType
  status: ConstructStatus
  version?: string
  taskQueue?: string
  inputType?: string
  outputType?: string
  hasCompensation?: boolean
  description?: string
  filePath?: string
}

export interface ValidationError {
  field: string
  message: string
  severity: 'error' | 'warning'
  /** 1-based source line, when known. `null`/omitted → editor falls back to line 1. */
  line?: number | null
  /** 1-based source column, when known. `null`/omitted → editor falls back to column 1. */
  column?: number | null
}

export interface StructureFieldDto {
  path: string
  value?: string | null
  type: string
  description?: string
}

export type LogicKind = 'execute' | 'preview' | 'explain'
export type LogicStatus = 'configured' | 'default'

export interface LogicInfoDto {
  kind: LogicKind
  status: LogicStatus
  required: boolean
  description?: string
}

export interface ObjectStructureDto {
  name: string
  type: 'process' | 'transaction' | 'helper' | 'function'
  fields: StructureFieldDto[]
  /** Absent on backends that predate the logic introspection feature. */
  logic?: LogicInfoDto[]
}

export interface HelperCatalogEntry {
  name: string
  description?: string
  inputType?: string
  outputType?: string
}

export interface HelpersResponse {
  names: string[]
  helpers: HelperCatalogEntry[]
}

export type { CompileDiagnostic } from './api'

export type DiagnosticSource = 'RELOAD' | 'PUBLISH' | 'DRAFT'

export interface PersistedCompileDiagnostic {
  id: number
  occurredAt: string
  source: DiagnosticSource
  definition: string
  file?: string | null
  line?: number | null
  column?: number | null
  severity: string
  code?: string | null
  message: string
}

export interface DiagnosticsPage {
  items: PersistedCompileDiagnostic[]
  total: number
  offset: number
  limit: number
}

export type DefinitionTestStatus = 'PASS' | 'FAIL' | 'ERROR'

export interface DefinitionTestCase {
  caseName: string
  /** `DslRequest` on the backend — arbitrary JSON on the client. */
  input: unknown
  /** `PreviewReport` on the backend — arbitrary JSON on the client. */
  expectedOutput: unknown
}

export interface DefinitionTestCaseResult {
  name: string
  status: DefinitionTestStatus
  actual?: unknown | null
  expected: unknown
  durationMs: number
  /** `ErrorResponse` on the backend — present for ERROR results. */
  diagnostics?: unknown
}

export interface DefinitionTestRunReport {
  total: number
  passed: number
  failed: number
  errored: number
  cases: DefinitionTestCaseResult[]
}

export interface ScheduleSummary {
  scheduleId: string
  definition: string
  cron: string
  timezone: string
  note?: string | null
  nextRunAt?: string | null
  paused: boolean
}

export interface CreateSchedulePayload {
  definition: string
  cron: string
  timezone?: string
  input?: unknown
  note?: string
}
