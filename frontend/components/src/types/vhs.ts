export interface VhsTapeFilters {
  route?: string
  since?: string
}

export interface VhsTapeEventSummary {
  index: number
  eventType: string
  relativeMs: number
  callId?: string
  callType?: string
  target?: string
  operation?: string
  durationMs?: number | null
  hasInput: boolean
  input?: unknown
  output?: unknown
  hasOutput: boolean
}

export interface VhsTapeDetail {
  runId: string
  header: Record<string, unknown>
  events: VhsTapeEventSummary[]
  raw: string
}

export interface VhsLoadTestForm {
  tapes?: string
  target?: string
  speed?: number
  concurrency?: number
  duration?: number
}

export interface VhsLoadTestTapeSummary {
  tapeName: string
  totalCalls: number
  successfulCalls: number
  failedCalls: number
  p50Ms: number
  p95Ms: number
  p99Ms: number
  errorRatePct: number
}

export interface VhsLoadTestReport {
  target: string
  tapesLoaded: number
  totalCalls: number
  successfulCalls: number
  failedCalls: number
  wallClockMs: number
  tapeSummaries: VhsLoadTestTapeSummary[]
  rawReports: unknown[]
}
