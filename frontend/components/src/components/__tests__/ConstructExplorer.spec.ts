import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import ConstructExplorer from '../dsl/ConstructExplorer.vue'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Valid' },
  { name: 'CreditWallet', type: 'Transaction', status: 'Invalid' },
  { name: 'GetOrder', type: 'Function', status: 'Published' },
  { name: 'SendEmail', type: 'Helper', status: 'Draft' },
]

describe('ConstructExplorer', () => {
  it('renders the expandable toggle button, search input, and all four group headers', () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs: [], selectedName: null } })

    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.get('[aria-label="Collapse constructs"]').exists()).toBe(true)

    const text = wrapper.text()
    expect(text).toContain('Process')
    expect(text).toContain('Transaction')
    expect(text).toContain('Function')
    expect(text).toContain('Helper')
  })

  it('shows an empty "none" placeholder for a type group with no constructs', () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs: [], selectedName: null } })

    expect(wrapper.findAll('li').every((li) => li.text() === 'none')).toBe(true)
  })

  it('groups constructs by type and shows each along with its status badge', () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs, selectedName: null } })

    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('CreditWallet')
    expect(text).toContain('GetOrder')
    expect(text).toContain('SendEmail')

    expect(text).toContain('(1)')
    const valid = wrapper.findAll('button').find((b) => b.text().includes('Valid'))
    expect(valid).toBeDefined()
  })

  it('emits select with the construct name when an item button is clicked', async () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs, selectedName: null } })

    const item = wrapper.findAll('button').find((b) => b.text().includes('CreateOrder'))!
    await item.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['CreateOrder'])
  })

  it('highlights the selected construct', () => {
    const wrapper = mount(ConstructExplorer, {
      props: { constructs, selectedName: 'GetOrder' },
    })

    const selected = wrapper.findAll('button').find((b) => b.text().includes('GetOrder'))!
    expect(selected.classes()).toContain('bg-gray-800')

    const unselected = wrapper.findAll('button').find((b) => b.text().includes('CreateOrder'))!
    expect(unselected.classes()).not.toContain('bg-gray-800')
  })

  it('filters the listed constructs as the user searches', async () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs, selectedName: null } })

    await wrapper.find('input[type="text"]').setValue('Order')

    expect(wrapper.text()).toContain('CreateOrder')
    expect(wrapper.text()).toContain('GetOrder')
    expect(wrapper.text()).not.toContain('CreditWallet')
    expect(wrapper.text()).not.toContain('SendEmail')
  })

  it('renders loading placeholders instead of the list while loading', () => {
    const wrapper = mount(ConstructExplorer, {
      props: { constructs, selectedName: null, loading: true },
    })

    expect(wrapper.findAll('.animate-pulse').length).toBeGreaterThan(0)
    expect(wrapper.text()).not.toContain('CreateOrder')
  })

  it('collapses into the expand rail when collapsed is true and emits updates on toggle', async () => {
    const wrapper = mount(ConstructExplorer, {
      props: { constructs, selectedName: null, collapsed: true },
    })

    expect(wrapper.find('input[type="text"]').exists()).toBe(false)
    expect(wrapper.get('[aria-label="Expand constructs"]').exists()).toBe(true)

    await wrapper.get('[aria-label="Expand constructs"]').trigger('click')
    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')!.at(-1)).toEqual([false])
  })

  it('emits an update when collapsing an expanded panel', async () => {
    const wrapper = mount(ConstructExplorer, { props: { constructs, selectedName: null } })

    await wrapper.get('[aria-label="Collapse constructs"]').trigger('click')

    expect(wrapper.emitted('update:collapsed')).toBeTruthy()
    expect(wrapper.emitted('update:collapsed')!.at(-1)).toEqual([true])
  })
})