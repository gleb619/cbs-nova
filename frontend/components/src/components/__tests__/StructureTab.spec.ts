import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ObjectStructureDto } from '../../types/dsl'
import StructureTab from '../dsl/StructureTab.vue'

const structure: ObjectStructureDto = {
  name: 'BatchProcessing',
  type: 'process',
  logic: [
    { kind: 'execute', status: 'configured', required: true },
    { kind: 'preview', status: 'default', required: false },
    { kind: 'explain', status: 'default', required: false },
  ],
  fields: [
    {
      path: 'taskQueue',
      value: 'BatchProcessing-queue',
      type: 'java.lang.String',
      description: 'Temporal task queue this workflow polls',
    },
  ],
}

describe('StructureTab', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(StructureTab)

    expect(wrapper.find('[data-testid="structure-tab"]').exists()).toBe(true)
  })

  it('renders the structure table when structure is provided', () => {
    const wrapper = mount(StructureTab, { props: { structure } })

    expect(wrapper.find('[data-testid="structure-fields"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('BatchProcessing')
    expect(wrapper.text()).toContain('BatchProcessing-queue')
  })

  it('shows a placeholder when no structure is available', () => {
    const wrapper = mount(StructureTab)

    expect(wrapper.text()).toContain('No structure available for this object.')
  })

  it('shows the loading spinner while structure is loading', () => {
    const wrapper = mount(StructureTab, { props: { structureLoading: true } })

    expect(wrapper.find('[data-testid="structure-loading"]').exists()).toBe(true)
  })

  it('shows the error banner with retry when structure failed to load', async () => {
    const wrapper = mount(StructureTab, { props: { structureError: 'boom' } })

    expect(wrapper.find('[data-testid="structure-error"]').exists()).toBe(true)

    wrapper.find('button').trigger('click')
    const retries = wrapper.emitted('retry')
    expect(retries).toBeTruthy()
  })

  it('no longer renders the steps stub empty state', () => {
    const wrapper = mount(StructureTab, { props: { structure } })

    expect(wrapper.text()).not.toContain('No steps defined yet.')
    expect(wrapper.find('ol').exists()).toBe(false)
  })
})
