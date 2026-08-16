<script setup lang="ts">
import { usePreviewDiff } from '../../composables/usePreviewDiff'
import type { RunnerOutput } from '../../types/runner'

defineOptions({ name: 'PreviewDiffView' })

const props = defineProps<{
  baseline: RunnerOutput | null
  current: RunnerOutput | null
}>()

const baselineRef = computed(() => props.baseline)
const currentRef = computed(() => props.current)
// biome-ignore lint/correctness/noUnusedVariables: used in template panels below
const { diffLines, astDiff, callDiff, metricsDiff } = usePreviewDiff(baselineRef, currentRef)

type Tab = 'output' | 'ast' | 'calls' | 'metrics'

const activeTab = ref<Tab>('output')

const tabs = computed<Array<{ value: Tab; label: string; testid: string }>>(() => [
  { value: 'output', label: 'Output Diff', testid: 'preview-diff-tab-output' },
  { value: 'ast', label: 'AST Diff', testid: 'preview-diff-tab-ast' },
  { value: 'calls', label: 'External Calls Diff', testid: 'preview-diff-tab-calls' },
  { value: 'metrics', label: 'Metrics Diff', testid: 'preview-diff-tab-metrics' },
])

// biome-ignore lint/correctness/noUnusedVariables: rendered into baseline pane
const baselineJson = computed(() => formatResult(props.baseline?.result))
// biome-ignore lint/correctness/noUnusedVariables: rendered into current pane
const currentJson = computed(() => formatResult(props.current?.result))
const hasBoth = computed(() => props.baseline !== null && props.current !== null)

function formatResult(value: unknown): string {
  if (value === undefined || value === null) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

const statusBadge: Record<string, string> = {
  same: 'bg-gray-100 text-gray-600',
  added: 'bg-green-100 text-green-700',
  removed: 'bg-red-100 text-red-700',
  modified: 'bg-yellow-100 text-yellow-800',
}

const statusLabel: Record<string, string> = {
  same: 'unchanged',
  added: 'added',
  removed: 'removed',
  modified: 'modified',
}

const callTypeClass = (type: string) => {
  switch (type) {
    case 'database':
      return 'text-blue-600 bg-blue-50'
    case 'http':
      return 'text-green-600 bg-green-50'
    case 'mq':
      return 'text-purple-600 bg-purple-50'
    case 'filesystem':
      return 'text-yellow-700 bg-yellow-50'
    case 'external_api':
      return 'text-pink-600 bg-pink-50'
    case 'microservice':
      return 'text-indigo-600 bg-indigo-50'
    case 'activity':
      return 'text-cyan-600 bg-cyan-50'
    default:
      return 'text-gray-500 bg-gray-100'
  }
}

const callTimestamp = (ts: unknown): string => {
  if (typeof ts !== 'number' || !Number.isFinite(ts)) return String(ts ?? '')
  try {
    return new Date(ts).toISOString()
  } catch {
    return String(ts)
  }
}
</script>

<template>
  <div class="flex flex-col gap-4" data-testid="preview-diff-view">
    <div class="flex items-center justify-between gap-2">
      <h3 class="text-sm font-semibold text-gray-700">Preview Diff</h3>
      <div v-if="!hasBoth" class="text-xs text-gray-500">
        Showing current only — baseline is missing.
      </div>
    </div>

    <div class="border-b border-gray-200 flex flex-wrap gap-1">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
        :data-testid="t.testid"
        :data-active="activeTab === t.value"
        :class="[
          'px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors',
          activeTab === t.value
            ? 'border-blue-600 text-blue-600'
            : 'border-transparent text-gray-600 hover:text-gray-900',
        ]"
        @click="activeTab = t.value"
      >
        {{ t.label }}
      </button>
    </div>

    <!-- Output diff tab -->
    <div
      v-if="activeTab === 'output'"
      class="flex flex-col gap-2"
      data-testid="preview-diff-output-panel"
    >
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="flex flex-col gap-2">
          <span class="text-xs font-medium text-gray-500">Baseline</span>
          <pre
            class="bg-gray-50 text-gray-900 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words"
            data-testid="preview-diff-baseline-pane"
          >{{ baselineJson }}</pre>
        </div>
        <div class="flex flex-col gap-2">
          <span class="text-xs font-medium text-gray-500">Current</span>
          <pre
            class="bg-gray-50 text-gray-900 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words"
            data-testid="preview-diff-current-pane"
          >{{ currentJson }}</pre>
        </div>
      </div>
      <div class="flex flex-col gap-1">
        <span class="text-xs font-medium text-gray-500">Line-level diff</span>
        <pre
          class="bg-gray-50 text-gray-900 text-xs rounded-lg p-2 overflow-auto max-h-[60vh]"
          data-testid="preview-diff-unified"
        >
          <DiffLine
            v-for="(line, index) in diffLines"
            :key="index"
            :kind="line.kind"
            :text="line.text"
          />
        </pre>
      </div>
    </div>

    <!-- AST diff tab -->
    <div
      v-else-if="activeTab === 'ast'"
      class="flex flex-col gap-2"
      data-testid="preview-diff-ast-panel"
    >
      <div v-if="!astDiff" class="text-sm text-gray-500">No AST tree available to compare.</div>
      <ASTDiffNode v-else :node="astDiff" :depth="0" />
    </div>

    <!-- External calls diff tab -->
    <div
      v-else-if="activeTab === 'calls'"
      class="flex flex-col gap-2"
      data-testid="preview-diff-calls-panel"
    >
      <div v-if="callDiff.length === 0" class="text-sm text-gray-500">
        No external calls to compare.
      </div>
      <ul v-else class="space-y-1 border border-gray-200 rounded-lg p-2 max-h-[60vh] overflow-auto">
        <li
          v-for="row in callDiff"
          :key="row.key"
          class="font-mono text-xs flex items-start gap-2 py-1"
          :data-status="row.status"
          data-testid="preview-diff-call-row"
        >
          <span
            class="px-1.5 py-0.5 rounded font-semibold shrink-0"
            :class="statusBadge[row.status]"
          >
            {{ statusLabel[row.status] }}
          </span>
          <template v-if="row.current">
            <span
              class="px-1.5 py-0.5 rounded font-semibold shrink-0"
              :class="callTypeClass(String(row.current.call.type ?? ''))"
              :data-type="String(row.current.call.type ?? '')"
            >
              {{ String(row.current.call.type ?? 'other') }}
            </span>
            <span class="text-gray-700 shrink-0">{{ String(row.current.call.target ?? '') }}</span>
            <span class="text-gray-400 shrink-0">—</span>
            <span class="text-gray-900 break-words"
              >{{ String(row.current.call.operation ?? '') }}</span
            >
            <span class="text-gray-500 shrink-0 ml-auto"
              >[{{ callTimestamp(row.current.call.timestamp) }}]</span
            >
          </template>
          <template v-else-if="row.baseline">
            <span
              class="px-1.5 py-0.5 rounded font-semibold shrink-0"
              :class="callTypeClass(String(row.baseline.call.type ?? ''))"
              :data-type="String(row.baseline.call.type ?? '')"
            >
              {{ String(row.baseline.call.type ?? 'other') }}
            </span>
            <span class="text-gray-700 shrink-0">{{ String(row.baseline.call.target ?? '') }}</span>
            <span class="text-gray-400 shrink-0">—</span>
            <span class="text-gray-900 break-words"
              >{{ String(row.baseline.call.operation ?? '') }}</span
            >
            <span class="text-gray-500 shrink-0 ml-auto"
              >[{{ callTimestamp(row.baseline.call.timestamp) }}]</span
            >
          </template>
          <span class="text-gray-400 shrink-0">·</span>
          <span class="text-gray-600 shrink-0"
            >{{ row.baseline?.sourceKind ?? row.current?.sourceKind }}:
            {{ row.baseline?.sourceName ?? row.current?.sourceName }}
          </span>
        </li>
      </ul>
    </div>

    <!-- Metrics diff tab -->
    <div
      v-else-if="activeTab === 'metrics'"
      class="flex flex-col gap-2"
      data-testid="preview-diff-metrics-panel"
    >
      <MetricsDiffTable :rows="metricsDiff" />
    </div>
  </div>
</template>
