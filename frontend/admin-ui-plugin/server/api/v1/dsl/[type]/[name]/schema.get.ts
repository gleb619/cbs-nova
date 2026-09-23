import { createError, defineEventHandler, getQuery, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

const ALLOWED_TYPES = new Set(['processes', 'transactions', 'functions', 'helpers'])

export default defineEventHandler(async (event) => {
  const type = getRouterParam(event, 'type')
  const name = getRouterParam(event, 'name')
  if (!type || !ALLOWED_TYPES.has(type)) {
    throw createError({ statusCode: 404, statusMessage: 'Unknown construct type' })
  }
  const mode = getQuery(event).mode
  const suffix = mode ? `?mode=${encodeURIComponent(String(mode))}` : ''
  return proxyToBackend(event, `/api/dsl/${type}/${name}/schema${suffix}`)
})
