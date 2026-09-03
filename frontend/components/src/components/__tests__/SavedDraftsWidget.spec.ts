import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import SavedDraftsWidget from '../dsl/SavedDraftsWidget.vue'

const DRAFTS: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
  { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
]

// The drawer teleports into <body>; stub Teleport so it renders in place.
const mountWidget = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) =>
  mount(SavedDraftsWidget, {
    props: { drafts: DRAFTS, ...props },
    slots: slots as never,
    global: { stubs: { teleport: true } },
  })

describe('SavedDraftsWidget', () => {
  it('shows the draft count on the badge', () => {
    const wrapper = mountWidget()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
  })

  it('shows an ellipsis while the first load is in flight', () => {
    const wrapper = mountWidget({ drafts: [], loading: true })

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('…')
  })

  it('keeps showing the last known count while refreshing', () => {
    const wrapper = mountWidget({ loading: true })

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
  })

  it('marks the badge and exposes the message when loading failed', () => {
    const wrapper = mountWidget({ drafts: [], error: 'boom' })

    const badge = wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]')
    expect(badge.classes()).toContain('bg-red-100')
    expect(badge.attributes('title')).toBe('boom')
  })

  it('keeps the drawer closed until Details is pressed', async () => {
    const wrapper = mountWidget()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
    expect(wrapper.emitted('open')).toHaveLength(1)
  })

  it('lists the drafts inside the drawer', async () => {
    const wrapper = mountWidget()

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('alpha')
  })

  it('emits select and closes the drawer when a draft is picked', async () => {
    const wrapper = mountWidget()
    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')

    expect(wrapper.emitted('select')).toEqual([['alpha']])
    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)
  })

  it('emits refresh on mount when autoLoad is set', () => {
    const wrapper = mountWidget({ autoLoad: true })

    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('does not emit refresh on mount by default', () => {
    const wrapper = mountWidget()

    expect(wrapper.emitted('refresh')).toBeUndefined()
  })

  it('emits refresh from the drawer button and disables it while loading', async () => {
    const wrapper = mountWidget()
    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    const button = wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]')
    await button.trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)

    await wrapper.setProps({ loading: true })
    expect(
      wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('renders the error inside the drawer', async () => {
    const wrapper = mountWidget({ error: 'drafts unavailable' })

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').text()).toContain(
      'drafts unavailable',
    )
  })

  it('omits the drawer error region when there is no error', async () => {
    const wrapper = mountWidget()

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').exists()).toBe(false)
  })

  it('exposes drafts and a refresh callback to the header and footer slots', async () => {
    const wrapper = mountWidget(
      {},
      {
        header: (slotProps: { drafts: SavedDraftSummary[]; refresh: () => void }) =>
          h('button', { 'data-testid': 'slot-header', onClick: slotProps.refresh }, [
            `header:${slotProps.drafts.length}`,
          ]),
        footer: (slotProps: { drafts: SavedDraftSummary[] }) =>
          h('div', { 'data-testid': 'slot-footer' }, [`footer:${slotProps.drafts.length}`]),
      },
    )

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    expect(wrapper.find('[data-testid="slot-header"]').text()).toBe('header:2')
    expect(wrapper.find('[data-testid="slot-footer"]').text()).toBe('footer:2')

    await wrapper.find('[data-testid="slot-header"]').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('highlights the selected draft in the drawer', async () => {
    const wrapper = mountWidget({ selectedName: 'beta' })

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')

    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items[1].classes()).toContain('bg-gray-700')
  })
})
