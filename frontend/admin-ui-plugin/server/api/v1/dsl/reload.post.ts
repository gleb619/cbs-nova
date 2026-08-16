
import { defineEventHandler, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  return proxyToBackend(event, '/api/dsl/reload', { method: 'POST' })
})
