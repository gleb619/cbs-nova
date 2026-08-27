<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { type DiffLineKind, useDiffLines } from '../../composables/useDiffLines'

defineOptions({ name: 'ExplainDiffView' })

const props = defineProps<{
  explainOutput: unknown
  runOutput: unknown
  layout?: 'split' | 'unified'
}>()

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const preferredLayout = ref<'split' | 'unified' | null>(null)

onMounted(() => {
  const handleResize = () => {
    windowWidth.value = window.innerWidth
  }
  window.addEventListener('resize', handleResize)
  onUnmounted(() => window.removeEventListener('resize', handleResize))
})

const effectiveLayout = computed(
  () => props.layout ?? preferredLayout.value ?? (windowWidth.value >= 768 ? 'split' : 'unified'),
)
const layoutLabel = computed(() => (effectiveLayout.value === 'split' ? 'Unified' : 'Split'))

const explainJson = computed(() => {
  if (props.explainOutput === undefined || props.explainOutput === null) return ''
  return JSON.stringify(props.explainOutput, null, 2)
})
const runJson = computed(() => JSON.stringify(props.runOutput ?? null, null, 2))
const hasRunOutput = computed(() => props.runOutput !== undefined)

// biome-ignore lint/correctness/noUnusedVariables: used in the unified layout template
const unifiedLines = computed(() => useDiffLines(explainJson.value, runJson.value).value)

function toggleLayout() {
  preferredLayout.value = effectiveLayout.value === 'split' ? 'unified' : 'split'
}

// biome-ignore lint/correctness/noUnusedVariables: used in the unified layout template
function lineClass(kind: DiffLineKind): string {
  switch (kind) {
    case 'lhs-only':
      return 'bg-yellow-50 border-l-4 border-yellow-400 pl-2'
    case 'rhs-only':
      return 'bg-green-50 border-l-4 border-green-400 pl-2'
    default:
      return 'border-l-4 border-transparent pl-2'
  }
}
</script>

<template>
  <div class="flex flex-col gap-4" data-testid="explain-diff-view">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-700">Run vs Explain</h3>
      <button
        v-if="hasRunOutput"
        type="button"
        class="text-xs font-medium text-blue-600 hover:text-blue-800"
        @click="toggleLayout"
      >
        {{ layoutLabel }}
      </button>
    </div>

    <div v-if="!hasRunOutput" class="text-sm text-gray-500">No run result to compare.</div>

    <template v-else>
      <!-- Split layout -->
      <div
        v-if="effectiveLayout === 'split'"
        class="grid grid-cols-1 md:grid-cols-2 gap-4"
        data-testid="split-layout"
      >
        <div class="flex flex-col gap-2">
          <span class="text-xs font-medium text-gray-500">Explain (dry run)</span>
          <pre
            class="bg-gray-50 text-gray-900 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words"
            data-testid="explain-pane"
          >{{ explainJson }}</pre>
        </div>
        <div class="flex flex-col gap-2">
          <span class="text-xs font-medium text-gray-500">Run (last result)</span>
          <pre
            class="bg-gray-50 text-gray-900 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words"
            data-testid="run-pane"
          >{{ runJson }}</pre>
        </div>
      </div>

      <!-- Unified layout -->
      <div v-else class="flex flex-col gap-2" data-testid="unified-layout">
        <pre class="bg-gray-50 text-gray-900 text-xs rounded-lg p-4 overflow-auto max-h-[60vh]">
          <div
            v-for="(line, index) in unifiedLines"
            :key="index"
            :class="lineClass(line.kind)"
            class="whitespace-pre"
            data-testid="diff-line"
          >
            {{ line.text }}
          </div>
        </pre>
      </div>
    </template>
  </div>
</template>
