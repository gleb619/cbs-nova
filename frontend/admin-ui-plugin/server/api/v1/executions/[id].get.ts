
import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  return proxyToBackend(event, `/api/executions/${id}`)
})
