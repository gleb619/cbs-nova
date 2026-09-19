import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PromotionDiffTable from '../PromotionDiffTable.vue'

const RESULTS = [
  { name: 'Alpha', outcome: 'created' as const },
  { name: 'Beta', outcome: 'updated' as const, message: 'definition differs (+12 bytes)' },
  { name: 'Gamma', outcome: 'unchanged' as const },
  { name: 'Delta', outcome: 'skipped' as const, message: 'invalid entry' },
]

describe('DslPromotionDiffTable', () => {
  it('renders one row per definition with its outcome badge', () => {
    const wrapper = mount(PromotionDiffTable, { props: { results: RESULTS } })

    expect(wrapper.find('[data-testid="promotion-diff-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-diff-row-Alpha"]').text()).toContain('CREATED')
    expect(wrapper.find('[data-testid="promotion-diff-row-Beta"]').text()).toContain('UPDATED')
    expect(wrapper.find('[data-testid="promotion-diff-row-Gamma"]').text()).toContain('UNCHANGED')
    expect(wrapper.find('[data-testid="promotion-diff-row-Delta"]').text()).toContain('SKIPPED')
    expect(wrapper.find('[data-testid="promotion-outcome-created"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-updated"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-unchanged"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-skipped"]').exists()).toBe(true)
  })

  it('shows the message column content', () => {
    const wrapper = mount(PromotionDiffTable, { props: { results: RESULTS } })

    expect(wrapper.text()).toContain('definition differs (+12 bytes)')
  })

  it('shows an empty state when there are no results', () => {
    const wrapper = mount(PromotionDiffTable, { props: { results: [] } })

    expect(wrapper.find('[data-testid="promotion-diff-empty"]').exists()).toBe(true)
  })

  it('shows a loading row while the diff is being computed', () => {
    const wrapper = mount(PromotionDiffTable, { props: { results: [], loading: true } })

    expect(wrapper.find('[data-testid="promotion-diff-loading"]').exists()).toBe(true)
  })
})
