import { beforeEach, describe, expect, it, vi } from 'vitest'

type RuntimeConfig = {
  public: {
    temporalUiBaseUrl?: string
    temporalNamespace?: string
  }
}

const { config } = vi.hoisted(() => ({
  config: {
    public: {
      temporalUiBaseUrl: '' as string | undefined,
      temporalNamespace: 'default' as string | undefined,
    } as RuntimeConfig['public'],
  } as RuntimeConfig,
}))

vi.mock('nuxt/app', () => ({
  useRuntimeConfig: () => config,
}))

import { useTemporalLink } from '../useTemporalLink'

describe('useTemporalLink', () => {
  beforeEach(() => {
    config.public.temporalUiBaseUrl = ''
    config.public.temporalNamespace = 'default'
  })

  it('reports enabled=false when base url is blank', () => {
    config.public.temporalUiBaseUrl = ''
    const link = useTemporalLink()
    expect(link.enabled).toBe(false)
    expect(link.workflowUrl('wf-1')).toBeNull()
  })

  it('reports enabled=false when base url is whitespace only', () => {
    config.public.temporalUiBaseUrl = '   '
    const link = useTemporalLink()
    expect(link.enabled).toBe(false)
    expect(link.workflowUrl('wf-1')).toBeNull()
  })

  it('builds the v2 Temporal UI deep-link with default namespace', () => {
    config.public.temporalUiBaseUrl = 'http://localhost:8233'
    config.public.temporalNamespace = 'default'
    const link = useTemporalLink()

    expect(link.enabled).toBe(true)
    expect(link.workflowUrl('wf-123')).toBe(
      'http://localhost:8233/namespaces/default/workflows/wf-123',
    )
  })

  it('honours a custom namespace', () => {
    config.public.temporalUiBaseUrl = 'http://localhost:8233'
    config.public.temporalNamespace = 'staging'
    const link = useTemporalLink()
    expect(link.workflowUrl('wf-abc')).toBe(
      'http://localhost:8233/namespaces/staging/workflows/wf-abc',
    )
  })

  it('strips a trailing slash from the base url', () => {
    config.public.temporalUiBaseUrl = 'http://localhost:8233/'
    const link = useTemporalLink()
    expect(link.workflowUrl('wf-1')).toBe(
      'http://localhost:8233/namespaces/default/workflows/wf-1',
    )
  })

  it('returns null when workflowId is empty / blank / null / undefined', () => {
    config.public.temporalUiBaseUrl = 'http://localhost:8233'
    const link = useTemporalLink()

    expect(link.workflowUrl('')).toBeNull()
    expect(link.workflowUrl('   ')).toBeNull()
    expect(link.workflowUrl(null)).toBeNull()
    expect(link.workflowUrl(undefined)).toBeNull()
  })

  it('percent-encodes namespace and workflow id segments', () => {
    config.public.temporalUiBaseUrl = 'http://localhost:8233'
    config.public.temporalNamespace = 'name with space'
    const link = useTemporalLink()
    expect(link.workflowUrl('wf/with/slash')).toBe(
      'http://localhost:8233/namespaces/name%20with%20space/workflows/wf%2Fwith%2Fslash',
    )
  })
})