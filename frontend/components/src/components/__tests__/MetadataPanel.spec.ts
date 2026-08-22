import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { DslConstruct } from '../../types/dsl'
import MetadataPanel from '../dsl/MetadataPanel.vue'

const construct: DslConstruct = {
  name: 'CreateOrder',
  type: 'Process',
  status: 'Valid',
  version: '1.2.3',
  taskQueue: 'order-worker',
}

describe('MetadataPanel', () => {
  it('prompts the user to select a construct when construct is null', () => {
    const wrapper = mount(MetadataPanel, { props: { construct: null } })

    expect(wrapper.text()).toContain('Select a construct to view metadata.')
    expect(wrapper.find('dl').exists()).toBe(false)
  })

  it('renders the construct name, type, and status', () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('Process')
    expect(text).toContain('Valid')
  })

  it('renders the version and task queue when provided', () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    expect(wrapper.text()).toContain('1.2.3')
    expect(wrapper.text()).toContain('order-worker')
  })

  it('falls back to em-dashes for a missing version and task queue', () => {
    const wrapper = mount(MetadataPanel, {
      props: { construct: { name: 'SendEmail', type: 'Function', status: 'Draft' } },
    })

    const text = wrapper.text()
    expect(text).toContain('SendEmail')
    expect(text).toContain('Function')
    expect(text).toContain('Draft')
    expect(text).not.toContain('1.2.3')
    expect(text).not.toContain('order-worker')
    const emDashCount = text.split('—').length - 1
    expect(emDashCount).toBe(2)
  })

  it('notes that editable metadata fields are coming soon', () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    expect(wrapper.text()).toContain('Editable metadata fields coming soon.')
  })
})