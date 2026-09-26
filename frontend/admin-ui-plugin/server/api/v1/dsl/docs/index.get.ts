import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const prefix = typeof query.prefix === 'string' ? query.prefix : undefined
  return proxyToBackend(event, '/api/dsl/docs', prefix ? { query: { prefix } } : undefined)
})
