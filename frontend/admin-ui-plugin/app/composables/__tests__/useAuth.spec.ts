import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest'
import { useAuth } from '../useAuth'

describe('useAuth', () => {
  beforeEach(() => {
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: true },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
  })

  it('enabled reflects public.authEnabled', async () => {
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: false },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    const auth = await useAuth()
    expect(auth.enabled).toBe(false)
  })

  it('login navigates to /api/v1/auth/login with current path as redirect', async () => {
    const routeMock = useRoute as unknown as Mock
    vi.mocked(routeMock).mockReturnValue({ path: '/runner' } as ReturnType<typeof useRoute>)
    const auth = await useAuth()
    auth.login()
    expect(navigateTo).toHaveBeenCalledWith(
      `/api/v1/auth/login?redirect=${encodeURIComponent('/runner')}`,
      { external: true },
    )
  })

  it('logout POSTs to /api/v1/auth/logout with X-Requested-With and navigates to the returned redirect', async () => {
    const fetchMock = $fetch as unknown as Mock
    vi.mocked(fetchMock).mockImplementation((url: string) =>
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
    const fetchMock = $fetch as unknown as Mock
    vi.mocked(fetchMock).mockImplementation((url: string) =>
      url === '/api/v1/auth/logout'
        ? Promise.reject(new Error('boom'))
        : Promise.resolve({ authenticated: false, user: null }),
    )
    const auth = await useAuth()
    await auth.logout()
    expect(navigateTo).toHaveBeenCalledWith('/', { external: true })
  })

  it('fetches session and populates user/authenticated before returning', async () => {
    const fetchMock = $fetch as unknown as Mock
    vi.mocked(fetchMock).mockResolvedValueOnce({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser' },
    })
    const auth = await useAuth()

    expect(auth.authenticated.value).toBe(true)
    expect(auth.user.value).toEqual({ sub: 'u-1', preferred_username: 'devuser' })
  })

  it('fetches the session exactly once per mount', async () => {
    const fetchMock = $fetch as unknown as Mock
    vi.mocked(fetchMock).mockResolvedValue({ authenticated: false, user: null })

    await useAuth()

    const sessionCalls = vi
      .mocked(fetchMock)
      .mock.calls.filter(([url]) => url === '/api/v1/auth/session')
    expect(sessionCalls).toHaveLength(1)
  })
})
