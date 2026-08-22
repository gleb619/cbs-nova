import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InputMappingGrid from '../dsl/InputMappingGrid.vue'

describe('InputMappingGrid', () => {
  it('renders the Field and Mapped Value headers', () => {
    const wrapper = mount(InputMappingGrid, { props: { mappings: {} } })

    const headers = wrapper.findAll('th').map((th) => th.text())
    expect(headers).toEqual(['Field', 'Mapped Value'])
  })

  it('shows the empty state when there are no mappings', () => {
    const wrapper = mount(InputMappingGrid, { props: { mappings: {} } })

    expect(wrapper.text()).toContain('No mappings')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
  })

  it('renders each field and mapped value pair', () => {
    const wrapper = mount(InputMappingGrid, {
      props: { mappings: { orderId: '$.order.id', customer: '$.user.name' } },
    })

    const text = wrapper.text()
    expect(text).toContain('orderId')
    expect(text).toContain('$.order.id')
    expect(text).toContain('customer')
    expect(text).toContain('$.user.name')
    expect(wrapper.text()).not.toContain('No mappings')
  })

  it('renders the mappings in the order they were provided', () => {
    const wrapper = mount(InputMappingGrid, {
      props: { mappings: { a: '1', b: '2' } },
    })

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('a')
    expect(rows[1]!.text()).toContain('b')
  })
})