import { beforeEach, describe, expect, it, vi } from 'vitest'

type RuntimeConfig = {
  public: {
    stalePollMs?: number
  }
}

const { config, useRuntimeConfigMock } = vi.hoisted(() => ({
  config: {
    public: {
      stalePollMs: undefined as number | undefined,
    },
  } as RuntimeConfig,
  useRuntimeConfigMock: vi.fn(),
}))

vi.mock('nuxt/app', () => ({
  useRuntimeConfig: () => useRuntimeConfigMock(),
}))

import { DEFAULT_STALE_POLL_MS, resolveStalePollMs } from '../useStalePollInterval'

describe('resolveStalePollMs', () => {
  beforeEach(() => {
    config.public = { stalePollMs: undefined }
    useRuntimeConfigMock.mockReset().mockReturnValue(config)
  })

  it('returns an explicit positive interval unchanged', () => {
    expect(resolveStalePollMs(1234)).toBe(1234)
  })

  it('falls through to the config/default for explicit 0', () => {
    config.public.stalePollMs = 2500
    expect(resolveStalePollMs(0)).toBe(2500)
  })

  it('falls through to the config/default for a negative explicit value', () => {
    config.public.stalePollMs = 2500
    expect(resolveStalePollMs(-100)).toBe(2500)
  })

  it('falls through to the config/default when explicit is undefined', () => {
    config.public.stalePollMs = 2500
    expect(resolveStalePollMs(undefined)).toBe(2500)
  })

  it('returns a positive runtimeConfig value', () => {
    config.public.stalePollMs = 7500
    expect(resolveStalePollMs()).toBe(7500)
  })

  it('ignores a zero runtimeConfig value and falls back to the default', () => {
    config.public.stalePollMs = 0
    expect(resolveStalePollMs()).toBe(DEFAULT_STALE_POLL_MS)
  })

  it('ignores a negative runtimeConfig value and falls back to the default', () => {
    config.public.stalePollMs = -1
    expect(resolveStalePollMs()).toBe(DEFAULT_STALE_POLL_MS)
  })

  it('falls back to the default when the config key is missing', () => {
    config.public = {}
    expect(resolveStalePollMs()).toBe(DEFAULT_STALE_POLL_MS)
  })

  it('falls back to the default when useRuntimeConfig throws', () => {
    useRuntimeConfigMock.mockImplementation(() => {
      throw new Error('no nuxt context')
    })
    expect(() => resolveStalePollMs()).not.toThrow()
    expect(resolveStalePollMs()).toBe(DEFAULT_STALE_POLL_MS)
  })
})
