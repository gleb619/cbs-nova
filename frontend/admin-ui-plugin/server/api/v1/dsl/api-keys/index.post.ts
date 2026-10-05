import { defineEventHandler, readBody, setResponseHeader } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  // The create response carries the plaintext API key, returned exactly once.
  // Mark the response non-cacheable and never log the body.
  setResponseHeader(event, 'cache-control', 'no-store')
  const body = await readBody(event)
  return proxyToBackend(event, '/api/dsl/auth/keys', { method: 'POST', body })
})
