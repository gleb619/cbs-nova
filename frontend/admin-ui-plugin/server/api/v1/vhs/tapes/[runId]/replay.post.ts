import { defineEventHandler, getRouterParam, readBody } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const runId = getRouterParam(event, 'runId')
  const body = await readBody(event)
  return proxyToBackend(event, `/api/v1/vhs/tapes/${runId}/replay`, {
    method: 'POST',
    body,
  })
})
