import { defineEventHandler } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

/**
 * GET /api/v1/executions/stats → backend GET /api/executions/stats.
 *
 * Aggregate run statistics (totals, per-status counts, trailing-24h failure
 * rate, top processes) for the dashboard. Query params (e.g. topProcesses)
 * pass through untouched via getQuery.
 */
export default defineEventHandler(async (event) => {
  return proxyToBackend(event, '/api/executions/stats')
})
