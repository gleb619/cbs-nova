import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const params: Record<string, string> = {}
  if (query.offset !== undefined) params.offset = String(query.offset)
  if (query.limit !== undefined) params.limit = String(query.limit)
  if (query.subscriptionId !== undefined) params.subscriptionId = String(query.subscriptionId)

  if (Object.keys(params).length === 0) {
    return proxyToBackend(event, '/api/dsl/webhooks/deliveries')
  }
  return proxyToBackend(event, '/api/dsl/webhooks/deliveries', { query: params })
})
