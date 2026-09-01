import { defineEventHandler } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

/**
 * GET /api/v1/executions/stats/timeseries → backend GET /api/executions/stats/timeseries.
 *
 * Per-bucket run counts grouped by status for the dashboard trend chart.
 * Query params (windowHours, bucketMinutes) pass through untouched via getQuery.
 */
export default defineEventHandler(async (event) => {
  return proxyToBackend(event, '/api/executions/stats/timeseries')
})
