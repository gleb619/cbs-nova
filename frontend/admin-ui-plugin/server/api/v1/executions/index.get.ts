import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  return proxyToBackend(event, '/api/executions')
})
