import { createError, defineEventHandler, sendRedirect } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import { clearOidcSession, discoverOidc, readSession } from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }

  const { refreshToken } = readSession(event)
  if (refreshToken) {
    try {
      const { end_session_endpoint } = await discoverOidc(config.issuer)
      if (end_session_endpoint) {
        await $fetch(end_session_endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({ client_id: config.clientId, refresh_token: refreshToken }).toString(),
          retry: false,
          timeout: 10000,
        })
      }
    } catch {
      // Best-effort logout: ignore provider-side failures and still clear cookies.
    }
  }

  clearOidcSession(event, config.callbackUrl)
  return sendRedirect(event, config.postLogoutRedirect, 302)
})
