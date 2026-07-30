import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import type { CallNode, RunnerOutput } from '../../types/runner'
import { useDiffLines } from '../useDiffLines'
import { usePreviewDiff } from '../usePreviewDiff'

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

describe('usePreviewDiff', () => {
  it('diffLines delegates to useDiffLines for the result field', () => {
    const baseline = makeOutput({ result: { value: 'old' } })
    const current = makeOutput({ result: { value: 'new' } })

    const { diffLines } = usePreviewDiff(baseline, current)

    // usePreviewDiff uses JSON.stringify(value, null, 2) for both sides; the
    // shapes of these JSON strings are stable enough to compare against the
    // standalone useDiffLines result.
    const expected = useDiffLines(JSON.stringify(baseline.result, null, 2), JSON.stringify(current.result, null, 2)).value

    expect(diffLines.value.map((line) => line.kind)).toEqual(expected.map((line) => line.kind))
    expect(diffLines.value.length).toBe(expected.length)
  })

  it('marks every diff line as "same" when the result is identical', () => {
    const shared = { hello: 'world' }
    const { diffLines } = usePreviewDiff(makeOutput({ result: shared }), makeOutput({ result: shared }))

    expect(diffLines.value.every((line) => line.kind === 'same')).toBe(true)
  })

  it('astDiff identifies added, removed, and modified nodes', () => {
    const baseline = makeOutput({
      astTree: makeNode({
        name: 'Root',
        children: [
          makeNode({ name: 'Keep', kind: 'TRANSACTION', success: true, output: 'old-output' }),
          makeNode({ name: 'Drop', kind: 'HELPER' }),
        ],
      }),
    })
    const current = makeOutput({
      astTree: makeNode({
        name: 'Root',
        children: [
          makeNode({ name: 'Keep', kind: 'TRANSACTION', success: false, output: 'new-output' }),
          makeNode({ name: 'Add', kind: 'FUNCTION' }),
        ],
      }),
    })

    const { astDiff } = usePreviewDiff(baseline, current)

    expect(astDiff.value).not.toBeNull()
    const root = astDiff.value
    expect(root?.status).toBe('same')

    const byName = Object.fromEntries((root?.children ?? []).map((c) => [c.name, c.status]))
    expect(byName.Keep).toBe('modified')
    expect(byName.Drop).toBe('removed')
    expect(byName.Add).toBe('added')

    const keepNode = root?.children.find((c) => c.name === 'Keep')
    expect(keepNode?.propertyChanges.map((p) => p.key).sort()).toEqual(['output', 'success'])
  })

  it('returns null from astDiff when both sides are missing the tree', () => {
    const { astDiff } = usePreviewDiff(makeOutput(), makeOutput())
    expect(astDiff.value).toBeNull()
  })

  it('callDiff compares flattened external call lists by source/target/operation', () => {
    const baseline = makeOutput({
      astTree: makeNode({
        externalCalls: [
          { type: 'http', target: 'api.example.com', operation: 'GET', timestamp: 1 },
          { type: 'database', target: 'orders', operation: 'select', timestamp: 2 },
        ],
        children: [
          makeNode({
            externalCalls: [
              { type: 'mq', target: 'events-bus', operation: 'publish', timestamp: 3 },
            ],
          }),
        ],
      }),
    })
    const current = makeOutput({
      astTree: makeNode({
        externalCalls: [{ type: 'http', target: 'api.example.com', operation: 'GET', timestamp: 4 }],
        children: [
          makeNode({
            externalCalls: [{ type: 'mq', target: 'events-bus', operation: 'publish', timestamp: 5 }],
          }),
          makeNode({
            externalCalls: [{ type: 'database', target: 'audit-log', operation: 'insert', timestamp: 6 }],
          }),
        ],
      }),
    })

    const { callDiff } = usePreviewDiff(baseline, current)

    const byStatus = callDiff.value.reduce<Record<string, number>>((acc, row) => {
      acc[row.status] = (acc[row.status] ?? 0) + 1
      return acc
    }, {})

    expect(byStatus.same).toBe(2) // api GET + mq publish
    expect(byStatus.added).toBe(1) // audit-log insert
    expect(byStatus.removed).toBe(1) // orders select
  })

  it('metricsDiff calculates percentage changes and tolerates missing baseline', () => {
    const baseline = makeOutput({
      metrics: {
        executionDurationMs: 100,
        memoryUsedBytes: 1024,
        callCounts: { PROCESS: 2, TRANSACTION: 1 },
        externalCallCounts: { 'api.example.com': 3 },
      },
    })
    const current = makeOutput({
      metrics: {
        executionDurationMs: 150,
        memoryUsedBytes: 512,
        callCounts: { PROCESS: 2, TRANSACTION: 3 },
        externalCallCounts: { 'api.example.com': 3, 'audit-log': 1 },
      },
    })

    const { metricsDiff } = usePreviewDiff(baseline, current)
    const byKey = Object.fromEntries(metricsDiff.value.map((row) => [row.key, row]))

    expect(byKey.executionDurationMs?.delta).toBe(50)
    expect(byKey.executionDurationMs?.percentChange).toBe(50)
    expect(byKey.memoryUsedBytes?.delta).toBe(-512)
    expect(byKey.memoryUsedBytes?.percentChange).toBe(-50)

    // Same value on both sides → 0 delta, percentChange is also 0 (not null).
    expect(byKey['callCounts.PROCESS']?.delta).toBe(0)
    expect(byKey['callCounts.PROCESS']?.percentChange).toBe(0)

    // Added on the current side → baseline null, no delta / percentChange.
    expect(byKey['externalCallCounts.audit-log']?.baseline).toBeNull()
    expect(byKey['externalCallCounts.audit-log']?.current).toBe(1)
    expect(byKey['externalCallCounts.audit-log']?.delta).toBeNull()
    expect(byKey['externalCallCounts.audit-log']?.percentChange).toBeNull()
  })

  it('metricsDiff handles a baseline of zero without dividing by zero', () => {
    const baseline = makeOutput({ metrics: { executionDurationMs: 0 } })
    const current = makeOutput({ metrics: { executionDurationMs: 5 } })

    const { metricsDiff } = usePreviewDiff(baseline, current)
    const row = metricsDiff.value.find((r) => r.key === 'executionDurationMs')

    expect(row?.delta).toBe(5)
    expect(row?.percentChange).toBeNull() // intentionally null — avoid Inf
  })

  it('metricsDiff handles a completely missing baseline gracefully', () => {
    const baseline = makeOutput()
    const current = makeOutput({ metrics: { executionDurationMs: 10 } })

    const { metricsDiff } = usePreviewDiff(baseline, current)

    expect(metricsDiff.value.length).toBeGreaterThan(0)
    for (const row of metricsDiff.value) {
      expect(row.baseline).toBeNull()
      expect(row.percentChange).toBeNull()
    }
  })

  it('accepts refs for baseline and current and stays reactive', () => {
    const baseline = ref<RunnerOutput | null>(makeOutput({ result: { a: 1 } }))
    const current = ref<RunnerOutput | null>(makeOutput({ result: { a: 1 } }))

    const { diffLines } = usePreviewDiff(baseline, current)
    expect(diffLines.value.every((line) => line.kind === 'same')).toBe(true)

    baseline.value = makeOutput({ result: { a: 2 } })
    expect(diffLines.value.some((line) => line.kind !== 'same')).toBe(true)
  })
})
