<script setup lang="ts">
import { computed, ref } from 'vue'

import type { RunnerMode, RunnerOutput, RunnerStatus } from '../../types/runner'

defineOptions({ name: 'OutputPanel' })

const props = defineProps<{
  output: RunnerOutput | null
  mode: RunnerMode
  status: RunnerStatus
  lastRunOutput?: unknown
  baselineOutput?: RunnerOutput | null
}>()

const emit = defineEmits<(e: 'clear-baseline') => void>()

const activeTab = ref<
  'result' | 'metadata' | 'errors' | 'callTree' | 'dryRunLogs' | 'externalCalls' | 'diff'
>('result')

const tabs = computed(() => [
  { value: 'result' as const, label: 'Result' },
  { value: 'metadata' as const, label: 'Metadata' },
  { value: 'errors' as const, label: 'Errors' },
  ...(props.mode === 'preview' || props.mode === 'explain'
    ? [
        { value: 'callTree' as const, label: 'Call Tree' },
        { value: 'dryRunLogs' as const, label: 'Logs' },
        { value: 'externalCalls' as const, label: 'External Calls' },
      ]
    : []),
  ...(props.mode === 'preview' && props.baselineOutput
    ? [{ value: 'diff' as const, label: 'Diff' }]
    : []),
])

const showExplain = computed(() => props.mode === 'explain')
const showDiff = computed(() => props.mode === 'explain' && props.output !== null)
const showPreviewDiff = computed(
  () =>
    props.mode === 'preview' && props.baselineOutput !== null && props.baselineOutput !== undefined,
)

const isEmpty = computed(() => !props.output)

function onClearBaseline() {
  emit('clear-baseline')
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div v-if="showExplain">
      <ExplainOutput
        :description="props.output?.description"
        :mermaid-diagram="props.output?.mermaidDiagram"
      />
    </div>

    <div v-if="showDiff">
      <ExplainDiffView :explain-output="props.output?.result" :run-output="props.lastRunOutput" />
    </div>

    <div v-if="!isEmpty" class="border-b border-gray-200 flex flex-wrap gap-1">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
        :data-testid="`output-panel-tab-${t.value}`"
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

    <div v-if="isEmpty" class="text-sm text-gray-500">Output will appear here after running.</div>

    <div v-else>
      <ResultTab v-if="activeTab === 'result'" :result="props.output?.result" />
      <MetadataTab v-else-if="activeTab === 'metadata'" :metadata="props.output?.metadata" />
      <ErrorsTab v-else-if="activeTab === 'errors'" :errors="props.output?.errors" />
      <CallTreeTab v-else-if="activeTab === 'callTree'" :tree="props.output?.astTree" />
      <ExternalCallsTab v-else-if="activeTab === 'externalCalls'" :tree="props.output?.astTree" />
      <DryRunLogsTab v-else-if="activeTab === 'dryRunLogs'" :logs="props.output?.dryRunLogs" />
      <div
        v-else-if="activeTab === 'diff' && showPreviewDiff"
        class="flex flex-col gap-3"
        data-testid="output-panel-diff"
      >
        <PreviewDiffView :baseline="props.baselineOutput ?? null" :current="props.output" />
        <div class="flex justify-end">
          <button
            type="button"
            class="text-xs font-medium text-gray-500 hover:text-gray-800"
            data-testid="output-panel-clear-baseline"
            @click="onClearBaseline"
          >
            Clear baseline
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
