
import { defineEventHandler, getRouterParam, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const body = await readBody(event)
  return proxyToBackend(event, `/api/dsl/preview/${name}`, { method: 'POST', body })
})
