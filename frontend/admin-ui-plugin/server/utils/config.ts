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
  // Coerce numeric config values: anything non-numeric ('', 'abc', null,
  // undefined) collapses to 0 via the `||` short-circuit. `Number(x)` of
  // a valid numeric string returns the number; NaN is filtered by the
  // same `||` fall-through.
  const idle = Number((config.authSessionIdleTimeoutSeconds as unknown) ?? 0) || 0
  const absolute = Number((config.authSessionAbsoluteTimeoutSeconds as unknown) ?? 0) || 0
  // Boolean-like string toggles. Default for the secure-override is
  // disabled (''), default for rotate-on-refresh is enabled.
  const secureCookies = (config.authSessionSecureCookies as string) ?? ''
  const rotateRaw = (config.authSessionRotateOnRefresh as string) ?? ''
  return {
    issuer: (config.authIssuer as string) ?? '',
    clientId: (config.authClientId as string) ?? 'cbs-nova-bff',
    clientSecret: (config.authClientSecret as string) ?? '',
    callbackUrl: (config.authCallbackUrl as string) ?? 'http://localhost:3000/api/v1/auth/callback',
    postLogoutRedirect: (config.authPostLogoutRedirect as string) ?? '/',
    enabled: Boolean((config.authIssuer as string) ?? ''),
    sessionIdleTimeoutSeconds: idle,
    sessionAbsoluteTimeoutSeconds: absolute,
    sessionSecureCookies: secureCookies === 'true',
    sessionRotateOnRefresh: rotateRaw === '' || rotateRaw === 'true',
  }
}
