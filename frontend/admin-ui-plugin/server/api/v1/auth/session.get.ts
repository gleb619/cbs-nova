import { createError, defineEventHandler } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import { fetchUserInfo, readSession, refreshTokens, writeSession } from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    return { authenticated: false, enabled: false }
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
      try {
        const refreshed = await refreshTokens(refreshToken)
        writeSession(event, refreshed, config.callbackUrl)
        const user = await loadUser(refreshed.access_token)
        return { authenticated: true, user }
      } catch {
        // Fall through to 401.
      }
    }
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }
})
