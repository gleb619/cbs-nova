import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const mode = getQuery(event).mode
  const suffix = mode ? `?mode=${encodeURIComponent(String(mode))}` : ''
  return proxyToBackend(event, `/api/dsl/schemas/${name}${suffix}`)
})
