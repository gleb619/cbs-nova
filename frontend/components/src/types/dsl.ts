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
}

export interface ValidationError {
  field: string
  message: string
  severity: 'error' | 'warning'
}

export interface StepDef {
  id: string
  type: 'helper' | 'function' | 'transaction' | 'step'
  name: string
  inputMapping?: string
}

export interface HelperCatalogEntry {
  name: string
  description?: string
  inputType?: string
  outputType?: string
  hasSideEffects: boolean
  previewBehavior?: string
}

export interface HelpersResponse {
  names: string[]
  helpers: HelperCatalogEntry[]
}

export interface CompileDiagnostic {
  file: string
  line?: number | null
  column?: number | null
  message: string
  severity: string
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
