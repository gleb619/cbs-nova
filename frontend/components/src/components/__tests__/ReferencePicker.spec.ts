import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import ReferencePicker from '../dsl/ReferencePicker.vue'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Valid' },
  { name: 'CancelOrder', type: 'Process', status: 'Draft' },
  { name: 'SendEmail', type: 'Function', status: 'Valid' },
  { name: 'LogHelper', type: 'Helper', status: 'Valid' },
]

describe('ReferencePicker', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(ReferencePicker, {
      props: { type: 'Process', constructs },
    })

    expect(wrapper.find('[data-testid="reference-picker"]').exists()).toBe(true)
  })

  it('renders constructs compatible with the requested type', () => {
    const wrapper = mount(ReferencePicker, {
      props: { type: 'Process', constructs },
    })

    const buttons = wrapper.findAll('button')
    const labels = buttons.map((b) => b.text())

    expect(labels.some((t) => t.includes('CreateOrder'))).toBe(true)
    expect(labels.some((t) => t.includes('CancelOrder'))).toBe(true)
    expect(labels.some((t) => t.includes('LogHelper'))).toBe(true)
    expect(labels.some((t) => t.includes('SendEmail'))).toBe(false)
  })

  it('filters the list as the user types in the search box', async () => {
    const wrapper = mount(ReferencePicker, {
      props: { type: 'Process', constructs },
    })

    const input = wrapper.find('input[type="text"]')
    await input.setValue('Create')

    const buttons = wrapper.findAll('button')
    const labels = buttons.map((b) => b.text())

    expect(labels.some((t) => t.includes('CreateOrder'))).toBe(true)
    expect(labels.some((t) => t.includes('CancelOrder'))).toBe(false)
  })

  it('shows the empty state when no constructs match the search', async () => {
    const wrapper = mount(ReferencePicker, {
      props: { type: 'Process', constructs },
    })

    const input = wrapper.find('input[type="text"]')
    await input.setValue('nothing-matches-this')

    expect(wrapper.text()).toContain('No matches')
    expect(wrapper.findAll('button')).toHaveLength(0)
  })

  it('emits pick with the selected construct name when a row is clicked', async () => {
    const wrapper = mount(ReferencePicker, {
      props: { type: 'Process', constructs },
    })

    const buttons = wrapper.findAll('button')
    const target = buttons.find((b) => b.text().includes('CreateOrder'))!
    await target.trigger('click')

    expect(wrapper.emitted('pick')).toBeTruthy()
    expect(wrapper.emitted('pick')![0]).toEqual(['CreateOrder'])
  })
})
