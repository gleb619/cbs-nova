import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const definition = getRouterParam(event, 'definition')
  return proxyToBackend(event, `/api/dsl/schedules/${definition}`, { method: 'DELETE' })
})
