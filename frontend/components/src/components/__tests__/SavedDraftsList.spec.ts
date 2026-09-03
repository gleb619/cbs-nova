import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import SavedDraftsList from '../dsl/SavedDraftsList.vue'

const DRAFTS: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
  { name: 'beta', updatedAt: 2 },
]

const mountList = (props: Record<string, unknown> = {}) =>
  mount(SavedDraftsList, { props: { drafts: DRAFTS, ...props } })

describe('SavedDraftsList', () => {
  it('renders one row per draft with its type and status', () => {
    const wrapper = mountList()

    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('alpha')
    expect(items[0].text()).toContain('Process')
    expect(items[0].text()).toContain('Draft')
  })

  it('falls back to an em dash and Draft for missing type and status', () => {
    const wrapper = mountList()

    const second = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')[1]
    expect(second.text()).toContain('—')
    expect(second.text()).toContain('Draft')
  })

  it('emits select with the draft name on click', async () => {
    const wrapper = mountList()

    await wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')[1].trigger('click')

    expect(wrapper.emitted('select')).toEqual([['beta']])
  })

  it('emits select on Enter for keyboard users', async () => {
    const wrapper = mountList()

    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('keydown.enter')

    expect(wrapper.emitted('select')).toEqual([['alpha']])
  })

  it('highlights the selected draft only', () => {
    const wrapper = mountList({ selectedName: 'beta' })

    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items[0].classes()).not.toContain('bg-gray-700')
    expect(items[1].classes()).toContain('bg-gray-700')
  })

  it('shows a loading hint when empty and loading', () => {
    const wrapper = mountList({ drafts: [], loading: true })

    expect(wrapper.text()).toContain('Loading drafts…')
    expect(wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')).toHaveLength(0)
  })

  it('shows the empty state when there is nothing to load', () => {
    const wrapper = mountList({ drafts: [], loading: false })

    expect(wrapper.text()).toContain('No saved drafts yet.')
  })

  it('keeps rendering rows while a refresh is in flight', () => {
    const wrapper = mountList({ loading: true })

    expect(wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('Loading drafts…')
  })

  it('honours custom test ids', () => {
    const wrapper = mountList({ testId: 'custom-list', itemTestId: 'custom-item' })

    expect(wrapper.find('[data-testid="custom-list"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="custom-item"]')).toHaveLength(2)
  })
})
