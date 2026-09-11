import { defineEventHandler, getRouterParam, proxyRequest } from 'h3'
import { buildBackendHeaders } from '~/server/utils/backendHeaders'
import { useBackendConfig } from '~/server/utils/config'
import { attachAuth } from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const id = getRouterParam(event, 'id')
  const { baseUrl } = useBackendConfig()
  const target = `${baseUrl.replace(/\/$/, '')}/api/executions/${id}/events`

  const { headers } = buildBackendHeaders(event, { json: false })
  attachAuth(event, headers)

  return proxyRequest(event, target, {
    fetchOptions: { headers },
  })
})
