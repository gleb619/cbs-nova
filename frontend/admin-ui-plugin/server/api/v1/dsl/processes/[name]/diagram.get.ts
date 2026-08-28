import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const { format } = getQuery(event)

  if (!format || typeof format !== 'string' || !format.trim()) {
    return proxyToBackend(event, `/api/dsl/processes/${name}/diagram`)
  }
  return proxyToBackend(event, `/api/dsl/processes/${name}/diagram`, {
    query: { format: format.trim() },
  })
})
