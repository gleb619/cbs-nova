import { defineEventHandler, getRouterParam, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const body = await readBody(event)
  return proxyToBackend(event, `/api/dsl/notifications/rules/${id}`, { method: 'PUT', body })
})
