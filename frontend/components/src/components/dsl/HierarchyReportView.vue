<script setup lang="ts">
import { computed } from 'vue'
import type { RunnerOutput } from '../../types/runner'
import ErrorList from '../ErrorList.vue'
import MermaidDiagram from '../runner/MermaidDiagram.vue'
import HierarchyReportNode from './HierarchyReportNode.vue'

const props = defineProps<{
  output: RunnerOutput
  showRaw?: boolean
}>()

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

const report = computed(() =>
  isObject(props.output.result) ? (props.output.result as Record<string, unknown>) : undefined,
)

function hasString(value: unknown): boolean {
  return typeof value === 'string' && value.length > 0
}

const canVisualize = computed(() => {
  if (!report.value) return false
  return (
    Array.isArray(report.value.children) ||
    hasString(report.value.mermaidDiagram) ||
    Array.isArray(report.value.externalCalls) ||
    Array.isArray(report.value.executionTrace)
  )
})

const fallbackToRaw = computed(() => {
  if (props.output.errors && props.output.errors.length > 0) return true
  return !canVisualize.value
})

const name = computed(() =>
  report.value && typeof report.value.name === 'string' ? report.value.name : '',
)
const description = computed(() =>
  report.value && typeof report.value.description === 'string'
    ? report.value.description
    : undefined,
)
const hasCompensation = computed(() =>
  report.value && typeof report.value.hasCompensation === 'boolean'
    ? report.value.hasCompensation
    : false,
)
const errorCount = computed(() => props.output.errors?.length ?? 0)

function totalCallCount(): number {
  const counts =
    report.value && isObject(report.value.callCounts)
      ? (report.value.callCounts as Record<string, unknown>)
      : {}
  return Object.values(counts).reduce((sum, n) => sum + (typeof n === 'number' ? n : 0), 0)
}

function externalCalls(): Array<Record<string, unknown>> {
  const calls = report.value?.externalCalls
  return Array.isArray(calls) ? calls.filter(isObject) : []
}

function executionTrace(): string[] {
  const trace = report.value?.executionTrace
  if (!Array.isArray(trace)) return []
  return trace.filter((t): t is string => typeof t === 'string')
}

function childReports(): Record<string, unknown>[] {
  const children = report.value?.children
  return Array.isArray(children) ? children.filter(isObject) : []
}

function externalCallLabel(call: Record<string, unknown>): string {
  const type = String(call.type ?? '')
  const target = String(call.target ?? '')
  const operation = String(call.operation ?? '')
  return type ? `[${type}] ${target} — ${operation}` : `${target} — ${operation}`
}
</script>

<template>
  <div class="space-y-3" data-testid="hierarchy-report-view">
    <ErrorList
      v-if="output.errors && output.errors.length > 0"
      :items="output.errors"
      data-testid="hierarchy-error-list"
    />

    <pre
      v-if="fallbackToRaw || showRaw"
      data-testid="hierarchy-result-text"
      class="w-full h-full min-h-0 font-mono text-xs leading-relaxed text-ink whitespace-pre-wrap break-words"
    >{{ JSON.stringify(output, null, 2) }}</pre>

    <template v-else>
      <div class="flex flex-wrap items-center gap-2" data-testid="hierarchy-summary">
        <span
          v-if="name"
          class="inline-flex items-center gap-1 rounded border border-accent-500/20 bg-accent-500/10 px-2 py-0.5 text-xs font-medium text-accent-600"
          data-testid="hierarchy-summary-name"
        >
          {{ name }}
        </span>
        <span
          v-if="description"
          class="inline-flex items-center rounded border border-line bg-surface px-2 py-0.5 text-xs text-ink"
          data-testid="hierarchy-summary-description"
        >
          {{ description }}
        </span>
        <span
          v-if="hasCompensation"
          class="inline-flex items-center rounded bg-warning-100 px-2 py-0.5 text-xs font-medium text-warning-700"
          data-testid="hierarchy-summary-compensation"
        >
          compensation
        </span>
        <span
          class="inline-flex items-center rounded bg-success-100 px-2 py-0.5 text-xs font-medium text-success-700"
          data-testid="hierarchy-summary-calls"
        >
          {{ totalCallCount() }}
          total call{{ totalCallCount() === 1 ? '' : 's' }}
        </span>
        <span
          v-if="errorCount > 0"
          class="inline-flex items-center rounded bg-error-100 px-2 py-0.5 text-xs font-medium text-error-700"
          data-testid="hierarchy-summary-errors"
        >
          {{ errorCount }}
          error{{ errorCount === 1 ? '' : 's' }}
        </span>
      </div>

      <div
        v-if="report && hasString(report.mermaidDiagram)"
        class="border border-line rounded-sm bg-white p-2"
        data-testid="hierarchy-mermaid"
      >
        <MermaidDiagram :definition="report.mermaidDiagram" />
      </div>

      <details
        v-if="childReports().length > 0"
        open
        class="border border-line rounded-sm bg-white"
        data-testid="hierarchy-children-tree"
      >
        <summary class="cursor-pointer select-none px-3 py-2 text-xs font-medium text-ink">
          Children ({{ childReports().length }})
        </summary>
        <div class="px-3 pb-3 space-y-1">
          <HierarchyReportNode
            v-for="(child, idx) in childReports()"
            :key="idx"
            :node="child"
            :depth="0"
          />
        </div>
      </details>

      <details
        v-if="externalCalls().length > 0"
        class="border border-line rounded-sm bg-white"
        data-testid="hierarchy-external-calls"
      >
        <summary class="cursor-pointer select-none px-3 py-2 text-xs font-medium text-ink">
          External calls ({{ externalCalls().length }})
        </summary>
        <ul class="px-3 pb-3 space-y-1">
          <li
            v-for="(call, idx) in externalCalls()"
            :key="idx"
            class="text-xs text-ink"
            data-testid="hierarchy-external-call"
          >
            {{ externalCallLabel(call) }}
          </li>
        </ul>
      </details>

      <details
        v-if="executionTrace().length > 0"
        class="border border-line rounded-sm bg-white"
        data-testid="hierarchy-execution-trace"
      >
        <summary class="cursor-pointer select-none px-3 py-2 text-xs font-medium text-ink">
          Execution trace ({{ executionTrace().length }})
        </summary>
        <ol class="px-3 pb-3 list-decimal pl-4 space-y-1">
          <li
            v-for="(step, idx) in executionTrace()"
            :key="idx"
            class="text-xs text-ink"
            data-testid="hierarchy-execution-trace-step"
          >
            {{ step }}
          </li>
        </ol>
      </details>
    </template>
  </div>
</template>
