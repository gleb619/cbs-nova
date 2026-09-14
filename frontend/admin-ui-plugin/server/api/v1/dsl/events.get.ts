import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.type !== undefined) params.type = String(query.type)
  if (query.aggregateType !== undefined) params.aggregateType = String(query.aggregateType)
  if (query.aggregateId !== undefined) params.aggregateId = String(query.aggregateId)
  if (query.correlationId !== undefined) params.correlationId = String(query.correlationId)
  if (query.since !== undefined) params.since = String(query.since)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/dsl/events')
  }
  return proxyToBackend(event, '/api/dsl/events', { query: params })
})
