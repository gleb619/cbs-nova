import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { TraceStep } from '../../types/execution'
import TraceNode from '../executions/TraceNode.vue'
import ExecutionsStatusBadge from '../executions/StatusBadge.vue'

function step(overrides: Partial<TraceStep> = {}): TraceStep {
  return { id: 's1', stepType: 'Process', name: 'Root', status: 'Completed', ...overrides }
}

function mountTraceNode(props: Record<string, unknown>) {
  return mount(TraceNode, {
    props,
    global: { components: { ExecutionsStatusBadge } },
  })
}

describe('TraceNode', () => {
  it('renders the dynamic testid, name, step type badge, status badge and duration', () => {
    const wrapper = mountTraceNode({ step: step({ duration: 1234 }), depth: 0 })

    expect(wrapper.find('[data-testid="execution-trace-node-s1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-trace-node-step-type-s1"]').text()).toBe('Process')
    expect(wrapper.find('[data-testid="execution-trace-node-duration-s1"]').text()).toBe('1.23s')
    expect(wrapper.text()).toContain('Root')
    expect(wrapper.find('[data-testid="status-badge"]').exists()).toBe(true)
  })

  it('indents by depth via margin-left style', () => {
    const wrapper = mountTraceNode({ step: step(), depth: 2 })

    expect(wrapper.find('[data-testid="execution-trace-node-s1"]').attributes('style')).toContain(
      '2.5rem',
    )
  })

  it('renders compensation styling when isCompensation is true', () => {
    const wrapper = mountTraceNode({ step: step({ isCompensation: true }), depth: 0 })

    const root = wrapper.find('[data-testid="execution-trace-node-s1"]')
    expect(root.classes()).toContain('bg-orange-50')
    expect(root.classes()).toContain('border-dashed')
    expect(root.classes()).toContain('border-orange-300')
    expect(
      wrapper.find('[data-testid="execution-trace-node-step-type-s1"]').classes(),
    ).toContain('text-orange-700')
  })

  it('renders non-compensation styling', () => {
    const wrapper = mountTraceNode({ step: step(), depth: 0 })

    const root = wrapper.find('[data-testid="execution-trace-node-s1"]')
    expect(root.classes()).toContain('hover:bg-gray-50')
    expect(wrapper.find('[data-testid="execution-trace-node-step-type-s1"]').classes()).toContain(
      'bg-gray-100',
    )
  })

  it('maps icons for known step types', () => {
    const cases: { type: TraceStep['stepType']; icon: string }[] = [
      { type: 'Process', icon: '🔷' },
      { type: 'Transaction', icon: '🔶' },
      { type: 'Function', icon: '🔹' },
      { type: 'Helper', icon: '🛠' },
    ]

    for (const { type, icon } of cases) {
      const wrapper = mountTraceNode({
        step: step({ id: type, stepType: type }),
        depth: 0,
      })
      expect(wrapper.text()).toContain(icon)
    }
  })

  it('formats sub-second durations as milliseconds', () => {
    const wrapper = mountTraceNode({ step: step({ duration: 500 }), depth: 0 })

    expect(wrapper.find('[data-testid="execution-trace-node-duration-s1"]').text()).toBe('500ms')
  })

  it('shows an em dash when duration is missing', () => {
    const wrapper = mountTraceNode({ step: step({ duration: undefined }), depth: 0 })

    expect(wrapper.find('[data-testid="execution-trace-node-duration-s1"]').text()).toBe('—')
  })
})
