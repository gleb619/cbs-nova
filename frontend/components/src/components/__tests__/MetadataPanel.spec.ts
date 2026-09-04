import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
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
  beforeEach(() => {
    // The panel reads its collapsed state from localStorage; clear it so each
    // test starts with the default (collapsed=true → body hidden) rather than
    // inheriting state from a prior run.
    window.localStorage.clear()
  })

  // Most tests want to inspect the field <dl>/textarea, which only render when
  // the panel is expanded. Open it once and return both the wrapper and the
  // mounted component for callers that need to drill further.
  async function mountExpanded(props: Parameters<typeof mount>[1]) {
    const wrapper = mount(MetadataPanel, props)
    await wrapper.find('[data-testid="metadata-panel-toggle"]').trigger('click')
    return wrapper
  }

  it('exposes root data-testid', () => {
    const wrapper = mount(MetadataPanel, { props: { construct } })

    expect(wrapper.find('[data-testid="metadata-panel"]').exists()).toBe(true)
  })

  it('prompts the user to select a construct when construct is null', () => {
    const wrapper = mount(MetadataPanel, { props: { construct: null } })

    expect(wrapper.text()).toContain('Select a construct to view metadata.')
    expect(wrapper.find('dl').exists()).toBe(false)
  })

  it('renders the construct name, type, and status', async () => {
    const wrapper = await mountExpanded({ props: { construct } })

    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('Process')
    expect(text).toContain('Valid')
  })

  it('renders the version and task queue when provided', async () => {
    const wrapper = await mountExpanded({ props: { construct } })

    expect(wrapper.text()).toContain('1.2.3')
    expect(wrapper.text()).toContain('order-worker')
  })

  it('falls back to em-dashes for missing optional fields', async () => {
    const wrapper = await mountExpanded({
      props: { construct: { name: 'SendEmail', type: 'Function', status: 'Draft' } },
    })

    const text = wrapper.text()
    expect(text).toContain('SendEmail')
    expect(text).toContain('Function')
    expect(text).toContain('Draft')
    expect(text).not.toContain('1.2.3')
    expect(text).not.toContain('order-worker')

    // Four optional fields default to em-dashes when absent:
    // version, taskQueue, inputType, outputType.
    const emDashCount = text.split('—').length - 1
    expect(emDashCount).toBeGreaterThanOrEqual(4)
    expect(emDashCount).toBeLessThanOrEqual(5)
  })

  it('renders inputType, outputType, and hasCompensation when provided', async () => {
    const wrapper = await mountExpanded({
      props: {
        construct: {
          name: 'Charge',
          type: 'Transaction',
          status: 'Valid',
          inputType: 'OrderRequest',
          outputType: 'Receipt',
          hasCompensation: true,
        },
      },
    })

    const text = wrapper.text()
    expect(text).toContain('OrderRequest')
    expect(text).toContain('Receipt')
    expect(text).toContain('Yes')
  })

  it('renders a spinner when loading is true and hides it otherwise', async () => {
    const hidden = mount(MetadataPanel, { props: { construct, loading: false } })
    expect(hidden.find('[data-testid="cbs-spinner"]').exists()).toBe(false)

    const visible = await mountExpanded({ props: { construct, loading: true } })
    expect(visible.find('[data-testid="cbs-spinner"]').exists()).toBe(true)
  })

  it('renders description in an editable textarea', async () => {
    const wrapper = await mountExpanded({
      props: {
        construct: {
          name: 'Charge',
          type: 'Transaction',
          status: 'Valid',
          description: 'Charges a customer and refunds on failure.',
        },
      },
    })

    const textarea = wrapper.find('[data-testid="metadata-field-description"]')
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe(
      'Charges a customer and refunds on failure.',
    )
  })

  it('emits update:description when the textarea changes', async () => {
    const wrapper = await mountExpanded({
      props: {
        construct: {
          name: 'Charge',
          type: 'Transaction',
          status: 'Valid',
          description: 'Initial',
        },
      },
    })

    const textarea = wrapper.find('[data-testid="metadata-field-description"]')
    await textarea.setValue('Updated description')

    expect(wrapper.emitted('update:description')).toEqual([['Updated description']])
  })

  it('collapses to name + type summary when toggle is clicked', async () => {
    const wrapper = await mountExpanded({ props: { construct } })

    // mountExpanded already opened the panel; one click returns it to the
    // collapsed state.
    await wrapper.find('[data-testid="metadata-panel-toggle"]').trigger('click')

    expect(wrapper.find('[data-testid="metadata-panel-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="metadata-panel-body"]').exists()).toBe(false)
    const text = wrapper.text()
    expect(text).toContain('CreateOrder')
    expect(text).toContain('Process')
    expect(text).not.toContain('order-worker')
  })
})
