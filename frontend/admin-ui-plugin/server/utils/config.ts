export function useBackendConfig() {
  const config = useRuntimeConfig()
  return {
    baseUrl: (config.backendBaseUrl as string) ?? 'http://localhost:8090',
    apiKey: (config.backendApiKey as string) ?? '',
    timeoutMs: Number(config.backendTimeoutMs ?? 10000),
  }
}

export function useAuthConfig() {
  const config = useRuntimeConfig()
  return {
    issuer: (config.authIssuer as string) ?? '',
    clientId: (config.authClientId as string) ?? 'cbs-nova-bff',
    clientSecret: (config.authClientSecret as string) ?? '',
    callbackUrl: (config.authCallbackUrl as string) ?? 'http://localhost:3000/api/v1/auth/callback',
    postLogoutRedirect: (config.authPostLogoutRedirect as string) ?? '/',
    enabled: Boolean((config.authIssuer as string) ?? ''),
  }
}
