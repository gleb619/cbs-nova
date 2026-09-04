import { defineEventHandler } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  return proxyToBackend(event, '/api/dsl/files/status')
})
