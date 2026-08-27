import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import PlainConstructList from '../dsl/PlainConstructList.vue'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Valid' },
  { name: 'CreditWallet', type: 'Transaction', status: 'Invalid' },
  { name: 'GetOrder', type: 'Function', status: 'Published' },
  { name: 'SendEmail', type: 'Helper', status: 'Draft' },
]

describe('PlainConstructList', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs: [], selectedName: null } })

    expect(wrapper.find('[data-testid="plain-construct-list"]').exists()).toBe(true)
  })

  it('renders all four group headers', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs: [], selectedName: null } })

    const text = wrapper.text()
    expect(text).toContain('Process')
    expect(text).toContain('Transaction')
    expect(text).toContain('Function')
    expect(text).toContain('Helper')
  })

  it('groups constructs by type and shows each along with its status badge', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('CreditWallet')
    expect(text).toContain('GetOrder')
    expect(text).toContain('SendEmail')
    expect(text).toContain('Process (1)')
    expect(text).toContain('Transaction (1)')
    expect(text).toContain('Function (1)')
    expect(text).toContain('Helper (1)')
  })

  it('shows an empty "none" placeholder for a type group with no constructs', () => {
    const wrapper = mount(PlainConstructList, {
      props: {
        constructs: [{ name: 'OnlyProcess', type: 'Process', status: 'Draft' }],
        selectedName: null,
      },
    })

    const lis = wrapper.findAll('li')
    expect(lis.length).toBeGreaterThan(0)
    expect(lis.filter((li) => li.text() === 'none').length).toBe(3)
  })

  it('emits select with the construct name when an item button is clicked', async () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    const item = wrapper.findAll('button').find((b) => b.text().includes('CreateOrder'))!
    await item.trigger('click')

    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')![0]).toEqual(['CreateOrder'])
  })

  it('calls onSelect callback when provided', async () => {
    const onSelect = vi.fn()
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: null, onSelect },
    })

    const item = wrapper.findAll('button').find((b) => b.text().includes('CreateOrder'))!
    await item.trigger('click')

    expect(onSelect).toHaveBeenCalledWith('CreateOrder')
  })

  it('highlights the selected construct', () => {
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: 'GetOrder' },
    })

    const selected = wrapper.findAll('button').find((b) => b.text().includes('GetOrder'))!
    expect(selected.classes()).toContain('bg-gray-800')

    const unselected = wrapper.findAll('button').find((b) => b.text().includes('CreateOrder'))!
    expect(unselected.classes()).not.toContain('bg-gray-800')
  })

  it('applies status chip styles for each status', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    const badges = wrapper.findAll('span.rounded-full')
    expect(badges.some((b) => b.classes().includes('bg-green-100'))).toBe(true)
    expect(badges.some((b) => b.classes().includes('bg-red-100'))).toBe(true)
    expect(badges.some((b) => b.classes().includes('bg-blue-100'))).toBe(true)
    expect(badges.some((b) => b.classes().includes('bg-gray-200'))).toBe(true)
  })
})
