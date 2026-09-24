import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DraftsMetadata } from '../../types/drafts'
import DraftMetadataCard from '../dsl/DraftMetadataCard.vue'

const METADATA: DraftsMetadata = {
  draftCount: 3,
  workbenchPath: '.workbench/drafts',
  sizeMb: 0.12,
  gitBranch: 'main',
  gitEnabled: true,
  statusCacheTtlSeconds: 5,
  historyLimit: 20,
}

const mountCard = (props: Record<string, unknown> = {}) =>
  mount(DraftMetadataCard, { props: { metadata: METADATA, ...props } })

describe('DraftMetadataCard', () => {
  it('renders the workbench path', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-path"]').text()).toBe(
      '.workbench/drafts',
    )
  })

  it('renders the draft count', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-count"]').text()).toBe('3')
  })

  it('renders size in MB', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-size"]').text()).toBe('0.12 MB')
  })

  it('renders the git branch', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-branch"]').text()).toBe('main')
  })

  it('renders an em dash when gitBranch is null', () => {
    const wrapper = mountCard({ metadata: { ...METADATA, gitBranch: null } })
    expect(wrapper.find('[data-testid="draft-metadata-card-branch"]').text()).toBe('—')
  })

  it('renders an em dash when sizeMb is null', () => {
    const wrapper = mountCard({ metadata: { ...METADATA, sizeMb: null } })
    expect(wrapper.find('[data-testid="draft-metadata-card-size"]').text()).toBe('—')
  })

  it('renders git enabled status', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-git"]').text()).toBe('enabled')
  })

  it('renders git disabled when gitEnabled is false', () => {
    const wrapper = mountCard({ metadata: { ...METADATA, gitEnabled: false } })
    expect(wrapper.find('[data-testid="draft-metadata-card-git"]').text()).toBe('disabled')
  })

  it('renders the cache TTL with seconds suffix', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-cache-ttl"]').text()).toBe('5s')
  })

  it('renders the history limit', () => {
    const wrapper = mountCard()
    expect(wrapper.find('[data-testid="draft-metadata-card-history-limit"]').text()).toBe('20')
  })

  it('shows the loading state when metadata is null and loading is true', () => {
    const wrapper = mountCard({ metadata: null, loading: true })
    expect(wrapper.find('[data-testid="draft-metadata-card-loading"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="draft-metadata-card-details"]').exists()).toBe(false)
  })

  it('shows the error message when error is set', () => {
    const wrapper = mountCard({ error: 'workspace unavailable' })
    expect(wrapper.find('[data-testid="draft-metadata-card-error"]').text()).toContain(
      'workspace unavailable',
    )
    expect(wrapper.find('[data-testid="draft-metadata-card-details"]').exists()).toBe(false)
  })

  it('shows details when both loading and metadata are provided', () => {
    const wrapper = mountCard({ loading: true })
    expect(wrapper.find('[data-testid="draft-metadata-card-details"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="draft-metadata-card-loading"]').exists()).toBe(false)
  })

  it('renders nothing special when metadata is null and not loading', () => {
    const wrapper = mountCard({ metadata: null, loading: false })
    expect(wrapper.find('[data-testid="draft-metadata-card-details"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="draft-metadata-card-loading"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="draft-metadata-card-error"]').exists()).toBe(false)
  })
})
