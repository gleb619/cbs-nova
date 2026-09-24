import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const query = getQuery(event)
  const limit = query.limit ? `?limit=${query.limit}` : ''
  return proxyToBackend(event, `/api/dsl/drafts/${name}/commits${limit}`)
})
