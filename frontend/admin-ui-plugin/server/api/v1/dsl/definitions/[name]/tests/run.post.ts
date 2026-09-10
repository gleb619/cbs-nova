import { defineEventHandler, getQuery, getRouterParam } from 'h3'
import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const name = getRouterParam(event, 'name')
  const query = getQuery(event)
  const caseFilter = Array.isArray(query.case) ? query.case : query.case ? [query.case] : undefined
  const filteredQuery = caseFilter ? { case: caseFilter } : {}
  return proxyToBackend(event, `/api/dsl/definitions/${name}/tests/run`, {
    method: 'POST',
    query: filteredQuery,
  })
})