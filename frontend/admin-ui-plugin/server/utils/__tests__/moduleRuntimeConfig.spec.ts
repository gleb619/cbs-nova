import { describe, expect, it } from 'vitest'
import { resolveRuntimeConfig, type ExistingRuntimeConfig } from '../moduleRuntimeConfig'
import type { ModuleOptions } from '../../../module'

describe('resolveRuntimeConfig', () => {
  it('produces every documented default from all-empty input', () => {
    const { config, publicConfig } = resolveRuntimeConfig({}, {})

    expect(config).toEqual({
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
    })
    expect(publicConfig).toEqual({
      authEnabled: false,
      appName: 'CBS Nova Admin',
      temporalUiBaseUrl: '',
      temporalNamespace: 'default',
    })
  })

  it('lets an options value win over the default', () => {
    const options: ModuleOptions = {
      backendBaseUrl: 'http://options-backend:9090',
      backendApiKey: 'options-key',
      backendTimeoutMs: 2500,
      authIssuer: 'https://options-issuer',
      authClientId: 'options-client',
      authClientSecret: 'options-secret',
      authCallbackUrl: 'https://options-callback',
      authPostLogoutRedirect: '/options-logout',
      authSessionIdleTimeoutSeconds: 300,
      authSessionAbsoluteTimeoutSeconds: 3600,
      authSessionSecureCookies: 'true',
      authSessionRotateOnRefresh: 'false',
      appName: 'Options Admin',
      temporalUiBaseUrl: 'https://options-temporal-ui',
      temporalNamespace: 'options-ns',
    }
    const { config, publicConfig } = resolveRuntimeConfig({}, options)

    expect(config).toEqual({
      backendBaseUrl: 'http://options-backend:9090',
      backendApiKey: 'options-key',
      backendTimeoutMs: 2500,
      authIssuer: 'https://options-issuer',
      authClientId: 'options-client',
      authClientSecret: 'options-secret',
      authCallbackUrl: 'https://options-callback',
      authPostLogoutRedirect: '/options-logout',
      authSessionIdleTimeoutSeconds: 300,
      authSessionAbsoluteTimeoutSeconds: 3600,
      authSessionSecureCookies: 'true',
      authSessionRotateOnRefresh: 'false',
    })
    expect(publicConfig).toEqual({
      authEnabled: true,
      appName: 'Options Admin',
      temporalUiBaseUrl: 'https://options-temporal-ui',
      temporalNamespace: 'options-ns',
    })
  })

  it('lets an existing (env-populated) value win over options', () => {
    const existing: ExistingRuntimeConfig = {
      backendBaseUrl: 'http://env-backend:7070',
      backendApiKey: 'env-key',
      backendTimeoutMs: 1234,
      authIssuer: 'https://env-issuer',
      authClientId: 'env-client',
      authClientSecret: 'env-secret',
      authCallbackUrl: 'https://env-callback',
      authPostLogoutRedirect: '/env-logout',
      authSessionIdleTimeoutSeconds: 111,
      authSessionAbsoluteTimeoutSeconds: 222,
      authSessionSecureCookies: 'true',
      authSessionRotateOnRefresh: 'false',
      public: {
        authEnabled: true,
        appName: 'Env Admin',
        temporalUiBaseUrl: 'https://env-temporal-ui',
        temporalNamespace: 'env-ns',
      },
    }
    const { config, publicConfig } = resolveRuntimeConfig(existing, {
      backendBaseUrl: 'http://options-backend:9090',
      appName: 'Options Admin',
    })

    expect(config.backendBaseUrl).toBe('http://env-backend:7070')
    expect(config.backendTimeoutMs).toBe(1234)
    expect(config.authIssuer).toBe('https://env-issuer')
    expect(config.authSessionRotateOnRefresh).toBe('false')
    expect(publicConfig.appName).toBe('Env Admin')
    expect(publicConfig.temporalNamespace).toBe('env-ns')
  })

  it('preserves backendTimeoutMs = 0 instead of falling through to 10000', () => {
    const { config } = resolveRuntimeConfig({ backendTimeoutMs: 0 }, {})
    expect(config.backendTimeoutMs).toBe(0)
  })

  it('preserves 0 session timeouts as 0, not as unset', () => {
    const existing: ExistingRuntimeConfig = {
      authSessionIdleTimeoutSeconds: 0,
      authSessionAbsoluteTimeoutSeconds: 0,
    }
    const { config } = resolveRuntimeConfig(existing, {
      authSessionIdleTimeoutSeconds: 900,
      authSessionAbsoluteTimeoutSeconds: 3600,
    })
    expect(config.authSessionIdleTimeoutSeconds).toBe(0)
    expect(config.authSessionAbsoluteTimeoutSeconds).toBe(0)
  })

  it('preserves authSessionSecureCookies of false and empty string', () => {
    expect(
      resolveRuntimeConfig({ authSessionSecureCookies: false }, {}).config.authSessionSecureCookies,
    ).toBe(false)
    expect(
      resolveRuntimeConfig({ authSessionSecureCookies: '' }, {}).config.authSessionSecureCookies,
    ).toBe('')
  })

  it('lets authEnabled = false win even when an issuer is configured', () => {
    const { publicConfig } = resolveRuntimeConfig(
      { authIssuer: 'https://issuer', public: { authEnabled: false } },
      {},
    )
    expect(publicConfig.authEnabled).toBe(false)
  })

  it('defaults authEnabled to Boolean(issuer) when unset', () => {
    expect(
      resolveRuntimeConfig({ authIssuer: 'https://issuer' }, {}).publicConfig.authEnabled,
    ).toBe(true)
    expect(resolveRuntimeConfig({ authIssuer: '' }, {}).publicConfig.authEnabled).toBe(false)
    expect(resolveRuntimeConfig({}, {}).publicConfig.authEnabled).toBe(false)
  })

  it('resolves callback url, post-logout redirect, appName and temporalNamespace defaults', () => {
    const { config, publicConfig } = resolveRuntimeConfig({}, {})

    expect(config.authCallbackUrl).toBe('http://localhost:3000/api/v1/auth/callback')
    expect(config.authPostLogoutRedirect).toBe('/')
    expect(publicConfig.appName).toBe('CBS Nova Admin')
    expect(publicConfig.temporalNamespace).toBe('default')
  })
})
