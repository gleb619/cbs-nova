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
  it('exposes root data-testid', () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    expect(wrapper.find('[data-testid="metadata-panel"]').exists()).toBe(true)
  })

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

  it('falls back to em-dashes for missing optional fields', () => {
    const wrapper = mount(MetadataPanel, {
      props: { construct: { name: 'SendEmail', type: 'Function', status: 'Draft' } },
    })

    const text = wrapper.text()
    expect(text).toContain('SendEmail')
    expect(text).toContain('Function')
    expect(text).toContain('Draft')
    expect(text).not.toContain('1.2.3')
    expect(text).not.toContain('order-worker')
    // Five optional fields default to em-dashes when absent:
    // version, taskQueue, inputType, outputType, description. hasCompensation
    // is a boolean and renders as '—' only when undefined (also 1 em-dash),
    // so the minimum total for a fully-empty optional set is 6.
    const emDashCount = text.split('—').length - 1
    expect(emDashCount).toBeGreaterThanOrEqual(5)
    expect(emDashCount).toBeLessThanOrEqual(6)
  })

  it('renders inputType, outputType, hasCompensation, and description when provided', () => {
    const wrapper = mount(MetadataPanel, {
      props: {
        construct: {
          name: 'Charge',
          type: 'Transaction',
          status: 'Valid',
          inputType: 'OrderRequest',
          outputType: 'Receipt',
          hasCompensation: true,
          description: 'Charges a customer and refunds on failure.',
        },
      },
    })

    const text = wrapper.text()
    expect(text).toContain('OrderRequest')
    expect(text).toContain('Receipt')
    expect(text).toContain('Yes')
    expect(text).toContain('Charges a customer and refunds on failure.')
  })

  it('collapses to name + type summary when toggle is clicked', async () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    await wrapper.find('[data-testid="metadata-panel-toggle"]').trigger('click')

    expect(wrapper.find('[data-testid="metadata-panel-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="metadata-panel-body"]').exists()).toBe(false)
    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('Process')
    expect(text).not.toContain('order-worker')
  })
})