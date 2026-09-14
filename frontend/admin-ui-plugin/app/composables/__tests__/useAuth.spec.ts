import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuth } from '../useAuth'

describe('useAuth', () => {
  beforeEach(() => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: true },
    } as ReturnType<typeof useRuntimeConfig>)
  })

  it('enabled reflects public.authEnabled', async () => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: false },
    } as ReturnType<typeof useRuntimeConfig>)
    const auth = await useAuth()
    expect(auth.enabled).toBe(false)
  })

  it('login navigates to /api/v1/auth/login with current path as redirect', async () => {
    vi.mocked(useRoute as never).mockReturnValue({ path: '/runner' } as ReturnType<typeof useRoute>)
    const auth = await useAuth()
    auth.login()
    expect(navigateTo).toHaveBeenCalledWith(
      `/api/v1/auth/login?redirect=${encodeURIComponent('/runner')}`,
      { external: true },
    )
  })

  it('logout POSTs to /api/v1/auth/logout with X-Requested-With and navigates to the returned redirect', async () => {
    vi.mocked($fetch as never).mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/v1/auth/logout'
          ? { redirect: '/signed-out' }
          : { authenticated: false, user: null },
      ),
    )
    const auth = await useAuth()
    await auth.logout()
    expect($fetch).toHaveBeenCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({ 'X-Requested-With': 'XMLHttpRequest' }),
      }),
    )
    expect(navigateTo).toHaveBeenCalledWith('/signed-out', { external: true })
  })

  it('logout falls back to / navigation on failure', async () => {
    vi.mocked($fetch as never).mockImplementation((url: string) =>
      url === '/api/v1/auth/logout'
        ? Promise.reject(new Error('boom'))
        : Promise.resolve({ authenticated: false, user: null }),
    )
    const auth = await useAuth()
    await auth.logout()
    expect(navigateTo).toHaveBeenCalledWith('/', { external: true })
  })

  it('fetches session and populates user/authenticated before returning', async () => {
    vi.mocked($fetch as never).mockResolvedValueOnce({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser' },
    })
    const auth = await useAuth()

    expect(auth.authenticated.value).toBe(true)
    expect(auth.user.value).toEqual({ sub: 'u-1', preferred_username: 'devuser' })
  })

  it('fetches the session exactly once per mount', async () => {
    vi.mocked($fetch as never).mockResolvedValue({ authenticated: false, user: null })

    await useAuth()

    const sessionCalls = vi
      .mocked($fetch)
      .mock.calls.filter(([url]) => url === '/api/v1/auth/session')
    expect(sessionCalls).toHaveLength(1)
  })
})
