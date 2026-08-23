import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.status !== undefined) params.status = String(query.status)
  if (query.mode !== undefined) params.mode = String(query.mode)
  if (query.entityName !== undefined) params.processName = String(query.entityName)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/executions')
  }
  return proxyToBackend(event, '/api/executions', { query: params })
})
