import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DraftRestoreBanner from '../dsl/DraftRestoreBanner.vue'

describe('DraftRestoreBanner', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(DraftRestoreBanner, { props: { savedAt: null } })

    expect(wrapper.find('[data-testid="draft-restore-banner"]').exists()).toBe(true)
  })

  it('renders the recovery notice', () => {
    const wrapper = mount(DraftRestoreBanner, { props: { savedAt: null } })

    const banner = wrapper.find('[role="status"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('Restored from local draft')
  })

  it('omits the saved-at suffix when savedAt is null', () => {
    const wrapper = mount(DraftRestoreBanner, { props: { savedAt: null } })

    expect(wrapper.text()).not.toContain('saved')
  })

  it('shows the saved-at suffix when savedAt is provided', () => {
    const wrapper = mount(DraftRestoreBanner, { props: { savedAt: 1_700_000_000_000 } })

    expect(wrapper.text()).toContain('saved')
  })

  it('emits discard when the Discard button is clicked', async () => {
    const wrapper = mount(DraftRestoreBanner, { props: { savedAt: null } })

    const button = wrapper.find('button')
    expect(button.text()).toBe('Discard')

    await button.trigger('click')

    expect(wrapper.emitted('discard')).toBeTruthy()
    expect(wrapper.emitted('discard')).toHaveLength(1)
  })
})
