import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.definitionName !== undefined) params.definitionName = String(query.definitionName)
  if (query.status !== undefined) params.status = String(query.status)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/dsl/change-requests')
  }
  return proxyToBackend(event, '/api/dsl/change-requests', { query: params })
})
