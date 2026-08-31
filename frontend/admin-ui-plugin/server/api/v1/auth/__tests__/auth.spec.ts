import { beforeEach, describe, expect, it, vi } from 'vitest'

let queryValue: Record<string, unknown> = {}
let cookieJar: Record<string, string | undefined> = {}
let redirectUrl: string | undefined
let redirectStatus: number | undefined
let setCookies: Array<{ name: string; value: string; opts: Record<string, unknown> }> = []

vi.mock('h3', async (importOriginal) => {
  const actual = await importOriginal<typeof import('h3')>()
  return {
    ...actual,
    getQuery: (_event: unknown) => queryValue,
    getCookie: (_event: unknown, name: string) => cookieJar[name],
    setCookie: (_event: unknown, name: string, value: string, opts: Record<string, unknown>) => {
      setCookies.push({ name, value, opts })
      cookieJar[name] = value
    },
    deleteCookie: (_event: unknown, name: string, _opts: Record<string, unknown>) => {
      cookieJar[name] = undefined
      setCookies.push({ name, value: '', opts: { maxAge: -1 } })
    },
    sendRedirect: (_event: unknown, url: string, status = 302) => {
      redirectUrl = url
      redirectStatus = status
      return undefined
    },
  }
})

vi.mock('~/server/utils/oidcSession', async (importOriginal) => {
  const actual = await importOriginal<typeof import('~/server/utils/oidcSession')>()
  return {
    ...actual,
    discoverOidc: vi.fn(() =>
      Promise.resolve({
        authorization_endpoint: 'http://keycloak/auth',
        token_endpoint: 'http://keycloak/token',
        end_session_endpoint: 'http://keycloak/logout',
        userinfo_endpoint: 'http://keycloak/userinfo',
      }),
    ),
    createPkcePair: vi.fn(() => Promise.resolve({ verifier: 'verifier-xyz', challenge: 'challenge-xyz' })),
    randomState: vi.fn(() => 'state-xyz'),
    exchangeCode: vi.fn(() =>
      Promise.resolve({
        access_token: 'access-xyz',
        refresh_token: 'refresh-xyz',
        expires_in: 3600,
      }),
    ),
    refreshTokens: vi.fn(() =>
      Promise.resolve({
        access_token: 'refreshed-xyz',
        refresh_token: 'refreshed-rt',
        expires_in: 3600,
      }),
    ),
    fetchUserInfo: vi.fn(() =>
      Promise.resolve({
        sub: 'u-1',
        preferred_username: 'devuser',
        email: 'devuser@example.com',
        name: 'Dev User',
      }),
    ),
  }
})

const loginHandler = (await import('../login.get')).default
const callbackHandler = (await import('../callback.get')).default
const logoutHandler = (await import('../logout.get')).default
const sessionHandler = (await import('../session.get')).default

const fakeEvent = {} as Parameters<typeof loginHandler>[0]

function setRuntimeConfig(overrides: Record<string, unknown> = {}) {
  vi.mocked(useRuntimeConfig as never).mockReturnValue({
    backendBaseUrl: 'http://localhost:8090',
    backendApiKey: '',
    backendTimeoutMs: 10000,
    authIssuer: '',
    authClientId: 'cbs-nova-bff',
    authClientSecret: '',
    authCallbackUrl: 'http://localhost:3000/api/v1/auth/callback',
    authPostLogoutRedirect: '/',
    public: { appName: 'CBS Nova Admin', authEnabled: false },
    ...overrides,
  } as ReturnType<typeof useRuntimeConfig>)
  vi.mocked(useAuthConfig as never).mockReturnValue({
    issuer: overrides.authIssuer ?? '',
    clientId: overrides.authClientId ?? 'cbs-nova-bff',
    clientSecret: overrides.authClientSecret ?? '',
    callbackUrl: overrides.authCallbackUrl ?? 'http://localhost:3000/api/v1/auth/callback',
    postLogoutRedirect: overrides.authPostLogoutRedirect ?? '/',
    enabled: Boolean(overrides.authIssuer ?? ''),
  })
}

beforeEach(() => {
  queryValue = {}
  cookieJar = {}
  redirectUrl = undefined
  redirectStatus = undefined
  setCookies = []
  setRuntimeConfig()
})

describe('login.get', () => {
  it('returns 404 when auth is not configured', async () => {
    setRuntimeConfig()
    await expect(loginHandler(fakeEvent)).rejects.toMatchObject({
      statusCode: 404,
    })
  })

  it('redirects to authorization endpoint with all required query params and sets txn cookie', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    queryValue = { redirect: '/runner' }

    await loginHandler(fakeEvent)

    expect(redirectStatus).toBe(302)
    expect(redirectUrl).toContain('http://keycloak/auth')
    if (!redirectUrl) throw new Error('redirectUrl missing')
    const url = new URL(redirectUrl)
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('client_id')).toBe('cbs-nova-bff')
    expect(url.searchParams.get('redirect_uri')).toBe('http://localhost:3000/api/v1/auth/callback')
    expect(url.searchParams.get('scope')).toBe('openid profile email')
    expect(url.searchParams.get('state')).toBe('state-xyz')
    expect(url.searchParams.get('code_challenge')).toBe('challenge-xyz')
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')

    const txn = setCookies.find((c) => c.name === 'cbs_oidc_txn')
    expect(txn).toBeDefined()
    if (!txn) throw new Error('txn cookie missing')
    expect(JSON.parse(txn.value)).toEqual({
      state: 'state-xyz',
      verifier: 'verifier-xyz',
      redirect: '/runner',
    })
    expect(txn.opts.httpOnly).toBe(true)
    expect(txn.opts.sameSite).toBe('lax')
  })

  it('ignores fully-qualified redirect values', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    queryValue = { redirect: 'https://evil.com/abc' }

    await loginHandler(fakeEvent)

    const txn = setCookies.find((c) => c.name === 'cbs_oidc_txn')
    expect(txn).toBeDefined()
    if (!txn) throw new Error('txn cookie missing')
    expect(JSON.parse(txn.value).redirect).toBe('/')
  })
})

describe('callback.get', () => {
  it('returns 404 when auth is not configured', async () => {
    setRuntimeConfig()
    await expect(callbackHandler(fakeEvent)).rejects.toMatchObject({
      statusCode: 404,
    })
  })

  it('returns 403 when state does not match', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_oidc_txn = JSON.stringify({ state: 'real-state', verifier: 'v', redirect: '/' })
    queryValue = { code: 'auth-code', state: 'wrong-state' }

    await expect(callbackHandler(fakeEvent)).rejects.toMatchObject({
      statusCode: 403,
      statusMessage: 'Invalid OIDC state',
    })
  })

  it('exchanges code, writes session cookies, clears txn, and redirects to stored path', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_oidc_txn = JSON.stringify({ state: 'state-xyz', verifier: 'verifier-xyz', redirect: '/runner' })
    queryValue = { code: 'auth-code', state: 'state-xyz' }

    await callbackHandler(fakeEvent)

    const at = setCookies.find((c) => c.name === 'cbs_at')
    const rt = setCookies.find((c) => c.name === 'cbs_rt')
    expect(at?.value).toBe('access-xyz')
    expect(rt?.value).toBe('refresh-xyz')

    expect(cookieJar.cbs_oidc_txn).toBeUndefined()
    expect(redirectStatus).toBe(302)
    expect(redirectUrl).toBe('/runner')
  })

  it('ignores open-redirect stored path and falls back to /', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_oidc_txn = JSON.stringify({ state: 'state-xyz', verifier: 'v', redirect: 'https://evil' })
    queryValue = { code: 'auth-code', state: 'state-xyz' }

    await callbackHandler(fakeEvent)

    expect(redirectUrl).toBe('/')
  })
})

describe('logout.get', () => {
  it('returns 404 when auth is not configured', async () => {
    setRuntimeConfig()
    await expect(logoutHandler(fakeEvent)).rejects.toMatchObject({
      statusCode: 404,
    })
  })

  it('clears session and redirects to postLogoutRedirect', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_at = 'access-xyz'
    cookieJar.cbs_rt = 'refresh-xyz'

    await logoutHandler(fakeEvent)

    expect(cookieJar.cbs_at).toBeUndefined()
    expect(cookieJar.cbs_rt).toBeUndefined()
    expect(redirectStatus).toBe(302)
    expect(redirectUrl).toBe('/')
  })
})

describe('session.get', () => {
  it('returns {authenticated:false,enabled:false} when auth is not configured', async () => {
    setRuntimeConfig()
    const result = await sessionHandler(fakeEvent)
    expect(result).toEqual({ authenticated: false, enabled: false })
  })

  it('returns 401 when configured but no access token cookie', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    await expect(sessionHandler(fakeEvent)).rejects.toMatchObject({
      statusCode: 401,
    })
  })

  it('returns user payload when configured with valid access token', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_at = 'access-xyz'

    const result = await sessionHandler(fakeEvent)

    expect(result).toEqual({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser', email: 'devuser@example.com', name: 'Dev User' },
    })
  })

  it('refreshes on 401 userinfo and returns refreshed user', async () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    cookieJar.cbs_at = 'old-access'
    cookieJar.cbs_rt = 'refresh-xyz'
    vi.mocked((await import('~/server/utils/oidcSession')).fetchUserInfo)
      .mockRejectedValueOnce({ response: { status: 401 } })
      .mockResolvedValueOnce({ sub: 'u-2', preferred_username: 'refreshed' })

    const result = await sessionHandler(fakeEvent)

    expect(result).toEqual({ authenticated: true, user: { sub: 'u-2', preferred_username: 'refreshed' } })
  })
})
