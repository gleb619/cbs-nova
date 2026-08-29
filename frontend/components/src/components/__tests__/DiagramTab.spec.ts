import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DiagramTab from '../executions/DiagramTab.vue'

describe('DiagramTab', () => {
  it('renders the root data-testid and a placeholder when no diagram is provided', () => {
    const wrapper = mount(DiagramTab, { props: { diagram: undefined } })

    expect(wrapper.find('[data-testid="execution-diagram-tab"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No diagram available for this execution.')
  })

  it('renders the diagram in a scrollable pre/code block when provided', () => {
    const wrapper = mount(DiagramTab, {
      props: { diagram: 'graph TD' + '\n' + '  A-->B' },
    })

    const pre = wrapper.find('pre')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toContain('graph TD')
    expect(pre.text()).toContain('A-->B')
  })
})
