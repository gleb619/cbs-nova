import type { H3Event } from 'h3'
import { useAuthConfig } from './config'
import { clearOidcSession, discoverOidc, readSession } from './oidcSession'

/**
 * Shared logout body used by both the deprecated GET route and the new
 * CSRF-safe POST route. Best-effort provider end-session POST followed by
 * local cookie clearing.
 */
export async function performLogout(event: H3Event): Promise<void> {
  const config = useAuthConfig()
  const { refreshToken } = readSession(event)
  if (refreshToken) {
    try {
      const { end_session_endpoint } = await discoverOidc(config.issuer)
      if (end_session_endpoint) {
        await $fetch(end_session_endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            client_id: config.clientId,
            refresh_token: refreshToken,
          }).toString(),
          retry: false,
          timeout: 10000,
        })
      }
    } catch {
      // Best-effort logout: ignore provider-side failures and still clear cookies.
    }
  }
  clearOidcSession(event, config.callbackUrl)
}
