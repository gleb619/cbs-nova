import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { useAuth } from '../useAuth'

describe('useAuth', () => {
  beforeEach(() => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: true },
    } as ReturnType<typeof useRuntimeConfig>)
  })

  it('enabled reflects public.authEnabled', () => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: false },
    } as ReturnType<typeof useRuntimeConfig>)
    const auth = useAuth()
    expect(auth.enabled).toBe(false)
  })

  it('login navigates to /api/v1/auth/login with current path as redirect', () => {
    vi.mocked(useRoute as never).mockReturnValue({ path: '/runner' } as ReturnType<typeof useRoute>)
    const auth = useAuth()
    auth.login()
    expect(navigateTo).toHaveBeenCalledWith(
      `/api/v1/auth/login?redirect=${encodeURIComponent('/runner')}`,
      { external: true },
    )
  })

  it('logout navigates to /api/v1/auth/logout', () => {
    const auth = useAuth()
    auth.logout()
    expect(navigateTo).toHaveBeenCalledWith('/api/v1/auth/logout', { external: true })
  })

  it('fetches session and populates user/authenticated', async () => {
    vi.mocked($fetch as never).mockResolvedValueOnce({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser' },
    })
    const auth = useAuth()
    await flushPromises()

    expect(auth.authenticated.value).toBe(true)
    expect(auth.user.value).toEqual({ sub: 'u-1', preferred_username: 'devuser' })
  })
})
