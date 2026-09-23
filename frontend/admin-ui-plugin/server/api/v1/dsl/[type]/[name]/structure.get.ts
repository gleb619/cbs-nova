import { createError, defineEventHandler, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

const ALLOWED_TYPES = new Set(['processes', 'transactions', 'functions', 'helpers'])

export default defineEventHandler(async (event) => {
  const type = getRouterParam(event, 'type')
  const name = getRouterParam(event, 'name')
  if (!type || !ALLOWED_TYPES.has(type)) {
    throw createError({ statusCode: 404, statusMessage: 'Unknown construct type' })
  }
  return proxyToBackend(event, `/api/dsl/${type}/${name}/structure`)
})
