import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const runId = getRouterParam(event, 'runId')
  return proxyToBackend(event, `/api/v1/vhs/tapes/${runId}`, { raw: true })
})
