import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import PlainConstructList from '../dsl/PlainConstructList.vue'

const constructs: DslConstruct[] = [
  { name: 'CreateOrder', type: 'Process', status: 'Valid' },
  { name: 'CreditWallet', type: 'Transaction', status: 'Invalid' },
  { name: 'GetOrder', type: 'Function', status: 'Published' },
  { name: 'SendEmail', type: 'Helper', status: 'Draft' },
  { name: 'AddFunds', type: 'Process', status: 'Added' },
  { name: 'RetireFlow', type: 'Process', status: 'Deleted' },
  { name: 'ConflictFlow', type: 'Process', status: 'Conflicting' },
  { name: 'MutateFlow', type: 'Process', status: 'Modified' },
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
    expect(text).toContain('AddFunds')
    expect(text).toContain('RetireFlow')
    expect(text).toContain('ConflictFlow')
    expect(text).toContain('MutateFlow')
    expect(text).toContain('Process (5)')
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

  it('renders a status icon for each construct', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    for (const c of constructs) {
      const icon = wrapper.find(`[data-testid="construct-status-icon-${c.status}"]`)
      expect(icon.exists(), `icon for ${c.status}`).toBe(true)
      expect(icon.attributes('aria-label')).toBe(`Status: ${c.status}`)
    }
  })

  it('colors the construct name by its status', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    const nameOf = (name: string) =>
      wrapper.findAll('span.truncate').find((s) => s.text() === name)!

    expect(nameOf('CreateOrder').classes()).toContain('text-green-300')
    expect(nameOf('CreditWallet').classes()).toContain('text-red-300')
    expect(nameOf('GetOrder').classes()).toContain('text-sky-300')
    expect(nameOf('SendEmail').classes()).toContain('text-gray-300')
    expect(nameOf('AddFunds').classes()).toContain('text-emerald-300')
    expect(nameOf('RetireFlow').classes()).toContain('text-orange-300')
    expect(nameOf('ConflictFlow').classes()).toContain('text-red-400')
    expect(nameOf('MutateFlow').classes()).toContain('text-yellow-300')
  })

  it('does not strike or bold build-problem names by default', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    const name = wrapper.findAll('span.truncate').find((s) => s.text() === 'CreditWallet')!
    expect(name.classes()).not.toContain('line-through')
    expect(name.classes()).not.toContain('font-bold')
  })

  it('applies strikethrough to build-problem names when enabled', () => {
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: null, strikethroughOnBuildError: true },
    })

    const invalid = wrapper.findAll('span.truncate').find((s) => s.text() === 'CreditWallet')!
    expect(invalid.classes()).toContain('line-through')

    const valid = wrapper.findAll('span.truncate').find((s) => s.text() === 'CreateOrder')!
    expect(valid.classes()).not.toContain('line-through')
  })

  it('applies bold to build-problem names when enabled', () => {
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: null, boldOnBuildError: true },
    })

    const invalid = wrapper.findAll('span.truncate').find((s) => s.text() === 'CreditWallet')!
    expect(invalid.classes()).toContain('font-bold')

    const valid = wrapper.findAll('span.truncate').find((s) => s.text() === 'CreateOrder')!
    expect(valid.classes()).not.toContain('font-bold')
  })

  it('renders a delete button for Draft items when deletable is true', () => {
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: null, deletable: true },
    })

    expect(
      wrapper.find('[data-testid="plain-construct-list-item-delete-SendEmail"]').exists(),
    ).toBe(true)
    expect(
      wrapper.find('[data-testid="plain-construct-list-item-delete-CreateOrder"]').exists(),
    ).toBe(false)
  })

  it('does not render delete buttons when deletable is false', () => {
    const wrapper = mount(PlainConstructList, { props: { constructs, selectedName: null } })

    expect(
      wrapper.find('[data-testid="plain-construct-list-item-delete-SendEmail"]').exists(),
    ).toBe(false)
  })

  it('emits delete with the construct name when the delete button is clicked', async () => {
    const wrapper = mount(PlainConstructList, {
      props: { constructs, selectedName: null, deletable: true },
    })

    await wrapper
      .find('[data-testid="plain-construct-list-item-delete-SendEmail"]')
      .trigger('click')

    expect(wrapper.emitted('select')).toBeFalsy()
    expect(wrapper.emitted('delete')).toBeTruthy()
    expect(wrapper.emitted('delete')![0]).toEqual(['SendEmail'])
  })
})
