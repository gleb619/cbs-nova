import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const timestamp = getRouterParam(event, 'timestamp')
  return proxyToBackend(event, `/api/dsl/drafts/${name}/history/${timestamp}/restore`, {
    method: 'POST',
  })
})
