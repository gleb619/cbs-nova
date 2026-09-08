import { createError, defineEventHandler, sendRedirect } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import { performLogout } from '~/server/utils/logout'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }

  console.warn('[auth] GET /api/v1/auth/logout is deprecated — use POST')

  await performLogout(event)
  return sendRedirect(event, config.postLogoutRedirect, 302)
})
