import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

/**
 * GET /api/v1/executions/export → backend GET /api/executions/export.csv.
 *
 * Raw CSV passthrough via proxyToBackend. The upstream Content-Type,
 * Content-Disposition and X-Export-Truncated headers are forwarded and the
 * body is returned as plain text so the browser triggers a download.
 */
export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.status !== undefined) params.status = String(query.status)
  if (query.mode !== undefined) params.mode = String(query.mode)
  if (query.entityName !== undefined) params.processName = String(query.entityName)
  if (query.processName !== undefined) params.processName = String(query.processName)
  if (query.correlationId !== undefined) params.correlationId = String(query.correlationId)

  return proxyToBackend<string>(event, '/api/executions/export.csv', {
    query: params,
    raw: true,
    forwardResponseHeaders: ['content-type', 'content-disposition', 'x-export-truncated'],
  })
})
