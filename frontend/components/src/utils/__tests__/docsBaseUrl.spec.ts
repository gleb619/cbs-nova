import { describe, expect, it } from 'vitest'
import { buildDocsBaseUrl } from '../docsBaseUrl'

describe('buildDocsBaseUrl', () => {
  it('builds a docs URL from an SSH remote with .git suffix', () => {
    expect(
      buildDocsBaseUrl({
        branch: 'feat/x',
        remote: { origin: { url: 'git@github.com:acme/cbs-nova.git' } },
      }),
    ).toBe('https://github.com/acme/cbs-nova/blob/feat/x/docs/')
  })

  it('behaves the same for an SSH remote without the .git suffix', () => {
    expect(
      buildDocsBaseUrl({
        branch: 'feat/x',
        remote: { origin: { url: 'git@github.com:acme/cbs-nova' } },
      }),
    ).toBe('https://github.com/acme/cbs-nova/blob/feat/x/docs/')
  })

  it('defaults the branch to main for an HTTPS remote without a branch', () => {
    expect(
      buildDocsBaseUrl({ remote: { origin: { url: 'https://github.com/acme/cbs-nova' } } }),
    ).toBe('https://github.com/acme/cbs-nova/blob/main/docs/')
  })

  it('strips .git from an HTTPS remote', () => {
    expect(
      buildDocsBaseUrl({
        remote: { origin: { url: 'https://github.com/acme/cbs-nova.git' } },
      }),
    ).toBe('https://github.com/acme/cbs-nova/blob/main/docs/')
  })

  it('returns undefined when git info is missing', () => {
    expect(buildDocsBaseUrl(undefined)).toBeUndefined()
  })

  it('returns undefined when remote.origin.url is missing', () => {
    expect(buildDocsBaseUrl({ branch: 'main' })).toBeUndefined()
    expect(buildDocsBaseUrl({ remote: {} })).toBeUndefined()
  })

  it('returns undefined for non-GitHub remotes', () => {
    expect(
      buildDocsBaseUrl({ remote: { origin: { url: 'git@gitlab.com:acme/cbs-nova.git' } } }),
    ).toBeUndefined()
    expect(
      buildDocsBaseUrl({ remote: { origin: { url: 'https://bitbucket.org/acme/cbs-nova' } } }),
    ).toBeUndefined()
  })

  it('keeps the full subgroup path for a repo name containing a slash', () => {
    expect(
      buildDocsBaseUrl({
        branch: 'feat/x',
        remote: { origin: { url: 'git@github.com:acme/group/sub/cbs-nova.git' } },
      }),
    ).toBe('https://github.com/acme/group/sub/cbs-nova/blob/feat/x/docs/')
  })
})
