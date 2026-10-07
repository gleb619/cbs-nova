import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetMermaidRenderForTests } from '../../composables/useMermaidRender'
import type { RunnerOutput } from '../../types/runner'
import HierarchyReportView from '../dsl/HierarchyReportView.vue'

const { mermaidRender } = vi.hoisted(() => ({ mermaidRender: vi.fn() }))

vi.mock('mermaid', () => ({
  default: {
    initialize: vi.fn(),
    render: mermaidRender,
  },
}))

function mountView(output: RunnerOutput, showRaw = false) {
  return mount(HierarchyReportView, {
    props: { output, showRaw },
  })
}

describe('HierarchyReportView', () => {
  beforeEach(() => {
    mermaidRender.mockReset()
    __resetMermaidRenderForTests()
  })

  it('renders summary chips for a successful report', () => {
    const output: RunnerOutput = {
      result: {
        name: 'ProcessA',
        description: 'Process: ProcessA',
        hasCompensation: true,
        callCounts: { http: 2, database: 3 },
        children: [],
        errors: [],
      },
    }
    const wrapper = mountView(output)

    expect(wrapper.find('[data-testid="hierarchy-summary-name"]').text()).toBe('ProcessA')
    expect(wrapper.find('[data-testid="hierarchy-summary-description"]').text()).toBe(
      'Process: ProcessA',
    )
    expect(wrapper.find('[data-testid="hierarchy-summary-compensation"]').text()).toBe(
      'compensation',
    )
    expect(wrapper.find('[data-testid="hierarchy-summary-calls"]').text()).toBe('5 total calls')
    expect(wrapper.find('[data-testid="hierarchy-summary-errors"]').exists()).toBe(false)
  })

  it('renders the mermaid diagram', async () => {
    mermaidRender.mockResolvedValue({ svg: '<svg data-mock="diagram"></svg>' })
    const output: RunnerOutput = {
      result: {
        name: 'ProcessA',
        mermaidDiagram: 'graph TD\n  A-->B',
        callCounts: {},
      },
    }
    const wrapper = mountView(output)
    await flushPromises()

    expect(wrapper.find('[data-testid="mermaid-diagram"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mermaid-diagram"]').find('svg').exists()).toBe(true)
  })

  it('shows the diagram source when mermaid rendering fails', async () => {
    mermaidRender.mockRejectedValue(new Error('bad diagram'))
    const output: RunnerOutput = {
      result: {
        name: 'ProcessA',
        mermaidDiagram: 'graph TD\n  broken',
        callCounts: {},
      },
    }
    const wrapper = mountView(output)
    await flushPromises()

    const source = wrapper.find('[data-testid="mermaid-diagram-source"]')
    expect(source.exists()).toBe(true)
    expect(source.text()).toContain('graph TD')
  })

  it('renders nested children and toggles them', async () => {
    const output: RunnerOutput = {
      result: {
        name: 'Root',
        children: [
          {
            name: 'Child1',
            description: 'Transaction: Child1',
            callCounts: { database: 1 },
            children: [{ name: 'Grandchild', callCounts: {} }],
          },
        ],
        callCounts: {},
      },
    }
    const wrapper = mountView(output)

    const nodes = wrapper.findAll('[data-testid="hierarchy-report-node"]')
    expect(nodes).toHaveLength(2)
    expect(wrapper.text()).toContain('Root')
    expect(wrapper.text()).toContain('Child1')
    expect(wrapper.text()).toContain('Grandchild')

    const childToggle = wrapper.findAll('[data-testid="hierarchy-report-node-toggle"]').at(0)
    await childToggle?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('Grandchild')
  })

  it('renders external calls and execution trace as collapsible lists', () => {
    const output: RunnerOutput = {
      result: {
        name: 'Root',
        externalCalls: [{ type: 'http', target: 'api', operation: 'POST /pay' }],
        executionTrace: ['started Root', 'called transaction: Tx'],
        callCounts: {},
      },
    }
    const wrapper = mountView(output)

    expect(wrapper.find('[data-testid="hierarchy-external-calls"]').text()).toContain(
      'External calls (1)',
    )
    expect(wrapper.find('[data-testid="hierarchy-execution-trace"]').text()).toContain(
      'Execution trace (2)',
    )
    expect(wrapper.find('[data-testid="hierarchy-external-call"]').text()).toContain('[http]')
    expect(wrapper.find('[data-testid="hierarchy-execution-trace-step"]').text()).toContain(
      'started Root',
    )
  })

  it('switches to raw JSON when showRaw is true', () => {
    const output: RunnerOutput = {
      result: {
        name: 'ProcessA',
        children: [{ name: 'Child1' }],
        callCounts: {},
      },
    }
    const wrapper = mountView(output, true)

    expect(wrapper.find('[data-testid="hierarchy-result-text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hierarchy-result-text"]').text()).toContain('ProcessA')
    expect(wrapper.find('[data-testid="hierarchy-summary"]').exists()).toBe(false)
  })

  it('falls back to raw JSON when the report lacks visual fields', () => {
    const output: RunnerOutput = {
      result: { name: 'ProcessA', callCounts: {} },
    }
    const wrapper = mountView(output)

    expect(wrapper.find('[data-testid="hierarchy-result-text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hierarchy-result-text"]').text()).toContain('ProcessA')
  })

  it('falls back to raw JSON and renders the error list on failure', () => {
    const output: RunnerOutput = {
      result: { name: 'ProcessA' },
      errors: [{ message: 'boom', code: 'ERR' }],
    }
    const wrapper = mountView(output)

    expect(wrapper.find('[data-testid="hierarchy-error-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hierarchy-result-text"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hierarchy-result-text"]').text()).toContain('boom')
  })
})
