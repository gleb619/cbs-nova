import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const { page, size, query, mode, type } = getQuery(event)

  const backendQuery: Record<string, string> = {}
  if (page && typeof page === 'string' && page.trim()) backendQuery.page = page.trim()
  if (size && typeof size === 'string' && size.trim()) backendQuery.size = size.trim()
  if (query && typeof query === 'string' && query.trim()) backendQuery.query = query.trim()
  if (mode && typeof mode === 'string' && mode.trim()) backendQuery.mode = mode.trim()
  if (type && typeof type === 'string' && type.trim()) backendQuery.type = type.trim()

  return proxyToBackend(event, '/api/dsl/objects/search', { query: backendQuery })
})
