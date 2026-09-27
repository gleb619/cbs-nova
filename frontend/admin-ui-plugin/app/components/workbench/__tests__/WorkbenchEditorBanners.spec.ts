import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import WorkbenchEditorBanners from '../WorkbenchEditorBanners.vue'

// All banners are purely presentational: the parent owns the state and the
// retry callback. These specs pin the render conditions exactly as defined.
const baseProps = {
  isFileBacked: false,
  restoredFromDraft: false,
  draftSavedAt: null as number | null,
  autosaveOffline: false,
  deleteError: null as string | null,
}

function mountBanners(extraProps: Record<string, unknown> = {}) {
  return mount(WorkbenchEditorBanners, {
    props: { ...baseProps, ...extraProps },
    attachTo: document.body,
  })
}

describe('WorkbenchEditorBanners', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('renders nothing when no banner condition is set', () => {
    const wrapper = mountBanners()
    expect(wrapper.find('[data-testid="draft-restore-banner"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="dsl-autosave-offline-banner"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="dsl-workbench-delete-error"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the draft restore banner when restoredFromDraft is true', () => {
    const wrapper = mountBanners({ restoredFromDraft: true, draftSavedAt: 1_700_000_000_000 })
    expect(wrapper.find('[data-testid="draft-restore-banner"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('emits discard-draft when the restore banner discard button is clicked', async () => {
    const wrapper = mountBanners({ restoredFromDraft: true })
    await wrapper.find('[data-testid="draft-restore-banner-discard-button"]').trigger('click')
    expect(wrapper.emitted('discard-draft')).toHaveLength(1)
    wrapper.unmount()
  })

  it('hides the draft restore banner when isFileBacked is true', () => {
    const wrapper = mountBanners({ restoredFromDraft: true, isFileBacked: true })
    expect(wrapper.find('[data-testid="draft-restore-banner"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the autosave offline banner when autosaveOffline is true', () => {
    const wrapper = mountBanners({ autosaveOffline: true })
    expect(wrapper.find('[data-testid="dsl-autosave-offline-banner"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('hides the autosave offline banner when isFileBacked is true', () => {
    const wrapper = mountBanners({ autosaveOffline: true, isFileBacked: true })
    expect(wrapper.find('[data-testid="dsl-autosave-offline-banner"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the delete error banner with the message when deleteError is set', () => {
    const wrapper = mountBanners({ deleteError: 'Delete failed' })
    const banner = wrapper.find('[data-testid="dsl-workbench-delete-error"]')
    expect(banner.exists()).toBe(true)
    expect(banner.find('[data-testid="error-banner"]').text()).toContain('Delete failed')
    wrapper.unmount()
  })

  it('emits retry-delete when the delete error retry button is clicked', async () => {
    const wrapper = mountBanners({ deleteError: 'Delete failed' })
    await wrapper.find('[data-testid="error-banner"] button').trigger('click')
    expect(wrapper.emitted('retry-delete')).toHaveLength(1)
    wrapper.unmount()
  })

  it('renders the draft restore and delete error banners independently', () => {
    const wrapper = mountBanners({ restoredFromDraft: true, deleteError: 'Delete failed' })
    expect(wrapper.find('[data-testid="draft-restore-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dsl-workbench-delete-error"]').exists()).toBe(true)
    wrapper.unmount()
  })
})
