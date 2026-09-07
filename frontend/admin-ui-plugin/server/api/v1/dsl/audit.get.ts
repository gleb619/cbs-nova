import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.action !== undefined) params.action = String(query.action)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/dsl/audit')
  }
  return proxyToBackend(event, '/api/dsl/audit', { query: params })
})
