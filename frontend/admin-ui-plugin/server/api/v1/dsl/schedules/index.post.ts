import { defineEventHandler, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const body = await readBody(event)
  return proxyToBackend(event, '/api/dsl/schedules', { method: 'POST', body })
})
