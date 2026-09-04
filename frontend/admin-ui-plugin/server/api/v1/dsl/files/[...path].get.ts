import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const path = getRouterParam(event, 'path')
  const target = Array.isArray(path) ? path.join('/') : path
  return proxyToBackend(event, `/api/dsl/files/${target}`)
})
