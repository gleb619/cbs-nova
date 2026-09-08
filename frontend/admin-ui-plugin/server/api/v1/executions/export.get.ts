import { defineEventHandler, getQuery, setResponseHeader, setResponseStatus } from 'h3'
import { $fetch } from 'ofetch'
import { buildBackendHeaders } from '~/server/utils/backendHeaders'
import { useBackendConfig } from '~/server/utils/config'
import { attachAuth } from '~/server/utils/oidcSession'

/**
 * GET /api/v1/executions/export → backend GET /api/executions/export.csv.
 *
 * Raw CSV passthrough. Forwards the upstream Content-Type, Content-Disposition
 * and X-Export-Truncated headers and returns the body as plain text so the
 * browser triggers a download.
 */
export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.status !== undefined) params.status = String(query.status)
  if (query.mode !== undefined) params.mode = String(query.mode)
  if (query.entityName !== undefined) params.processName = String(query.entityName)
  if (query.processName !== undefined) params.processName = String(query.processName)
  if (query.correlationId !== undefined) params.correlationId = String(query.correlationId)

  const { baseUrl, timeoutMs } = useBackendConfig()
  const url = `${baseUrl.replace(/\/$/, '')}/api/executions/export.csv`
  const { headers } = buildBackendHeaders(event, { json: false })

  attachAuth(event, headers)

  const response = await $fetch.raw<string>(url, {
    method: 'GET',
    headers,
    query: params,
    responseType: 'text',
    timeout: timeoutMs,
    retry: false,
  })

  setResponseStatus(event, response.status)
  const contentType = response.headers.get('content-type')
  if (contentType) setResponseHeader(event, 'Content-Type', contentType)
  const disposition = response.headers.get('content-disposition')
  if (disposition) setResponseHeader(event, 'Content-Disposition', disposition)
  const truncated = response.headers.get('x-export-truncated')
  if (truncated) setResponseHeader(event, 'X-Export-Truncated', truncated)

  return response._data
})
