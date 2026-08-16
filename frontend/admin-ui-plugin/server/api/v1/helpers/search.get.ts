import { proxyToBackend } from '~/server/utils/httpClient'

export default defineEventHandler(async (event) => {
  const { name, type, description } = getQuery(event)

  const query: Record<string, string> = {}
  if (name && typeof name === 'string' && name.trim()) query.name = name.trim()
  if (type && typeof type === 'string' && type.trim()) query.type = type.trim()
  if (description && typeof description === 'string' && description.trim())
    query.description = description.trim()

  return proxyToBackend(event, '/api/dsl/helpers/search', { query })
})
