import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.route !== undefined) params.route = String(query.route)
  if (query.since !== undefined) params.since = String(query.since)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/v1/vhs/tapes')
  }
  return proxyToBackend(event, '/api/v1/vhs/tapes', { query: params })
})
