import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { TraceStep } from '../../types/execution'
import ExecutionTrace from '../executions/ExecutionTrace.vue'
import ExecutionsTraceNode from '../executions/TraceNode.vue'
import ExecutionsStatusBadge from '../executions/StatusBadge.vue'

function step(overrides: Partial<TraceStep> = {}): TraceStep {
  return { id: 's1', stepType: 'Process', name: 'Root', status: 'Completed', ...overrides }
}

function mountExecutionTrace(props: Record<string, unknown>) {
  return mount(ExecutionTrace, {
    props,
    global: { components: { ExecutionsTraceNode, ExecutionsStatusBadge } },
  })
}

describe('ExecutionTrace', () => {
  it('renders the root data-testid and an empty placeholder when steps are empty', () => {
    const wrapper = mountExecutionTrace({ steps: [] })

    expect(wrapper.find('[data-testid="execution-trace"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="execution-trace-empty"]').text()).toBe(
      'No trace steps recorded.',
    )
  })

  it('renders flat steps as root-level TraceNodes', () => {
    const wrapper = mountExecutionTrace({
      steps: [step({ id: 'a', name: 'A' }), step({ id: 'b', name: 'B' })],
    })

    expect(wrapper.find('[data-testid="execution-trace-tree"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="execution-trace-node-a"], [data-testid="execution-trace-node-b"]')).toHaveLength(2)
    expect(wrapper.find('[data-testid="execution-trace-node-a"]').text()).toContain('A')
    expect(wrapper.find('[data-testid="execution-trace-node-b"]').text()).toContain('B')
  })

  it('renders nested children with increasing depth', () => {
    const wrapper = mountExecutionTrace({
      steps: [
        step({ id: 'root', name: 'Root' }),
        step({ id: 'child', name: 'Child', parentId: 'root' }),
        step({ id: 'grandchild', name: 'Grandchild', parentId: 'child' }),
      ],
    })

    expect(wrapper.findAll('[data-testid="execution-trace-node-root"], [data-testid="execution-trace-node-child"], [data-testid="execution-trace-node-grandchild"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="execution-trace-node-root"]').attributes('style')).toContain(
      '0rem',
    )
    expect(wrapper.find('[data-testid="execution-trace-node-child"]').attributes('style')).toContain(
      '1.25rem',
    )
    expect(
      wrapper.find('[data-testid="execution-trace-node-grandchild"]').attributes('style'),
    ).toContain('2.5rem')
  })

  it('treats steps with an unknown parentId as roots', () => {
    const wrapper = mountExecutionTrace({
      steps: [step({ id: 'a', name: 'A' }), step({ id: 'b', name: 'B', parentId: 'missing' })],
    })

    expect(wrapper.findAll('[data-testid="execution-trace-node-a"], [data-testid="execution-trace-node-b"]')).toHaveLength(2)
  })
})
