import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.env !== undefined) params.env = String(query.env)
  if (Object.keys(params).length === 0) return proxyToBackend(event, '/api/dsl/promote/definitions')
  return proxyToBackend(event, '/api/dsl/promote/definitions', { query: params })
})
