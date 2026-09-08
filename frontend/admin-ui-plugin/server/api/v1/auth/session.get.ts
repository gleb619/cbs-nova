import { createError, defineEventHandler } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import {
  clearOidcSession,
  detectRefreshReuse,
  fetchUserInfo,
  readSession,
  refreshTokens,
  sessionExpiredAbsolute,
  writeSession,
} from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    return { authenticated: false, enabled: false }
  }

  // Absolute-timeout enforcement: if the wall-clock session age exceeds
  // the configured limit, treat the session as logged out.
  if (sessionExpiredAbsolute(event)) {
    clearOidcSession(event, config.callbackUrl)
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  const { accessToken, refreshToken } = readSession(event)

  if (!accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  async function loadUser(token: string) {
    return fetchUserInfo(token)
  }

  try {
    const user = await loadUser(accessToken)
    return { authenticated: true, user }
  } catch (err) {
    const status = (err as { response?: { status?: number } }).response?.status
    if ((status === 401 || status === 403) && refreshToken) {
      // Refresh-token reuse detection: replay of a previously rotated-out
      // token inside the marker window means stolen credentials. Clear
      // and 401.
      if (config.sessionRotateOnRefresh && (await detectRefreshReuse(event, refreshToken))) {
        clearOidcSession(event, config.callbackUrl)
        console.warn('[auth] refresh-token reuse detected — session cleared')
        throw createError({
          statusCode: 401,
          statusMessage: 'session revoked',
        })
      }
      try {
        const refreshed = await refreshTokens(refreshToken)
        await writeSession(event, refreshed, config.callbackUrl, { isRefresh: true })
        const user = await loadUser(refreshed.access_token)
        return { authenticated: true, user }
      } catch {
        // Fall through to 401.
      }
    }
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }
})
