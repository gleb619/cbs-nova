import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthConfig, useBackendConfig } from '../config'

const setRuntimeConfig = (overrides: Record<string, unknown> = {}) => {
  vi.mocked(useRuntimeConfig as never).mockReturnValue({
    backendBaseUrl: 'http://localhost:8090',
    backendApiKey: '',
    backendTimeoutMs: 10000,
    authIssuer: '',
    authClientId: 'cbs-nova-bff',
    authClientSecret: '',
    authCallbackUrl: 'http://localhost:3000/api/v1/auth/callback',
    authPostLogoutRedirect: '/',
    authSessionIdleTimeoutSeconds: 0,
    authSessionAbsoluteTimeoutSeconds: 0,
    authSessionSecureCookies: '',
    authSessionRotateOnRefresh: '',
    public: { appName: 'CBS Nova Admin', authEnabled: false },
    ...overrides,
  } as ReturnType<typeof useRuntimeConfig>)
}

describe('useBackendConfig', () => {
  beforeEach(() => {
    setRuntimeConfig()
  })

  it('returns defaults when runtime config is empty', () => {
    const cfg = useBackendConfig()
    expect(cfg.baseUrl).toBe('http://localhost:8090')
    expect(cfg.apiKey).toBe('')
    expect(cfg.timeoutMs).toBe(10000)
  })

  it('applies explicit overrides', () => {
    setRuntimeConfig({
      backendBaseUrl: 'http://backend.example.com',
      backendApiKey: 'secret-key',
      backendTimeoutMs: 2500,
    })
    const cfg = useBackendConfig()
    expect(cfg.baseUrl).toBe('http://backend.example.com')
    expect(cfg.apiKey).toBe('secret-key')
    expect(cfg.timeoutMs).toBe(2500)
  })

  it('coerces numeric-string timeoutMs to a number', () => {
    setRuntimeConfig({ backendTimeoutMs: '5000' })
    const cfg = useBackendConfig()
    expect(cfg.timeoutMs).toBe(5000)
  })

  // Current implementation: Number('abc') → NaN. Unlike auth numeric values,
  // timeoutMs has no `|| 10000` guard, so a non-numeric runtime value leaks
  // NaN downstream to $fetch. Document current behavior; a follow-up guard is
  // suggested (out of scope for this test-only task).
  it('returns NaN for non-numeric timeoutMs', () => {
    setRuntimeConfig({ backendTimeoutMs: 'abc' })
    const cfg = useBackendConfig()
    expect(cfg.timeoutMs).toBeNaN()
  })
})

describe('useAuthConfig', () => {
  beforeEach(() => {
    setRuntimeConfig()
  })

  it('returns defaults when runtime config is empty', () => {
    const cfg = useAuthConfig()
    expect(cfg.issuer).toBe('')
    expect(cfg.clientId).toBe('cbs-nova-bff')
    expect(cfg.clientSecret).toBe('')
    expect(cfg.callbackUrl).toBe('http://localhost:3000/api/v1/auth/callback')
    expect(cfg.postLogoutRedirect).toBe('/')
    expect(cfg.enabled).toBe(false)
    expect(cfg.sessionIdleTimeoutSeconds).toBe(0)
    expect(cfg.sessionAbsoluteTimeoutSeconds).toBe(0)
    expect(cfg.sessionSecureCookies).toBe(false)
    expect(cfg.sessionRotateOnRefresh).toBe(true)
  })

  it('enables auth when authIssuer is a non-empty string', () => {
    setRuntimeConfig({ authIssuer: 'http://keycloak:8080/realms/cbs-nova' })
    const cfg = useAuthConfig()
    expect(cfg.enabled).toBe(true)
    expect(cfg.issuer).toBe('http://keycloak:8080/realms/cbs-nova')
  })

  it('keeps auth disabled when authIssuer is empty', () => {
    setRuntimeConfig({ authIssuer: '' })
    expect(useAuthConfig().enabled).toBe(false)
  })

  describe('sessionIdleTimeoutSeconds / sessionAbsoluteTimeoutSeconds coercion', () => {
    it.each([
      { value: '', expected: 0, label: 'empty string' },
      { value: 'abc', expected: 0, label: 'non-numeric string' },
      { value: null, expected: 0, label: 'null' },
      { value: '300', expected: 300, label: 'numeric string' },
      { value: '0', expected: 0, label: 'zero string' },
      { value: '-5', expected: -5, label: 'negative string' },
    ])('coerces $label ($value) to $expected', ({ value, expected }) => {
      setRuntimeConfig({
        authSessionIdleTimeoutSeconds: value,
        authSessionAbsoluteTimeoutSeconds: value,
      })
      const cfg = useAuthConfig()
      expect(cfg.sessionIdleTimeoutSeconds).toBe(expected)
      expect(cfg.sessionAbsoluteTimeoutSeconds).toBe(expected)
    })
  })

  describe('sessionSecureCookies coercion', () => {
    it.each([
      { value: 'true', expected: true },
      { value: 'false', expected: false },
      { value: '', expected: false },
      { value: '1', expected: false },
      { value: 'TRUE', expected: false },
    ])('sessionSecureCookies $value → $expected', ({ value, expected }) => {
      setRuntimeConfig({ authSessionSecureCookies: value })
      expect(useAuthConfig().sessionSecureCookies).toBe(expected)
    })
  })

  describe('sessionRotateOnRefresh coercion', () => {
    it.each([
      { value: '', expected: true, label: 'empty string (default-on)' },
      { value: 'true', expected: true },
      { value: 'false', expected: false },
      { value: 'x', expected: false },
    ])('sessionRotateOnRefresh $label → $expected', ({ value, expected }) => {
      setRuntimeConfig({ authSessionRotateOnRefresh: value })
      expect(useAuthConfig().sessionRotateOnRefresh).toBe(expected)
    })
  })
})
