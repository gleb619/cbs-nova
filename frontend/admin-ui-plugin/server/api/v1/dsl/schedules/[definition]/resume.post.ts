import { defineEventHandler, getRouterParam, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const definition = getRouterParam(event, 'definition')
  const body = await readBody(event)
  return proxyToBackend(event, `/api/dsl/schedules/${definition}/resume`, { method: 'POST', body })
})
