import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExecutionFilters from '../executions/ExecutionFilters.vue'

describe('ExecutionFilters', () => {
  it('emits an empty filter payload by default when Apply is clicked', async () => {
    const wrapper = mount(ExecutionFilters)

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.emitted('filter')?.[0]).toEqual([{}])
  })

  it('emits an empty filter payload when Reset is clicked', async () => {
    const wrapper = mount(ExecutionFilters)

    const statusSelect = wrapper.find('#filter-status')
    await statusSelect.setValue('Failed')
    const applyButton = wrapper.find('button')
    await applyButton.trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.emitted('filter')?.[0]).toEqual([{ status: 'Failed' }])

    const resetButton = wrapper.findAll('button').at(1)
    expect(resetButton?.text()).toBe('Reset')
    await resetButton?.trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(2)
    expect(wrapper.emitted('filter')?.[1]).toEqual([{}])
  })

  it('emits the full filter payload shape when all fields are populated', async () => {
    const wrapper = mount(ExecutionFilters)

    await wrapper.find('#filter-status').setValue('Completed')
    await wrapper.find('#filter-mode').setValue('RUN')
    await wrapper.find('#filter-entity').setValue('Order')
    await wrapper.find('#filter-from').setValue('2026-07-01')
    await wrapper.find('#filter-to').setValue('2026-07-30')
    await wrapper.find('#filter-correlation').setValue('corr-123')

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.emitted('filter')?.[0]).toEqual([
      {
        status: 'Completed',
        mode: 'RUN',
        entityName: 'Order',
        from: '2026-07-01',
        to: '2026-07-30',
        correlationId: 'corr-123',
      },
    ])
  })

  it('emits an updated payload when a single field changes', async () => {
    const wrapper = mount(ExecutionFilters)

    await wrapper.find('#filter-status').setValue('Running')
    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.emitted('filter')?.[0]).toEqual([{ status: 'Running' }])

    await wrapper.find('#filter-mode').setValue('EXPLAIN')
    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(2)
    expect(wrapper.emitted('filter')?.[1]).toEqual([{ status: 'Running', mode: 'EXPLAIN' }])
  })

  it('does not include empty string fields in the emitted payload', async () => {
    const wrapper = mount(ExecutionFilters)

    await wrapper.find('#filter-status').setValue('Pending')
    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')?.[0]).toEqual([
      {
        status: 'Pending',
      },
    ])
  })

  it('offers Stale as a status option and emits it when selected', async () => {
    const wrapper = mount(ExecutionFilters)

    const statusSelect = wrapper.find('#filter-status')
    const optionValues = statusSelect.findAll('option').map((o) => o.attributes('value'))
    expect(optionValues).toContain('Stale')

    await statusSelect.setValue('Stale')
    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('filter')).toHaveLength(1)
    expect(wrapper.emitted('filter')?.[0]).toEqual([{ status: 'Stale' }])
  })
})
