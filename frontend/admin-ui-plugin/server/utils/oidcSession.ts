import { deleteCookie, getCookie, setCookie, type H3Event } from 'h3'
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
const REFRESH_TOKEN_MAX_AGE_SECONDS = 30 * 24 * 60 * 60

function isSecureCallbackUrl(callbackUrl: string): boolean {
  try {
    return new URL(callbackUrl).protocol === 'https:'
  } catch {
    return false
  }
}

function cookieDefaults(callbackUrl: string) {
  return {
    httpOnly: true,
    sameSite: 'lax' as const,
    secure: isSecureCallbackUrl(callbackUrl),
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

export function writeSession(event: H3Event, tokenResponse: TokenResponse, callbackUrl: string) {
  const atMaxAge = tokenResponse.expires_in ?? 3600
  setCookie(event, AT_COOKIE, tokenResponse.access_token, {
    ...cookieDefaults(callbackUrl),
    maxAge: atMaxAge,
  })
  if (tokenResponse.refresh_token) {
    setCookie(event, RT_COOKIE, tokenResponse.refresh_token, {
      ...cookieDefaults(callbackUrl),
      maxAge: REFRESH_TOKEN_MAX_AGE_SECONDS,
    })
  }
}

export function clearSession(event: H3Event, callbackUrl: string) {
  const defaults = cookieDefaults(callbackUrl)
  deleteCookie(event, AT_COOKIE, defaults)
  deleteCookie(event, RT_COOKIE, defaults)
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

export { TXN_COOKIE, AT_COOKIE, RT_COOKIE }
