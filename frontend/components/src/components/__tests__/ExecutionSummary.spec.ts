import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExecutionDetail } from '../../types/execution'
import ExecutionSummary from '../executions/ExecutionSummary.vue'
import ExecutionsStatusBadge from '../executions/StatusBadge.vue'

function execution(overrides: Partial<ExecutionDetail> = {}): ExecutionDetail {
  return {
    id: 'e1',
    entity: 'TestEntity',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Completed',
    startedAt: '2025-06-15T10:30:00Z',
    duration: 1250,
    correlationId: 'corr-1',
    workflowId: 'wf-1',
    ...overrides,
  }
}

function mountSummary(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(ExecutionSummary, {
    props,
    slots,
    global: { components: { ExecutionsStatusBadge } },
  })
}

describe('ExecutionSummary', () => {
  it('renders the root data-testid, entity, entityType, mode and status badge', () => {
    const wrapper = mountSummary({ execution: execution() })

    expect(wrapper.find('[data-testid="execution-summary"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('TestEntity')
    expect(wrapper.text()).toContain('Process')
    expect(wrapper.text()).toContain('RUN')
    expect(wrapper.find('[data-testid="status-badge"]').exists()).toBe(true)
  })

  it('formats startedAt and duration in seconds', () => {
    const wrapper = mountSummary({
      execution: execution({
        startedAt: '2025-06-15T10:30:00Z',
        duration: 1250,
      }),
    })

    expect(wrapper.text()).toContain(new Date('2025-06-15T10:30:00Z').toLocaleString())
    expect(wrapper.text()).toContain('Duration: 1.25s')
  })

  it('renders correlation and workflow ids', () => {
    const wrapper = mountSummary({
      execution: execution({ correlationId: 'corr-1', workflowId: 'wf-1' }),
    })

    expect(wrapper.find('[data-testid="execution-summary-correlation"]').text()).toContain('corr-1')
    expect(wrapper.find('[data-testid="execution-summary-workflow"]').text()).toContain('wf-1')
  })

  it('falls back to em dash for missing optional fields', () => {
    const wrapper = mountSummary({
      execution: execution({
        startedAt: undefined,
        duration: undefined,
        correlationId: undefined,
        workflowId: undefined,
      }),
    })

    expect(wrapper.text()).toContain('Started: —')
    expect(wrapper.text()).toContain('Duration: —')
    expect(wrapper.find('[data-testid="execution-summary-correlation"]').text()).toContain('—')
    expect(wrapper.find('[data-testid="execution-summary-workflow"]').text()).toContain('—')
  })

  it('formats durations under one second as milliseconds', () => {
    const wrapper = mountSummary({ execution: execution({ duration: 800 }) })

    expect(wrapper.text()).toContain('Duration: 800ms')
  })

  it('renders the actions slot content', () => {
    const wrapper = mountSummary(
      { execution: execution() },
      { actions: '<button data-testid="action-slot-btn">Action</button>' },
    )

    expect(wrapper.find('[data-testid="action-slot-btn"]').exists()).toBe(true)
  })
})
