import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExplainOutput from '../runner/ExplainOutput.vue'

describe('ExplainOutput', () => {
  it('renders the root data-testid and a placeholder when no content is provided', () => {
    const wrapper = mount(ExplainOutput, {
      props: { description: undefined, mermaidDiagram: undefined },
    })

    expect(wrapper.find('[data-testid="explain-output"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No explanation available.')
  })

  it('renders the description section when provided', () => {
    const wrapper = mount(ExplainOutput, {
      props: { description: 'Line one' + '\n' + 'Line two', mermaidDiagram: undefined },
    })

    expect(wrapper.find('[data-testid="explain-output-description"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Line one')
    expect(wrapper.text()).toContain('Line two')
  })

  it('renders the mermaid diagram section when provided', () => {
    const wrapper = mount(ExplainOutput, {
      props: { description: undefined, mermaidDiagram: 'graph TD' },
    })

    expect(wrapper.find('[data-testid="explain-output-mermaid"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('graph TD')
  })

  it('renders both sections when both props are provided', () => {
    const wrapper = mount(ExplainOutput, {
      props: { description: 'desc', mermaidDiagram: 'diagram' },
    })

    expect(wrapper.find('[data-testid="explain-output-description"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="explain-output-mermaid"]').exists()).toBe(true)
  })
})
