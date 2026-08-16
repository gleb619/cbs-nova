import { type ComputedRef, computed, type Ref, unref } from 'vue'
import type { CallNode, PreviewMetricsSnapshot, RunnerOutput } from '../types/runner'
import { type DiffLine, useDiffLines } from './useDiffLines'

/**
 * Status of a single AST tree node when compared between two preview runs.
 *
 * - `same` — node exists in both runs with identical structural identity (path + kind + name).
 * - `added` — present only in the current run (baseline does not reach this node).
 * - `removed` — present only in the baseline run (current run does not reach this node).
 * - `modified` — node identity matches but at least one scalar property (`input`, `output`,
 *   `success`) differs between the two runs.
 */
export type ASTDiffStatus = 'same' | 'added' | 'removed' | 'modified'

export interface ASTDiffNode {
  status: ASTDiffStatus
  name: string
  kind: CallNode['kind']
  success: boolean
  children: ASTDiffNode[]
  propertyChanges: Array<{ key: string; lhs: unknown; rhs: unknown }>
}

/**
 * Flattened external call shape — mirrors the iteration order of
 * `ExternalCallsTab.vue` (depth-first, root first). Each call is paired with its
 * source-node identity so the diff can distinguish "same target, different
 * producer" from "same target, same producer".
 */
export interface FlatExternalCall {
  sourcePath: string
  sourceKind: CallNode['kind']
  sourceName: string
  call: Record<string, unknown>
}

/**
 * Status of an external-call row in the diff table. Comparison key is
 * `<sourcePath>|<target>|<operation>` — same triple = `same`, otherwise the row
 * is marked `added` / `removed`, and `modified` is reserved for the (rare) case
 * where two distinct keys collide on the same composite identity.
 */
export type CallDiffStatus = 'same' | 'added' | 'removed' | 'modified'

export interface CallDiffRow {
  key: string
  status: CallDiffStatus
  baseline?: FlatExternalCall
  current?: FlatExternalCall
}

/**
 * One row of the metrics diff table. `delta` is `current - baseline`; for
 * `executionDurationMs` / `memoryUsedBytes` / call counts, a *lower* number is
 * an improvement (so the view flips the green/red mapping). Percentage change
 * is `null` whenever the baseline is missing or zero (avoids division-by-zero
 * and "infinite regression" noise).
 */
export interface MetricsDiffRow {
  key: string
  label: string
  baseline: number | null
  current: number | null
  delta: number | null
  percentChange: number | null
  /**
   * `true` when a decrease is the desired direction (latency / memory / call
   * counts). `false` for raw counts where direction is neutral — the view
   * treats neutral direction as "no semantic improvement/regression".
   */
  lowerIsBetter: boolean
}

export interface PreviewDiff {
  diffLines: ComputedRef<DiffLine[]>
  astDiff: ComputedRef<ASTDiffNode | null>
  callDiff: ComputedRef<CallDiffRow[]>
  metricsDiff: ComputedRef<MetricsDiffRow[]>
}

function asString(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function flattenExternalCalls(
  node: CallNode | undefined,
  path: string,
  acc: FlatExternalCall[] = [],
): FlatExternalCall[] {
  if (!node) return acc
  const here = path ? `${path}/${node.name}` : node.name
  for (const call of node.externalCalls ?? []) {
    acc.push({
      sourcePath: here,
      sourceKind: node.kind,
      sourceName: node.name,
      call,
    })
  }
  for (let i = 0; i < (node.children ?? []).length; i++) {
    flattenExternalCalls(node.children[i], here, acc)
  }
  return acc
}

function callKey(call: FlatExternalCall): string {
  const target = String(call.call.target ?? '')
  const operation = String(call.call.operation ?? '')
  return `${call.sourcePath}|${target}|${operation}`
}

function diffExternalCalls(
  baseline: FlatExternalCall[],
  current: FlatExternalCall[],
): CallDiffRow[] {
  const byKey = new Map<string, CallDiffRow>()

  for (const call of baseline) {
    byKey.set(callKey(call), { key: callKey(call), status: 'removed', baseline: call })
  }
  for (const call of current) {
    const k = callKey(call)
    const existing = byKey.get(k)
    if (existing) {
      existing.current = call
      existing.status = 'same'
    } else {
      byKey.set(k, { key: k, status: 'added', current: call })
    }
  }

  return Array.from(byKey.values()).sort((a, b) => {
    const order: Record<CallDiffStatus, number> = { same: 0, added: 1, removed: 2, modified: 3 }
    if (order[a.status] !== order[b.status]) return order[a.status] - order[b.status]
    return a.key.localeCompare(b.key)
  })
}

/**
 * Build a `Map<key, number>` from a `Map<CallKind, Integer>`-like structure. The
 * backend serialises maps as plain objects, but defensively accept `Map`
 * instances for future-proofing.
 */
function normaliseNumericMap(
  value: Record<string, number> | Map<string, number> | undefined,
): Record<string, number> {
  if (!value) return {}
  if (value instanceof Map) {
    const out: Record<string, number> = {}
    for (const [k, v] of value) out[k] = typeof v === 'number' ? v : Number(v)
    return out
  }
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(value)) {
    if (typeof v === 'number' && Number.isFinite(v)) out[k] = v
  }
  return out
}

function pairChildren(
  lhs: CallNode[] | undefined,
  rhs: CallNode[] | undefined,
): Array<{ lhs?: CallNode; rhs?: CallNode }> {
  const pairs: Array<{ lhs?: CallNode; rhs?: CallNode }> = []
  const rhsQueue = new Map<string, CallNode[]>()
  for (const r of rhs ?? []) {
    const k = `${r.kind}:${r.name}`
    const queue = rhsQueue.get(k) ?? []
    queue.push(r)
    rhsQueue.set(k, queue)
  }

  const consumed = new WeakSet<CallNode>()
  for (const l of lhs ?? []) {
    const k = `${l.kind}:${l.name}`
    const queue = rhsQueue.get(k)
    const match = queue?.shift()
    if (match) consumed.add(match)
    pairs.push({ lhs: l, rhs: match })
  }
  for (const r of rhs ?? []) {
    if (!consumed.has(r)) pairs.push({ rhs: r })
  }
  return pairs
}

function diffAst(
  baseline: CallNode | undefined,
  current: CallNode | undefined,
  inheritedStatus: 'added' | 'removed' | 'modified' | 'same',
): ASTDiffNode | null {
  if (!baseline && !current) return null

  if (baseline && !current) {
    return {
      status: 'removed',
      name: baseline.name,
      kind: baseline.kind,
      success: baseline.success,
      children: (baseline.children ?? []).map((c) => diffAst(c, undefined, 'removed')),
      propertyChanges: [],
    }
  }
  if (!baseline && current) {
    return {
      status: 'added',
      name: current.name,
      kind: current.kind,
      success: current.success,
      children: (current.children ?? []).map((c) => diffAst(undefined, c, 'added')),
      propertyChanges: [],
    }
  }

  const lhs = baseline as CallNode
  const rhs = current as CallNode
  const sameIdentity = lhs.name === rhs.name && lhs.kind === rhs.kind

  if (!sameIdentity) {
    // Identity diverged at this position — bubble the inherited status and
    // record the property deltas so the view can surface "X became Y".
    return {
      status: inheritedStatus === 'same' ? 'modified' : inheritedStatus,
      name: `${lhs.name} → ${rhs.name}`,
      kind: rhs.kind,
      success: rhs.success,
      children: [],
      propertyChanges: [
        { key: 'name', lhs: lhs.name, rhs: rhs.name },
        { key: 'kind', lhs: lhs.kind, rhs: rhs.kind },
      ],
    }
  }

  const propertyChanges: ASTDiffNode['propertyChanges'] = []
  if (lhs.success !== rhs.success) {
    propertyChanges.push({ key: 'success', lhs: lhs.success, rhs: rhs.success })
  }
  if (asString(lhs.input) !== asString(rhs.input)) {
    propertyChanges.push({ key: 'input', lhs: lhs.input, rhs: rhs.input })
  }
  if (asString(lhs.output) !== asString(rhs.output)) {
    propertyChanges.push({ key: 'output', lhs: lhs.output, rhs: rhs.output })
  }

  // Pair children by (name, kind) so a renamed sibling doesn't get silently
  // reclassified as a modified node — the diff stays meaningful even when the
  // DSL insert/removed a child.
  const children: ASTDiffNode[] = []
  for (const pair of pairChildren(lhs.children, rhs.children)) {
    const childStatus: ASTDiffStatus = propertyChanges.length > 0 ? 'modified' : 'same'
    const child = diffAst(pair.lhs, pair.rhs, childStatus)
    if (child) children.push(child)
  }

  return {
    status: propertyChanges.length > 0 ? 'modified' : 'same',
    name: lhs.name,
    kind: lhs.kind,
    success: rhs.success,
    children,
    propertyChanges,
  }
}

function diffMetric(
  key: string,
  label: string,
  baseline: number | null | undefined,
  current: number | null | undefined,
  lowerIsBetter: boolean,
): MetricsDiffRow {
  const baselineNum = typeof baseline === 'number' && Number.isFinite(baseline) ? baseline : null
  const currentNum = typeof current === 'number' && Number.isFinite(current) ? current : null

  let delta: number | null = null
  let percentChange: number | null = null
  if (baselineNum !== null && currentNum !== null) {
    delta = currentNum - baselineNum
    if (baselineNum !== 0) {
      percentChange = (delta / baselineNum) * 100
    }
  }

  return {
    key,
    label,
    baseline: baselineNum,
    current: currentNum,
    delta,
    percentChange,
    lowerIsBetter,
  }
}

function diffMetrics(
  baseline: PreviewMetricsSnapshot | undefined,
  current: PreviewMetricsSnapshot | undefined,
): MetricsDiffRow[] {
  const rows: MetricsDiffRow[] = [
    diffMetric(
      'executionDurationMs',
      'Execution duration (ms)',
      baseline?.executionDurationMs,
      current?.executionDurationMs,
      true,
    ),
    diffMetric(
      'memoryUsedBytes',
      'Memory used (bytes)',
      baseline?.memoryUsedBytes,
      current?.memoryUsedBytes,
      true,
    ),
  ]

  const baselineCounts = normaliseNumericMap(
    baseline?.callCounts as Record<string, number> | undefined,
  )
  const currentCounts = normaliseNumericMap(
    current?.callCounts as Record<string, number> | undefined,
  )
  const callKeys = new Set([...Object.keys(baselineCounts), ...Object.keys(currentCounts)])
  for (const kind of callKeys) {
    rows.push(
      diffMetric(
        `callCounts.${kind}`,
        `Calls (${kind})`,
        baselineCounts[kind],
        currentCounts[kind],
        true,
      ),
    )
  }

  const baselineExternal = normaliseNumericMap(baseline?.externalCallCounts)
  const currentExternal = normaliseNumericMap(current?.externalCallCounts)
  const externalKeys = new Set([...Object.keys(baselineExternal), ...Object.keys(currentExternal)])
  for (const target of externalKeys) {
    rows.push(
      diffMetric(
        `externalCallCounts.${target}`,
        `External (${target})`,
        baselineExternal[target],
        currentExternal[target],
        true,
      ),
    )
  }

  return rows
}

/**
 * Build a preview-diff view over a `baseline` and `current` `RunnerOutput`.
 *
 * Both arguments accept either a `RunnerOutput` (or `null`) or a
 * `Ref<RunnerOutput | null>` / `ComputedRef<RunnerOutput | null>` so the view
 * can be bound directly from page state. The output-text diff is delegated to
 * the shared `useDiffLines` composable — do not reimplement LCS here.
 */
export function usePreviewDiff(
  baseline: RunnerOutput | null | Ref<RunnerOutput | null> | ComputedRef<RunnerOutput | null>,
  current: RunnerOutput | null | Ref<RunnerOutput | null> | ComputedRef<RunnerOutput | null>,
): PreviewDiff {
  const baselineValue = computed(() => unref(baseline))
  const currentValue = computed(() => unref(current))

  const lhsJson = computed(() => asString(baselineValue.value?.result))
  const rhsJson = computed(() => asString(currentValue.value?.result))

  // Call useDiffLines inside a computed so changes to lhsJson/rhsJson propagate
  // through the LCS pipeline without us having to subscribe manually.
  const diffLines = computed<DiffLine[]>(() => useDiffLines(lhsJson.value, rhsJson.value).value)

  const astDiff = computed<ASTDiffNode | null>(() =>
    diffAst(baselineValue.value?.astTree, currentValue.value?.astTree, 'same'),
  )

  const callDiff = computed<CallDiffRow[]>(() =>
    diffExternalCalls(
      flattenExternalCalls(baselineValue.value?.astTree, ''),
      flattenExternalCalls(currentValue.value?.astTree, ''),
    ),
  )

  const metricsDiff = computed<MetricsDiffRow[]>(() =>
    diffMetrics(baselineValue.value?.metrics, currentValue.value?.metrics),
  )

  return {
    diffLines,
    astDiff,
    callDiff,
    metricsDiff,
  }
}
