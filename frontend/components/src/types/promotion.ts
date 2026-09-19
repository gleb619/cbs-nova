export type PromotionOutcome = 'created' | 'updated' | 'unchanged' | 'skipped' | 'published' | 'failed'

export interface PromotionEnvironment {
  name: string
}

export interface PromotionDefinition {
  name: string
  type: string
  status: string
}

export interface PromotionRequest {
  source: string
  target: string
  definitions?: string[]
  includeDrafts?: boolean
}

export interface PromoteEntryResult {
  name: string
  outcome: PromotionOutcome
  message?: string
}

export interface PromoteResult {
  dryRun: boolean
  reloaded: boolean
  published: number
  failed: number
  results: PromoteEntryResult[]
  reloadError?: string
}
