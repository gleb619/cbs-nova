import {beforeEach, describe, expect, it, vi, type Mock} from 'vitest'
import { performLogout } from '../logout'
import { useAuthConfig } from '../config'
import { clearOidcSession, discoverOidc, readSession } from '../oidcSession'

const callbackUrl = 'http://localhost:3000/api/v1/auth/callback'

const authConfigFixture = {
  issuer: 'http://keycloak:8080/realms/cbs-nova',
  clientId: 'cbs-nova-bff',
  clientSecret: '',
  callbackUrl,
  postLogoutRedirect: '/',
  enabled: true,
  sessionIdleTimeoutSeconds: 0,
  sessionAbsoluteTimeoutSeconds: 0,
  sessionSecureCookies: false,
  sessionRotateOnRefresh: true,
}

vi.mock('../config', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../config')>()
  return {
    ...actual,
    useAuthConfig: vi.fn(() => authConfigFixture),
  }
})

vi.mock('../oidcSession', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../oidcSession')>()
  return {
    ...actual,
    readSession: vi.fn(),
    discoverOidc: vi.fn(),
    clearOidcSession: vi.fn(),
  }
})

const fakeEvent = { node: { req: { headers: {} } } } as Parameters<typeof performLogout>[0]

describe('performLogout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useAuthConfig as Mock).mockReturnValue(authConfigFixture)
    vi.mocked(readSession as Mock).mockReturnValue({
      accessToken: undefined,
      refreshToken: undefined,
    })
    vi.mocked(discoverOidc as Mock).mockResolvedValue({
      authorization_endpoint: 'http://keycloak/auth',
      token_endpoint: 'http://keycloak/token',
    } as Awaited<ReturnType<typeof discoverOidc>>)
    vi.mocked(clearOidcSession as Mock).mockResolvedValue(undefined)
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockClear()
  })

  it('does not call $fetch and clears session when there is no refresh token', async () => {
    await performLogout(fakeEvent)

    expect(discoverOidc).not.toHaveBeenCalled()
    expect($fetch).not.toHaveBeenCalled()
    expect(clearOidcSession).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledWith(fakeEvent, callbackUrl)
  })

  it('POSTs to end_session_endpoint then clears session on the happy path', async () => {
    const refreshToken = 'refresh-xyz'
    const endSessionEndpoint = 'http://keycloak/logout'
    vi.mocked(readSession as Mock).mockReturnValue({
      accessToken: 'access-xyz',
      refreshToken,
    })
    vi.mocked(discoverOidc as Mock).mockResolvedValue({
      authorization_endpoint: 'http://keycloak/auth',
      token_endpoint: 'http://keycloak/token',
      end_session_endpoint: endSessionEndpoint,
    } as Awaited<ReturnType<typeof discoverOidc>>)

    await performLogout(fakeEvent)

    expect(discoverOidc).toHaveBeenCalledTimes(1)
    expect(discoverOidc).toHaveBeenCalledWith(authConfigFixture.issuer)
    expect($fetch).toHaveBeenCalledTimes(1)
    const [url, opts] = ($fetch as unknown as ReturnType<typeof vi.fn>).mock.calls[0] as [
      string,
      {
        method: string
        headers: Record<string, string>
        body: string
        retry: boolean
        timeout: number
      },
    ]
    expect(url).toBe(endSessionEndpoint)
    expect(opts.method).toBe('POST')
    expect(opts.headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    const body = new URLSearchParams(opts.body)
    expect(body.get('client_id')).toBe(authConfigFixture.clientId)
    expect(body.get('refresh_token')).toBe(refreshToken)
    expect(opts.retry).toBe(false)
    expect(opts.timeout).toBe(10000)
    expect(clearOidcSession).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledWith(fakeEvent, callbackUrl)
  })

  it('does not call $fetch and still clears session when discovery lacks end_session_endpoint', async () => {
    vi.mocked(readSession as Mock).mockReturnValue({
      accessToken: 'access-xyz',
      refreshToken: 'refresh-xyz',
    })

    await performLogout(fakeEvent)

    expect($fetch).not.toHaveBeenCalled()
    expect(clearOidcSession).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledWith(fakeEvent, callbackUrl)
  })

  it('does not throw and still clears session when discoverOidc rejects', async () => {
    vi.mocked(readSession as Mock).mockReturnValue({
      accessToken: 'access-xyz',
      refreshToken: 'refresh-xyz',
    })
    vi.mocked(discoverOidc as Mock).mockRejectedValue(new Error('discovery failed'))

    await expect(performLogout(fakeEvent)).resolves.toBeUndefined()

    expect($fetch).not.toHaveBeenCalled()
    expect(clearOidcSession).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledWith(fakeEvent, callbackUrl)
  })

  it('does not throw and still clears session when $fetch rejects', async () => {
    const refreshToken = 'refresh-xyz'
    const endSessionEndpoint = 'http://keycloak/logout'
    vi.mocked(readSession as Mock).mockReturnValue({
      accessToken: 'access-xyz',
      refreshToken,
    })
    vi.mocked(discoverOidc as Mock).mockResolvedValue({
      authorization_endpoint: 'http://keycloak/auth',
      token_endpoint: 'http://keycloak/token',
      end_session_endpoint: endSessionEndpoint,
    } as Awaited<ReturnType<typeof discoverOidc>>)
    ;($fetch as unknown as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error('logout failed'),
    )

    await expect(performLogout(fakeEvent)).resolves.toBeUndefined()

    expect($fetch).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledTimes(1)
    expect(clearOidcSession).toHaveBeenCalledWith(fakeEvent, callbackUrl)
  })
})
