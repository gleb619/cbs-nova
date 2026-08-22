import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { StepDef } from '../../types/dsl'
import StructureTab from '../dsl/StructureTab.vue'

describe('StructureTab', () => {
  it('renders the empty state when no steps are supplied', () => {
    const wrapper = mount(StructureTab, { props: { steps: [] } })

    expect(wrapper.text()).toContain('No steps defined yet.')
    expect(wrapper.findAll('li')).toHaveLength(0)
    expect(wrapper.find('ol').exists()).toBe(false)
  })

  it('renders each step in order with name, type, and a 1-based index', () => {
    const steps: StepDef[] = [
      { id: 's1', type: 'helper', name: 'LogStart' },
      { id: 's2', type: 'function', name: 'CallPayment' },
      { id: 's3', type: 'transaction', name: 'BookInventory' },
      { id: 's4', type: 'step', name: 'NotifyUser' },
    ]

    const wrapper = mount(StructureTab, { props: { steps } })

    const items = wrapper.findAll('li')
    expect(items).toHaveLength(4)

    expect(items[0]!.text()).toContain('1.')
    expect(items[0]!.text()).toContain('LogStart')
    expect(items[0]!.text()).toContain('helper')

    expect(items[1]!.text()).toContain('2.')
    expect(items[1]!.text()).toContain('CallPayment')
    expect(items[1]!.text()).toContain('function')

    expect(items[2]!.text()).toContain('3.')
    expect(items[2]!.text()).toContain('BookInventory')
    expect(items[2]!.text()).toContain('transaction')

    expect(items[3]!.text()).toContain('4.')
    expect(items[3]!.text()).toContain('NotifyUser')
    expect(items[3]!.text()).toContain('step')
  })

  it('preserves the supplied step order in the rendered list', () => {
    const steps: StepDef[] = [
      { id: 'a', type: 'step', name: 'Alpha' },
      { id: 'b', type: 'step', name: 'Beta' },
      { id: 'c', type: 'step', name: 'Gamma' },
    ]

    const wrapper = mount(StructureTab, { props: { steps } })

    const names = wrapper.findAll('li').map((li) => {
      const span = li.findAll('span').find((s) => s.classes().includes('font-medium'))!
      return span.text()
    })

    expect(names).toEqual(['Alpha', 'Beta', 'Gamma'])
  })
})
