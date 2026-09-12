import type { UserInfo } from '~/server/utils/oidcSession'

type AuthState = {
  enabled: boolean
  user: UserInfo | null
  authenticated: boolean
}

export async function useAuth() {
  const config = useRuntimeConfig()
  const enabled = Boolean(config.public.authEnabled)

  const user = useState<UserInfo | null>('cbs-auth-user', () => null)
  const authenticated = useState<boolean>('cbs-auth-authenticated', () => false)

  async function loadSession() {
    if (!enabled) {
      authenticated.value = false
      user.value = null
      return
    }
    try {
      const data = await $fetch<AuthState>('/api/v1/auth/session')
      user.value = data.user ?? null
      authenticated.value = data.authenticated
    } catch {
      user.value = null
      authenticated.value = false
    }
  }

  await callOnce('cbs-auth-session', loadSession)

  const route = useRoute()
  function login() {
    const redirect = encodeURIComponent(route.path)
    return navigateTo(`/api/v1/auth/login?redirect=${redirect}`, { external: true })
  }

  async function logout() {
    try {
      const result = await $fetch<{ redirect?: string }>('/api/v1/auth/logout', {
        method: 'POST',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      })
      return navigateTo(result.redirect ?? '/', { external: true })
    } catch {
      return navigateTo('/', { external: true })
    }
  }

  return {
    enabled,
    user: readonly(user),
    authenticated: readonly(authenticated),
    login,
    logout,
  }
}
