import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest'
import {
  __resetOidcDiscoveryCache,
  AT_COOKIE,
  clearOidcSession,
  clearOidcTxn,
  createPkcePair,
  detectRefreshReuse,
  discoverOidc,
  exchangeCode,
  expiringSoon,
  fetchUserInfo,
  RT_COOKIE,
  RT_PREV_COOKIE,
  randomState,
  readOidcTxn,
  readSession,
  refreshTokens,
  SESSION_START_COOKIE,
  sessionExpiredAbsolute,
  sha256b64url,
  TXN_COOKIE,
  writeOidcTxn,
  writeSession,
} from '../oidcSession'

const callbackUrl = 'http://localhost:3000/api/v1/auth/callback'

function makeEvent(headers: Record<string, string> = {}) {
  const responseHeaders: Record<string, string | string[] | undefined> = {}
  return {
    node: {
      req: { headers },
      res: {
        getHeader: (name: string) => responseHeaders[name.toLowerCase()],
        setHeader: (name: string, value: string | string[]) => {
          responseHeaders[name.toLowerCase()] = value
        },
        appendHeader: (name: string, value: string) => {
          const key = name.toLowerCase()
          const current = responseHeaders[key]
          responseHeaders[key] = current
            ? [...(Array.isArray(current) ? current : [current]), value]
            : value
        },
        removeHeader: (name: string) => {
          delete responseHeaders[name.toLowerCase()]
        },
      },
    },
  } as Parameters<typeof readSession>[0]
}

function getSetCookie(event: ReturnType<typeof makeEvent>): string | string[] | undefined {
  return event.node.res.getHeader('set-cookie') as string | string[] | undefined
}

function firstSetCookie(event: ReturnType<typeof makeEvent>, name: string): string | undefined {
  const raw = getSetCookie(event)
  const cookies = Array.isArray(raw) ? raw : raw ? [raw] : []
  return cookies.find((c) => c.startsWith(`${name}=`))
}

describe('oidcSession', () => {
  beforeEach(() => {
    __resetOidcDiscoveryCache()
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      authIssuer: 'http://keycloak:8080/realms/cbs-nova',
      authClientId: 'cbs-nova-bff',
      authClientSecret: 'change_me_in_production',
      authCallbackUrl: callbackUrl,
      authPostLogoutRedirect: '/',
      public: { appName: 'CBS Nova Admin', authEnabled: true },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockClear()
    ;($fetch.raw as unknown as ReturnType<typeof vi.fn>).mockClear()
  })

  describe('createPkcePair', () => {
    it('returns verifier and base64url S256 challenge', async () => {
      const pair = await createPkcePair()
      expect(pair.verifier).toMatch(/^[A-Za-z0-9_-]+$/)
      expect(pair.verifier.length).toBeGreaterThanOrEqual(32)
      expect(pair.challenge).toMatch(/^[A-Za-z0-9_-]+$/)
      expect(pair.challenge).not.toContain('=')
      expect(pair.challenge).not.toContain('+')
      expect(pair.challenge).not.toContain('/')
      expect(pair.challenge.length).toBe(43)
    })

    it('produces unique verifiers and challenges', async () => {
      const a = await createPkcePair()
      const b = await createPkcePair()
      expect(a.verifier).not.toBe(b.verifier)
      expect(a.challenge).not.toBe(b.challenge)
    })
  })

  describe('randomState', () => {
    it('returns unique values', () => {
      const a = randomState()
      const b = randomState()
      expect(a).not.toBe(b)
      expect(a.length).toBeGreaterThan(0)
    })
  })

  describe('discoverOidc', () => {
    it('fetches discovery and returns endpoints', async () => {
      const metadata = {
        authorization_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/auth',
        token_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/token',
        end_session_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/logout',
        userinfo_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/userinfo',
      }
      ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(metadata)

      const result = await discoverOidc('http://keycloak:8080/realms/cbs-nova')

      expect(result).toEqual(metadata)
      expect($fetch).toHaveBeenCalledTimes(1)
      expect($fetch).toHaveBeenCalledWith(
        'http://keycloak:8080/realms/cbs-nova/.well-known/openid-configuration',
        expect.objectContaining({ retry: 1, timeout: 10000 }),
      )
    })

    it('caches discovery so fetch is only called once per issuer', async () => {
      const metadata = {
        authorization_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/auth',
        token_endpoint: 'http://keycloak:8080/realms/cbs-nova/protocol/openid-connect/token',
      }
      ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce(metadata)

      const first = await discoverOidc('http://keycloak:8080/realms/cbs-nova')
      const second = await discoverOidc('http://keycloak:8080/realms/cbs-nova')

      expect(first).toBe(second)
      expect($fetch).toHaveBeenCalledTimes(1)
    })
  })

  describe('cookie helpers', () => {
    it('writes and reads the OIDC txn cookie', () => {
      const event = makeEvent()
      const txn = { state: 'state-abc', verifier: 'verifier-xyz', redirect: '/runner' }
      writeOidcTxn(event, txn, callbackUrl)

      const cookie = firstSetCookie(event, TXN_COOKIE)
      expect(cookie).toBeDefined()
      expect(cookie).toContain(`${TXN_COOKIE}=`)
      expect(cookie).toContain('HttpOnly')
      expect(cookie).toContain('SameSite=Lax')
      expect(cookie).not.toContain('Secure')

      // Simulate the browser sending the cookie back on the next request.
      const readEvent = makeEvent({ cookie: cookie as string })
      expect(readOidcTxn(readEvent)).toEqual(txn)
    })

    it('clears the txn cookie', () => {
      const event = makeEvent()
      writeOidcTxn(event, { state: 's', verifier: 'v', redirect: '/' }, callbackUrl)
      clearOidcTxn(event, callbackUrl)
      const cookie = firstSetCookie(event, TXN_COOKIE)
      expect(cookie).toMatch(new RegExp(`${TXN_COOKIE}=;.*Max-Age=0`))
    })

    it('writes session cookies with correct names and flags', () => {
      const event = makeEvent()
      writeSession(
        event,
        { access_token: 'at-xyz', refresh_token: 'rt-xyz', expires_in: 1800 },
        callbackUrl,
      )

      const atCookie = firstSetCookie(event, AT_COOKIE)
      const rtCookie = firstSetCookie(event, RT_COOKIE)
      expect(atCookie).toContain(`${AT_COOKIE}=at-xyz`)
      expect(rtCookie).toContain(`${RT_COOKIE}=rt-xyz`)
      expect(atCookie).toContain('HttpOnly')
      expect(atCookie).toContain('SameSite=Lax')

      const readEvent = makeEvent({ cookie: [atCookie, rtCookie].join('; ') as string })
      expect(readSession(readEvent)).toEqual({ accessToken: 'at-xyz', refreshToken: 'rt-xyz' })
    })

    it('uses secure flag for https callback URL', () => {
      const httpsCallback = 'https://example.com/api/v1/auth/callback'
      const event = makeEvent()
      writeSession(event, { access_token: 'at' }, httpsCallback)
      const cookie = firstSetCookie(event, AT_COOKIE)
      expect(cookie).toContain('Secure')
    })

    it('clears session cookies', () => {
      const event = makeEvent()
      writeSession(event, { access_token: 'at', refresh_token: 'rt' }, callbackUrl)
      clearOidcSession(event, callbackUrl)
      const atCookie = firstSetCookie(event, AT_COOKIE)
      const rtCookie = firstSetCookie(event, RT_COOKIE)
      expect(atCookie).toMatch(new RegExp(`${AT_COOKIE}=;.*Max-Age=0`))
      expect(rtCookie).toMatch(new RegExp(`${RT_COOKIE}=;.*Max-Age=0`))
    })

    it('forces secure flag when sessionSecureCookies=true even on http callback', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authIssuer: 'http://keycloak:8080/realms/cbs-nova',
        authCallbackUrl: callbackUrl,
        authSessionSecureCookies: 'true',
        authSessionRotateOnRefresh: 'true',
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const event = makeEvent()
      writeSession(event, { access_token: 'at' }, callbackUrl)
      const cookie = firstSetCookie(event, AT_COOKIE)
      expect(cookie).toContain('Secure')
    })

    it('caps AT maxAge at the configured idle timeout', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authIssuer: 'http://keycloak:8080/realms/cbs-nova',
        authCallbackUrl: callbackUrl,
        authSessionIdleTimeoutSeconds: 300,
        authSessionRotateOnRefresh: 'true',
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const event = makeEvent()
      writeSession(event, { access_token: 'at', expires_in: 3600 }, callbackUrl)
      const cookie = firstSetCookie(event, AT_COOKIE)
      expect(cookie).toMatch(/Max-Age=300/)
    })

    it('uses full expires_in when idle timeout is zero', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authIssuer: 'http://keycloak:8080/realms/cbs-nova',
        authCallbackUrl: callbackUrl,
        authSessionIdleTimeoutSeconds: 0,
        authSessionRotateOnRefresh: 'true',
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const event = makeEvent()
      writeSession(event, { access_token: 'at', expires_in: 3600 }, callbackUrl)
      const cookie = firstSetCookie(event, AT_COOKIE)
      expect(cookie).toMatch(/Max-Age=3600/)
    })

    it('sets cbs_sess_start on fresh login', () => {
      const event = makeEvent()
      writeSession(event, { access_token: 'at' }, callbackUrl)
      const cookie = firstSetCookie(event, SESSION_START_COOKIE)
      expect(cookie).toBeDefined()
      expect(cookie).toContain(`${SESSION_START_COOKIE}=`)
      const value = cookie?.split(';')[0].split('=')[1]
      expect(Number(value)).toBeGreaterThan(0)
    })

    it('does not reset cbs_sess_start on refresh', () => {
      const originalStart = String(Math.floor(Date.now() / 1000) - 60)
      const event = makeEvent({ cookie: `${SESSION_START_COOKIE}=${originalStart}` })
      writeSession(event, { access_token: 'at' }, callbackUrl, { isRefresh: true })
      const cookie = firstSetCookie(event, SESSION_START_COOKIE)
      if (cookie) {
        expect(cookie).toMatch(new RegExp(`${SESSION_START_COOKIE}=${originalStart}`))
      }
    })

    it('writes cbs_rt_prev when a refresh rotates the refresh token', async () => {
      const oldRt = 'old-refresh-token'
      const event = makeEvent({ cookie: `${RT_COOKIE}=${oldRt}` })
      await writeSession(
        event,
        { access_token: 'at', refresh_token: 'new-refresh-token' },
        callbackUrl,
        { isRefresh: true },
      )
      await new Promise((r) => setImmediate(r))
      const cookie = firstSetCookie(event, RT_PREV_COOKIE)
      expect(cookie).toBeDefined()
      const expectedHash = await sha256b64url(oldRt)
      expect(cookie).toContain(`${RT_PREV_COOKIE}=${expectedHash}`)
      expect(cookie).toMatch(/Max-Age=120/)
    })

    it('clearOidcSession also removes cbs_sess_start and cbs_rt_prev', () => {
      const event = makeEvent()
      writeSession(event, { access_token: 'at', refresh_token: 'rt' }, callbackUrl)
      clearOidcSession(event, callbackUrl)
      const start = firstSetCookie(event, SESSION_START_COOKIE)
      const prev = firstSetCookie(event, RT_PREV_COOKIE)
      expect(start).toMatch(new RegExp(`${SESSION_START_COOKIE}=;.*Max-Age=0`))
      expect(prev).toMatch(new RegExp(`${RT_PREV_COOKIE}=;.*Max-Age=0`))
    })
  })

  describe('sessionExpiredAbsolute', () => {
    function makeAbsoluteEvent(startSec: number | undefined) {
      const cookies: Record<string, string> = {}
      if (startSec !== undefined) cookies[SESSION_START_COOKIE] = String(startSec)
      return makeEvent({
        cookie: Object.entries(cookies)
          .map(([k, v]) => `${k}=${v}`)
          .join('; '),
      })
    }

    it('returns false when cbs_sess_start is absent', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authSessionAbsoluteTimeoutSeconds: 60,
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      expect(sessionExpiredAbsolute(makeAbsoluteEvent(undefined))).toBe(false)
    })

    it('returns false when absolute timeout is 0 (disabled)', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authSessionAbsoluteTimeoutSeconds: 0,
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const longAgo = Math.floor(Date.now() / 1000) - 99999
      expect(sessionExpiredAbsolute(makeAbsoluteEvent(longAgo))).toBe(false)
    })

    it('returns true when the session start is older than the absolute timeout', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authSessionAbsoluteTimeoutSeconds: 60,
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const longAgo = Math.floor(Date.now() / 1000) - 120
      expect(sessionExpiredAbsolute(makeAbsoluteEvent(longAgo))).toBe(true)
    })

    it('returns false when within the absolute window', () => {
      vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
        authSessionAbsoluteTimeoutSeconds: 3600,
      } as unknown as ReturnType<typeof useRuntimeConfig>)
      const recent = Math.floor(Date.now() / 1000) - 30
      expect(sessionExpiredAbsolute(makeAbsoluteEvent(recent))).toBe(false)
    })
  })

  describe('detectRefreshReuse', () => {
    it('returns false when cbs_rt_prev is absent', async () => {
      const event = makeEvent()
      expect(await detectRefreshReuse(event, 'rt')).toBe(false)
    })

    it('returns true when the RT matches the stored hash', async () => {
      const rt = 'rotated-out-token'
      const hash = await sha256b64url(rt)
      const event = makeEvent({ cookie: `${RT_PREV_COOKIE}=${hash}` })
      expect(await detectRefreshReuse(event, rt)).toBe(true)
    })

    it('returns false for a non-matching RT', async () => {
      const hash = await sha256b64url('original')
      const event = makeEvent({ cookie: `${RT_PREV_COOKIE}=${hash}` })
      expect(await detectRefreshReuse(event, 'different')).toBe(false)
    })
  })

  describe('exchangeCode', () => {
    it('posts to token endpoint with authorization_code grant and PKCE verifier', async () => {
      const tokenResponse = {
        access_token: 'new-access',
        refresh_token: 'new-refresh',
        expires_in: 3600,
      }
      ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        authorization_endpoint: 'http://keycloak/auth',
        token_endpoint: 'http://keycloak/token',
      })
      ;($fetch.raw as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        _data: tokenResponse,
      })

      const result = await exchangeCode('auth-code', 'verifier-xyz')

      expect(result).toEqual(tokenResponse)
      expect($fetch.raw).toHaveBeenCalledTimes(1)
      const [, opts] = ($fetch.raw as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
        string,
        { method: string; body: string; headers: Record<string, string> },
      ]
      expect(opts.method).toBe('POST')
      expect(opts.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
      const body = new URLSearchParams(opts.body)
      expect(body.get('grant_type')).toBe('authorization_code')
      expect(body.get('code')).toBe('auth-code')
      expect(body.get('code_verifier')).toBe('verifier-xyz')
      expect(body.get('client_id')).toBe('cbs-nova-bff')
      expect(body.get('client_secret')).toBe('change_me_in_production')
      expect(body.get('redirect_uri')).toBe(callbackUrl)
    })
  })

  describe('refreshTokens', () => {
    it('posts to token endpoint with refresh_token grant', async () => {
      const tokenResponse = { access_token: 'refreshed-access', expires_in: 3600 }
      ;($fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        token_endpoint: 'http://keycloak/token',
      })
      ;($fetch.raw as unknown as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
        _data: tokenResponse,
      })

      const result = await refreshTokens('refresh-123')

      expect(result).toEqual(tokenResponse)
      const [, opts] = ($fetch.raw as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
        string,
        { method: string; body: string },
      ]
      expect(opts.method).toBe('POST')
      const body = new URLSearchParams(opts.body)
      expect(body.get('grant_type')).toBe('refresh_token')
      expect(body.get('refresh_token')).toBe('refresh-123')
      expect(body.get('client_id')).toBe('cbs-nova-bff')
    })
  })

  describe('fetchUserInfo', () => {
    it('GETs userinfo endpoint with Bearer token', async () => {
      const user = {
        sub: 'u-1',
        preferred_username: 'devuser',
        email: 'devuser@example.com',
        name: 'Dev User',
      }
      ;($fetch as unknown as ReturnType<typeof vi.fn>)
        .mockResolvedValueOnce({
          userinfo_endpoint: 'http://keycloak/userinfo',
        })
        .mockResolvedValueOnce(user)

      const result = await fetchUserInfo('access-123')

      expect(result).toEqual(user)
      expect($fetch).toHaveBeenLastCalledWith(
        'http://keycloak/userinfo',
        expect.objectContaining({
          headers: { Authorization: 'Bearer access-123' },
          retry: false,
        }),
      )
    })
  })

  describe('expiringSoon', () => {
    it('detects a token that expires within the buffer', () => {
      const now = Math.floor(Date.now() / 1000)
      const payload = { exp: now + 30 }
      const token = `header.${globalThis.btoa(JSON.stringify(payload))}.sig`
      expect(expiringSoon(token)).toBe(true)
    })

    it('does not flag a token that expires beyond the buffer', () => {
      const now = Math.floor(Date.now() / 1000)
      const payload = { exp: now + 120 }
      const token = `header.${globalThis.btoa(JSON.stringify(payload))}.sig`
      expect(expiringSoon(token)).toBe(false)
    })
  })
})
