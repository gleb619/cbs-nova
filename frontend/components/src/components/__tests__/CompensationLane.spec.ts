import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { TraceStep } from '../../types/execution'
import CompensationLane from '../executions/CompensationLane.vue'
import ExecutionsTraceNode from '../executions/TraceNode.vue'
import ExecutionsStatusBadge from '../executions/StatusBadge.vue'

function step(overrides: Partial<TraceStep> = {}): TraceStep {
  return {
    id: 's1',
    stepType: 'Transaction',
    name: 'Compensation',
    status: 'Compensated',
    isCompensation: true,
    ...overrides,
  }
}

function mountCompensationLane(props: Record<string, unknown>) {
  return mount(CompensationLane, {
    props,
    global: { components: { ExecutionsTraceNode, ExecutionsStatusBadge } },
  })
}

describe('CompensationLane', () => {
  it('does not render when steps is empty', () => {
    const wrapper = mountCompensationLane({ steps: [] })

    expect(wrapper.find('[data-testid="compensation-lane"]').exists()).toBe(false)
  })

  it('renders the lane, header and a TraceNode for each step', () => {
    const wrapper = mountCompensationLane({ steps: [step({ id: 'a' }), step({ id: 'b' })] })

    expect(wrapper.find('[data-testid="compensation-lane"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Compensation')
    expect(wrapper.findAll('[data-testid="execution-trace-node-a"], [data-testid="execution-trace-node-b"]')).toHaveLength(2)
  })
})
