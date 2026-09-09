import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.definition !== undefined) params.definition = String(query.definition)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/dsl/diagnostics')
  }
  return proxyToBackend(event, '/api/dsl/diagnostics', { query: params })
})
