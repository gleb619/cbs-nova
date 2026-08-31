import { defineEventHandler, getQuery } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) =>
  proxyToBackend(event, '/api/dsl/definitions/export', { query: getQuery(event) }),
)
