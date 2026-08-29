import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import ExplainTab from '../dsl/ExplainTab.vue'

function output(overrides: Partial<RunnerOutput> = {}): RunnerOutput {
  return { description: 'desc', result: { ok: true }, ...overrides }
}

describe('ExplainTab', () => {
  it('emits run when the Run explain button is clicked', async () => {
    const wrapper = mount(ExplainTab, { props: { output: null, status: 'idle' } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('run')).toBeTruthy()
    expect(wrapper.emitted('run')).toHaveLength(1)
  })

  it.each([
    ['loading', 'Loading…'],
    ['success', 'Done'],
    ['failed', 'Failed'],
  ])('shows %s status text', (status, text) => {
    const wrapper = mount(ExplainTab, {
      props: { output: null, status: status as RunnerStatus },
    })

    expect(wrapper.text()).toContain(text)
  })

  it('renders output errors when present', () => {
    const wrapper = mount(ExplainTab, {
      props: {
        output: output({ errors: [{ message: 'explainer failed' }] }),
        status: 'failed',
      },
    })

    expect(wrapper.text()).toContain('explainer failed')
  })

  it('does not render an error block when output is null', () => {
    const wrapper = mount(ExplainTab, { props: { output: null, status: 'idle' } })

    expect(wrapper.text()).not.toContain('explainer failed')
  })

  it('renders ExplainOutput and ResultTab children when output is provided', () => {
    const wrapper = mount(ExplainTab, {
      props: {
        output: output({ description: 'd', mermaidDiagram: 'g' }),
        status: 'success',
      },
    })

    expect(wrapper.find('[data-testid="explain-output"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
  })
})
