import { createError, defineEventHandler, getHeader } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import { performLogout } from '~/server/utils/logout'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }

  // Same-site custom-header CSRF guard. A cross-site form POST cannot set
  // arbitrary headers (CORS preflight would block it), so the presence
  // of X-Requested-With: XMLHttpRequest here proves the request came
  // from same-origin JS.
  const requestedWith = getHeader(event, 'x-requested-with')
  if (requestedWith !== 'XMLHttpRequest') {
    throw createError({
      statusCode: 403,
      statusMessage: 'missing X-Requested-With',
    })
  }

  await performLogout(event)
  return { redirect: config.postLogoutRedirect }
})
