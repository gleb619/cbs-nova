// Nitro route param is named `id`, not `runId`, to match the sibling
// `executions/[id].get.ts`. Nitro's router keys one dynamic param per path
// segment, so mixing `[id]` and `[runId]` at this level would rename the
// param for BOTH routes and break `executions/[id].get.ts`.
import { defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'
export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  return proxyToBackend(event, `/api/executions/${id}/cancel`, { method: 'POST' })
})
