import { defineEventHandler, getRouterParam, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const path = getRouterParam(event, 'path')
  const target = Array.isArray(path) ? path.join('/') : path
  const body = await readBody(event)
  return proxyToBackend(event, `/api/dsl/files/${target}`, { method: 'POST', body })
})
