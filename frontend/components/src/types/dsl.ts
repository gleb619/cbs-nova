export type ConstructType = 'Process' | 'Transaction' | 'Function' | 'Helper'
export type ConstructStatus = 'Draft' | 'Valid' | 'Invalid' | 'Published'

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
