import { defineEventHandler } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) =>
  proxyToBackend(event, '/api/dsl/drafts'),
)