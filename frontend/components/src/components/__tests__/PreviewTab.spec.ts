import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import PreviewTab from '../dsl/PreviewTab.vue'

function output(overrides: Partial<RunnerOutput> = {}): RunnerOutput {
  return { result: { ok: true }, ...overrides }
}

describe('PreviewTab', () => {
  it('emits run when the Run preview button is clicked', async () => {
    const wrapper = mount(PreviewTab, { props: { output: null, status: 'idle' } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('run')).toBeTruthy()
    expect(wrapper.emitted('run')).toHaveLength(1)
  })

  it.each([
    ['loading', 'Loading…'],
    ['success', 'Done'],
    ['failed', 'Failed'],
  ])('shows %s status text', (status, text) => {
    const wrapper = mount(PreviewTab, {
      props: { output: null, status: status as RunnerStatus },
    })

    expect(wrapper.text()).toContain(text)
  })

  it('renders output errors when present and hides ResultTab', () => {
    const wrapper = mount(PreviewTab, {
      props: {
        output: output({ errors: [{ message: 'preview failed' }] }),
        status: 'failed',
      },
    })

    expect(wrapper.text()).toContain('preview failed')
    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(false)
  })

  it('renders ResultTab with the result when there are no errors', () => {
    const wrapper = mount(PreviewTab, {
      props: {
        output: output({ result: { ok: true } }),
        status: 'success',
      },
    })

    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('ok')
  })

  it('renders an empty ResultTab when output is null', () => {
    const wrapper = mount(PreviewTab, { props: { output: null, status: 'idle' } })

    expect(wrapper.find('[data-testid="runner-result-tab"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No result yet.')
  })
})
