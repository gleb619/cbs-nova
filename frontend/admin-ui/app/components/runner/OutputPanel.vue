<script setup lang="ts">
import type { RunnerMode, RunnerOutput, RunnerStatus } from '~/types/runner'

const props = defineProps<{
  output: RunnerOutput | null
  mode: RunnerMode
  status: RunnerStatus
}>()

const activeTab = ref<'result' | 'metadata' | 'errors'>('result')

const tabs = [
  { value: 'result' as const, label: 'Result' },
  { value: 'metadata' as const, label: 'Metadata' },
  { value: 'errors' as const, label: 'Errors' },
]

const showExplain = computed(() => props.mode === 'explain')

const isEmpty = computed(() => !props.output)
</script>

<template>
  <div class="flex flex-col gap-4">
    <div v-if="showExplain">
      <ExplainOutput
        :description="props.output?.description"
        :mermaid-diagram="props.output?.mermaidDiagram"
      />
    </div>

    <div v-if="!isEmpty" class="border-b border-gray-200 flex gap-1">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
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
      <ErrorsTab v-else :errors="props.output?.errors" />
    </div>
  </div>
</template>
