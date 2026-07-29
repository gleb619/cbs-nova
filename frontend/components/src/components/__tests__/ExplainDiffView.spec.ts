import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExplainDiffView from '../runner/ExplainDiffView.vue'

function mountExplainDiffView(props: Record<string, unknown>) {
  return mount(ExplainDiffView, { props })
}

describe('ExplainDiffView', () => {
  it('shows placeholder when runOutput is undefined', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { status: 'ok' },
      runOutput: undefined,
    })

    expect(wrapper.text()).toContain('No run result to compare.')
    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)
  })

  it('renders two JSON panes when both outputs are present', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { mode: 'explain', value: 1 },
      runOutput: { mode: 'run', value: 2 },
    })

    const explainPane = wrapper.find('[data-testid="explain-pane"]')
    const runPane = wrapper.find('[data-testid="run-pane"]')

    expect(explainPane.exists()).toBe(true)
    expect(runPane.exists()).toBe(true)
    expect(explainPane.text()).toContain('"mode": "explain"')
    expect(runPane.text()).toContain('"mode": "run"')
  })

  it('switches between split and unified layout via toggle button', async () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 2 },
    })

    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)

    const toggleButton = wrapper.find('button')
    expect(toggleButton.text()).toBe('Unified')
    await toggleButton.trigger('click')

    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(true)

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('[data-testid="split-layout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(false)
  })

  it('colours unified diff lines by kind', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: { a: 1 },
      runOutput: { a: 1, b: 2 },
      layout: 'unified',
    })

    expect(wrapper.find('[data-testid="unified-layout"]').exists()).toBe(true)

    const lines = wrapper.findAll('[data-testid="diff-line"]')
    const classes = lines.map((line) => line.classes())

    expect(classes.some((c) => c.includes('bg-yellow-50'))).toBe(true)
    expect(classes.some((c) => c.includes('bg-green-50'))).toBe(true)
    expect(classes.some((c) => c.includes('border-transparent'))).toBe(true)
  })

  it('renders an empty explain pane without throwing when explainOutput is null', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: null,
      runOutput: { a: 1 },
    })

    expect(wrapper.find('[data-testid="explain-pane"]').text()).toBe('')
  })

  it('renders an empty explain pane without throwing when explainOutput is undefined', () => {
    const wrapper = mountExplainDiffView({
      explainOutput: undefined,
      runOutput: { a: 1 },
    })

    expect(wrapper.find('[data-testid="explain-pane"]').text()).toBe('')
  })
})
