import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { CallNode, RunnerOutput } from '../../types/runner'
import ASTDiffNode from '../runner/ASTDiffNode.vue'
import DiffLine from '../runner/DiffLine.vue'
import MetricsDiffTable from '../runner/MetricsDiffTable.vue'
import PreviewDiffView from '../runner/PreviewDiffView.vue'

function makeNode(overrides: Partial<CallNode> = {}): CallNode {
  return {
    name: 'Root',
    kind: 'PROCESS',
    success: true,
    children: [],
    externalCalls: [],
    ...overrides,
  }
}

function makeOutput(overrides: Partial<RunnerOutput> = {}): RunnerOutput {
  return {
    result: { ok: true },
    ...overrides,
  }
}

function mountDiff(props: Record<string, unknown>) {
  return mount(PreviewDiffView, {
    props,
    global: {
      components: { DiffLine, ASTDiffNode, MetricsDiffTable },
    },
  })
}

describe('PreviewDiffView', () => {
  it('renders all four diff tabs by default', () => {
    const baseline = makeOutput({ result: { value: 'baseline' } })
    const current = makeOutput({ result: { value: 'current' } })

    const wrapper = mountDiff({ baseline, current })
    const buttons = wrapper.findAll('[data-testid^="preview-diff-tab-"]')

    expect(buttons.map((b) => b.text())).toEqual([
      'Output Diff',
      'AST Diff',
      'External Calls Diff',
      'Metrics Diff',
    ])
  })

  it('shows a placeholder note when the baseline is missing', () => {
    const current = makeOutput({ result: { value: 'current' } })

    const wrapper = mountDiff({ baseline: null, current })
    expect(wrapper.text()).toContain('baseline is missing')
  })

  it('switches the active tab and shows the matching panel on click', async () => {
    const baseline = makeOutput({
      result: { a: 1 },
      astTree: makeNode({ name: 'BaselineRoot' }),
      metrics: { executionDurationMs: 100 },
    })
    const current = makeOutput({
      result: { a: 2 },
      astTree: makeNode({ name: 'CurrentRoot' }),
      metrics: { executionDurationMs: 200 },
    })

    const wrapper = mountDiff({ baseline, current })

    expect(wrapper.find('[data-testid="preview-diff-output-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="preview-diff-ast-panel"]').exists()).toBe(false)

    const astButton = wrapper.find('[data-testid="preview-diff-tab-ast"]')
    await astButton.trigger('click')
    expect(wrapper.find('[data-testid="preview-diff-ast-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="preview-diff-output-panel"]').exists()).toBe(false)

    const metricsButton = wrapper.find('[data-testid="preview-diff-tab-metrics"]')
    await metricsButton.trigger('click')
    expect(wrapper.find('[data-testid="preview-diff-metrics-panel"]').exists()).toBe(true)

    const callsButton = wrapper.find('[data-testid="preview-diff-tab-calls"]')
    await callsButton.trigger('click')
    expect(wrapper.find('[data-testid="preview-diff-calls-panel"]').exists()).toBe(true)
  })

  it('colours diff lines by kind (added / removed / unchanged)', () => {
    const baseline = makeOutput({ result: { keep: 1, drop: 'x' } })
    const current = makeOutput({ result: { keep: 1, add: 'y' } })

    const wrapper = mountDiff({ baseline, current })

    const lines = wrapper.findAll('[data-testid="preview-diff-line"]')
    expect(lines.length).toBeGreaterThan(0)

    const kinds = lines.map((line) => line.attributes('data-kind'))
    expect(kinds).toContain('lhs-only')
    expect(kinds).toContain('rhs-only')
    expect(kinds).toContain('same')

    const lhsLine = lines.find((line) => line.attributes('data-kind') === 'lhs-only')
    const rhsLine = lines.find((line) => line.attributes('data-kind') === 'rhs-only')
    const sameLine = lines.find((line) => line.attributes('data-kind') === 'same')

    expect(lhsLine?.classes()).toContain('bg-red-50')
    expect(lhsLine?.classes()).toContain('border-red-400')
    expect(rhsLine?.classes()).toContain('bg-green-50')
    expect(rhsLine?.classes()).toContain('border-green-500')
    expect(sameLine?.classes()).toContain('border-transparent')
  })

  it('renders the AST diff tree with status icons', async () => {
    const baseline = makeOutput({
      astTree: makeNode({
        name: 'Root',
        children: [
          makeNode({ name: 'Keep', kind: 'TRANSACTION', success: true }),
          makeNode({ name: 'Drop', kind: 'HELPER' }),
        ],
      }),
    })
    const current = makeOutput({
      astTree: makeNode({
        name: 'Root',
        children: [
          makeNode({ name: 'Keep', kind: 'TRANSACTION', success: false }),
          makeNode({ name: 'Add', kind: 'FUNCTION' }),
        ],
      }),
    })

    const wrapper = mountDiff({ baseline, current })

    const astButton = wrapper.find('[data-testid="preview-diff-tab-ast"]')
    await astButton.trigger('click')

    const nodes = wrapper.findAll('[data-testid="ast-diff-node"]')
    expect(nodes.length).toBeGreaterThan(0)

    const statuses = nodes.map((node) => node.attributes('data-status'))
    expect(statuses).toContain('added')
    expect(statuses).toContain('removed')
    expect(statuses).toContain('modified')

    const statusBadges = wrapper.findAll('[data-testid="ast-diff-status"]')
    expect(statusBadges.length).toBeGreaterThan(0)
  })

  it('renders the metrics diff table with deltas and percentage changes', async () => {
    const baseline = makeOutput({ metrics: { executionDurationMs: 100, memoryUsedBytes: 1024 } })
    const current = makeOutput({ metrics: { executionDurationMs: 150, memoryUsedBytes: 512 } })

    const wrapper = mountDiff({ baseline, current })
    const metricsButton = wrapper.find('[data-testid="preview-diff-tab-metrics"]')
    await metricsButton.trigger('click')

    const table = wrapper.find('[data-testid="metrics-diff-table"]')
    expect(table.exists()).toBe(true)
    expect(table.text()).toContain('Execution duration (ms)')
    expect(table.text()).toContain('Memory used (bytes)')

    // Increase in execution duration is a regression → red.
    const durationRow = wrapper.find('[data-row-key="executionDurationMs"]')
    expect(durationRow.exists()).toBe(true)
    expect(durationRow.text()).toContain('+50 ms')
    expect(durationRow.text()).toContain('+50%')
    expect(durationRow.findAll('td').some((td) => td.classes().includes('text-red-700'))).toBe(true)

    // Decrease in memory is an improvement → green.
    const memoryRow = wrapper.find('[data-row-key="memoryUsedBytes"]')
    expect(memoryRow.exists()).toBe(true)
    expect(memoryRow.text()).toContain('−512')
    expect(memoryRow.text()).toContain('−50%')
    expect(memoryRow.findAll('td').some((td) => td.classes().includes('text-green-700'))).toBe(true)
  })

  it('renders the external-calls diff with one row per call status', async () => {
    const baseline = makeOutput({
      astTree: makeNode({
        externalCalls: [
          { type: 'http', target: 'api.example.com', operation: 'GET', timestamp: 1 },
        ],
        children: [
          makeNode({
            externalCalls: [
              { type: 'database', target: 'orders', operation: 'select', timestamp: 2 },
            ],
          }),
        ],
      }),
    })
    const current = makeOutput({
      astTree: makeNode({
        externalCalls: [
          { type: 'http', target: 'api.example.com', operation: 'GET', timestamp: 3 },
        ],
        children: [
          makeNode({
            externalCalls: [
              { type: 'database', target: 'audit-log', operation: 'insert', timestamp: 4 },
            ],
          }),
        ],
      }),
    })

    const wrapper = mountDiff({ baseline, current })
    const callsButton = wrapper.find('[data-testid="preview-diff-tab-calls"]')
    await callsButton.trigger('click')

    const rows = wrapper.findAll('[data-testid="preview-diff-call-row"]')
    expect(rows.length).toBe(3)

    const statuses = rows.map((row) => row.attributes('data-status')).sort()
    expect(statuses).toEqual(['added', 'removed', 'same'])
  })

  it('shows an empty-state message in the AST panel when both trees are missing', async () => {
    const wrapper = mountDiff({ baseline: makeOutput(), current: makeOutput() })
    const astButton = wrapper.find('[data-testid="preview-diff-tab-ast"]')
    await astButton.trigger('click')

    expect(wrapper.text()).toContain('No AST tree available to compare.')
  })
})
