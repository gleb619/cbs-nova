/**
 * Wire shape of the dashboard stats endpoint
 * (BFF `GET /api/v1/executions/stats` → backend `GET /api/executions/stats`).
 *
 * `statusCounts` keys use the same display casing as execution list items
 * (e.g. `Running`, `Completed`); statuses with zero runs may be absent.
 */
export interface DashboardStats {
  totalRuns: number
  statusCounts: Record<string, number>
  windowRuns: number
  windowFailedRuns: number
  windowFailureRate: number
  windowHours: number
  topProcesses: DashboardProcessRunCount[]
}

export interface DashboardProcessRunCount {
  processName: string
  runCount: number
}
