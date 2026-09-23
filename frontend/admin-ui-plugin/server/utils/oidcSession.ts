import { deleteCookie, getCookie, type H3Event, setCookie } from 'h3'
import { useAuthConfig } from './config'

export type OidcMetadata = {
  authorization_endpoint: string
  token_endpoint: string
  end_session_endpoint?: string
  userinfo_endpoint?: string
}

export type TokenResponse = {
  access_token: string
  token_type?: string
  expires_in?: number
  refresh_token?: string
  id_token?: string
}

export type UserInfo = {
  sub: string
  preferred_username?: string
  email?: string
  name?: string
}

export type PkcePair = {
  verifier: string
  challenge: string
}

export type OidcTxn = {
  state: string
  verifier: string
  redirect: string
}

export type WriteSessionOptions = {
  /**
   * When true, this writeSession call is the result of a token refresh,
   * not a fresh login. The SESSION_START_COOKIE (used for absolute
   * timeout) is preserved, and the previous RT value is hashed into
   * RT_PREV_COOKIE to enable reuse detection on the next refresh.
   */
  isRefresh?: boolean
}

const discoveryCache = new Map<string, OidcMetadata>()

const BASE64URL = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'

function base64url(bytes: Uint8Array): string {
  let result = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b1 = bytes[i] ?? 0
    const b2 = bytes[i + 1] ?? 0
    const b3 = bytes[i + 2] ?? 0
    const n = (b1 << 16) | (b2 << 8) | b3
    result += BASE64URL[(n >> 18) & 63]
    result += BASE64URL[(n >> 12) & 63]
    if (i + 1 < bytes.length) result += BASE64URL[(n >> 6) & 63]
    if (i + 2 < bytes.length) result += BASE64URL[n & 63]
  }
  return result
}

export function sameOriginRedirect(raw: unknown, fallback = '/'): string {
  if (typeof raw !== 'string' || !raw.startsWith('/')) return fallback
  // Reject protocol-relative or fully-qualified URLs masquerading as paths.
  if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(raw)) return fallback
  return raw
}

export function __resetOidcDiscoveryCache(): void {
  discoveryCache.clear()
}

export async function discoverOidc(issuer: string): Promise<OidcMetadata> {
  const cached = discoveryCache.get(issuer)
  if (cached) return cached

  const config = await $fetch<OidcMetadata>(
    `${issuer.replace(/\/$/, '')}/.well-known/openid-configuration`,
    {
      retry: 1,
      timeout: 10000,
    },
  )

  const metadata: OidcMetadata = {
    authorization_endpoint: config.authorization_endpoint,
    token_endpoint: config.token_endpoint,
    end_session_endpoint: config.end_session_endpoint,
    userinfo_endpoint: config.userinfo_endpoint,
  }
  discoveryCache.set(issuer, metadata)
  return metadata
}

export async function createPkcePair(): Promise<PkcePair> {
  const verifierBytes = new Uint8Array(64)
  crypto.getRandomValues(verifierBytes)
  const verifier = base64url(verifierBytes)

  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  const challenge = base64url(new Uint8Array(digest))

  return { verifier, challenge }
}

export function randomState(): string {
  return crypto.randomUUID()
}

const TXN_COOKIE = 'cbs_oidc_txn'
const AT_COOKIE = 'cbs_at'
const RT_COOKIE = 'cbs_rt'
const RT_PREV_COOKIE = 'cbs_rt_prev'
const SESSION_START_COOKIE = 'cbs_sess_start'
const REFRESH_TOKEN_MAX_AGE_SECONDS = 30 * 24 * 60 * 60
// RT_PREV is a short-lived marker cookie holding a hash of the
// rotated-out refresh token. It catches the common "replay immediately
// after rotation" case — not a distributed/long-window attack (that
// needs a server-side token store and is intentionally out of scope
// for this change).
const RT_PREV_MAX_AGE_SECONDS = 120

function isSecureCallbackUrl(callbackUrl: string): boolean {
  try {
    return new URL(callbackUrl).protocol === 'https:'
  } catch {
    return false
  }
}

function cookieDefaults(callbackUrl: string) {
  const { sessionSecureCookies } = useAuthConfig()
  return {
    httpOnly: true,
    sameSite: 'lax' as const,
    // Force Secure on every auth cookie when explicitly enabled — useful
    // when the BFF is behind a TLS-terminating proxy that already stripped
    // https:// from the original callbackUrl. When not enabled we fall
    // back to the auto-detection of the callback URL protocol.
    secure: sessionSecureCookies || isSecureCallbackUrl(callbackUrl),
    path: '/',
  }
}

export function readOidcTxn(event: H3Event): OidcTxn | undefined {
  const raw = getCookie(event, TXN_COOKIE)
  if (!raw) return undefined
  try {
    return JSON.parse(raw) as OidcTxn
  } catch {
    return undefined
  }
}

export function writeOidcTxn(event: H3Event, txn: OidcTxn, callbackUrl: string) {
  setCookie(event, TXN_COOKIE, JSON.stringify(txn), {
    ...cookieDefaults(callbackUrl),
    maxAge: 600,
  })
}

export function clearOidcTxn(event: H3Event, callbackUrl: string) {
  deleteCookie(event, TXN_COOKIE, cookieDefaults(callbackUrl))
}

export type Session = {
  accessToken?: string
  refreshToken?: string
}

export function readSession(event: H3Event): Session {
  return {
    accessToken: getCookie(event, AT_COOKIE) ?? undefined,
    refreshToken: getCookie(event, RT_COOKIE) ?? undefined,
  }
}

export async function writeSession(
  event: H3Event,
  tokenResponse: TokenResponse,
  callbackUrl: string,
  opts?: WriteSessionOptions,
) {
  const config = useAuthConfig()
  const idle = config.sessionIdleTimeoutSeconds
  const absolute = config.sessionAbsoluteTimeoutSeconds
  const rotateOnRefresh = config.sessionRotateOnRefresh
  const defaults = cookieDefaults(callbackUrl)

  const requestedAtMaxAge = tokenResponse.expires_in ?? 3600
  // Idle-timeout cap: when configured, never let AT live longer than the
  // idle window. Floor at 1 s so we never accidentally set maxAge=0.
  const atMaxAge = idle > 0 ? Math.max(1, Math.min(requestedAtMaxAge, idle)) : requestedAtMaxAge

  setCookie(event, AT_COOKIE, tokenResponse.access_token, {
    ...defaults,
    maxAge: atMaxAge,
  })

  if (tokenResponse.refresh_token) {
    // On a successful refresh that hands back a new refresh_token, hash the
    // OLD RT into RT_PREV_COOKIE before overwriting. The next caller of
    // refreshTokens() will compare its incoming RT against RT_PREV_COOKIE
    // and treat a match as reuse.
    if (opts?.isRefresh && rotateOnRefresh) {
      const oldRt = getCookie(event, RT_COOKIE)
      if (oldRt) {
        const hash = await sha256b64url(oldRt)
        setCookie(event, RT_PREV_COOKIE, hash, {
          ...defaults,
          maxAge: RT_PREV_MAX_AGE_SECONDS,
        })
      }
    }
    setCookie(event, RT_COOKIE, tokenResponse.refresh_token, {
      ...defaults,
      maxAge: REFRESH_TOKEN_MAX_AGE_SECONDS,
    })
  }

  // Session-start marker for absolute-timeout enforcement. Set on every
  // fresh login — preserved across refreshes so the wall clock measures
  // total session age, not idle age.
  if (!opts?.isRefresh) {
    const existingStart = getCookie(event, SESSION_START_COOKIE)
    if (!existingStart) {
      const start = String(Math.floor(Date.now() / 1000))
      // Must outlive the RT so absolute-timeout still has a reference
      // point even after the RT cookie expires.
      const startMaxAge = Math.max(absolute, REFRESH_TOKEN_MAX_AGE_SECONDS)
      setCookie(event, SESSION_START_COOKIE, start, {
        ...defaults,
        maxAge: startMaxAge,
      })
    }
  }
}

export function sessionExpiredAbsolute(event: H3Event): boolean {
  const absolute = useAuthConfig().sessionAbsoluteTimeoutSeconds
  if (absolute <= 0) return false
  const startRaw = getCookie(event, SESSION_START_COOKIE)
  if (!startRaw) return false
  const start = Number(startRaw)
  if (!Number.isFinite(start)) return false
  const nowSec = Math.floor(Date.now() / 1000)
  return nowSec - start > absolute
}

export function clearOidcSession(event: H3Event, callbackUrl: string) {
  const defaults = cookieDefaults(callbackUrl)
  deleteCookie(event, AT_COOKIE, defaults)
  deleteCookie(event, RT_COOKIE, defaults)
  deleteCookie(event, SESSION_START_COOKIE, defaults)
  deleteCookie(event, RT_PREV_COOKIE, defaults)
}

async function tokenRequest(endpoint: string, body: URLSearchParams): Promise<TokenResponse> {
  const response = await $fetch.raw<TokenResponse>(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
    retry: false,
    timeout: 10000,
  })
  return response._data as TokenResponse
}

export async function exchangeCode(code: string, verifier: string): Promise<TokenResponse> {
  const { clientId, clientSecret, callbackUrl, issuer } = useAuthConfig()
  const { token_endpoint } = await discoverOidc(issuer)

  const body = new URLSearchParams()
  body.set('grant_type', 'authorization_code')
  body.set('code', code)
  body.set('redirect_uri', callbackUrl)
  body.set('client_id', clientId)
  if (clientSecret) body.set('client_secret', clientSecret)
  body.set('code_verifier', verifier)

  return tokenRequest(token_endpoint, body)
}

export async function refreshTokens(refreshToken: string): Promise<TokenResponse> {
  const { clientId, clientSecret, issuer } = useAuthConfig()
  const { token_endpoint } = await discoverOidc(issuer)

  const body = new URLSearchParams()
  body.set('grant_type', 'refresh_token')
  body.set('refresh_token', refreshToken)
  body.set('client_id', clientId)
  if (clientSecret) body.set('client_secret', clientSecret)

  return tokenRequest(token_endpoint, body)
}

export async function sha256b64url(input: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input))
  return base64url(new Uint8Array(digest))
}

export async function detectRefreshReuse(event: H3Event, refreshToken: string): Promise<boolean> {
  const prev = getCookie(event, RT_PREV_COOKIE)
  if (!prev) return false
  const hash = await sha256b64url(refreshToken)
  return hash === prev
}

export async function fetchUserInfo(accessToken: string): Promise<UserInfo> {
  const { issuer } = useAuthConfig()
  const { userinfo_endpoint } = await discoverOidc(issuer)
  if (!userinfo_endpoint) {
    throw new Error('OIDC discovery did not expose userinfo_endpoint')
  }
  return $fetch<UserInfo>(userinfo_endpoint, {
    headers: { Authorization: `Bearer ${accessToken}` },
    retry: false,
    timeout: 10000,
  })
}

export function attachAuth(event: H3Event, headers: Record<string, string>) {
  const config = useAuthConfig()
  if (!config.enabled) return

  const inboundAuthorization = event.node.req.headers.authorization
  if (inboundAuthorization) return

  const { accessToken } = readSession(event)
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }
}

export function expiringSoon(accessToken: string, bufferSeconds = 60): boolean {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) return true
    const decoded = JSON.parse(globalThis.atob(payload)) as { exp?: number }
    if (!decoded.exp) return false
    return decoded.exp - bufferSeconds < Math.floor(Date.now() / 1000)
  } catch {
    return false
  }
}

export {
  AT_COOKIE,
  REFRESH_TOKEN_MAX_AGE_SECONDS,
  RT_COOKIE,
  RT_PREV_COOKIE,
  RT_PREV_MAX_AGE_SECONDS,
  SESSION_START_COOKIE,
  TXN_COOKIE,
}
