import { defineEventHandler, getQuery, getRequestHeader, setResponseHeader, setResponseStatus } from 'h3'
import { $fetch } from 'ofetch'
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

  const { baseUrl, apiKey, timeoutMs } = useBackendConfig()
  const url = `${baseUrl.replace(/\/$/, '')}/api/executions/export.csv`
  const headers: Record<string, string> = {}
  if (apiKey) headers['X-Api-Key'] = apiKey

  const inboundRequestId = getRequestHeader(event, 'x-request-id')
  const requestId = inboundRequestId || globalThis.crypto.randomUUID()
  headers['X-Request-Id'] = requestId

  const traceparent = getRequestHeader(event, 'traceparent')
  if (traceparent) headers.traceparent = traceparent

  const authorization = getRequestHeader(event, 'authorization')
  if (authorization) headers.Authorization = authorization

  const idempotencyKey = getRequestHeader(event, 'idempotency-key')
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey

  const correlationId = getRequestHeader(event, 'x-correlation-id')
  if (correlationId) headers['X-Correlation-Id'] = correlationId

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
