<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { DslConstruct, StepDef } from '../../types/dsl'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import CodeTab from './CodeTab.vue'
import ExplainTab from './ExplainTab.vue'
import PreviewTab from './PreviewTab.vue'
import StructureTab from './StructureTab.vue'

const props = defineProps<{
  construct: DslConstruct | null
  /**
   * Optional controlled body content (e.g. driven by `useWorkbenchDraft`).
   * When omitted, the editor falls back to its own internal stub state so
   * existing callers keep working unchanged.
   */
  code?: string
  preview?: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => Promise<RunnerOutput>
  explain?: (
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
  ) => Promise<RunnerOutput>
}>()

const emit = defineEmits<{
  'update:code': [value: string]
}>()

const tab = ref<'structure' | 'code' | 'preview' | 'explain'>('structure')

// stub — future: derive from construct introspection
const steps = ref<StepDef[]>([])

// Internal fallback body, used only when the caller doesn't pass `code`.
const internalCode = ref('')

const previewOutput = ref<RunnerOutput | null>(null)
const previewStatus = ref<RunnerStatus>('idle')
const explainOutput = ref<RunnerOutput | null>(null)
const explainStatus = ref<RunnerStatus>('idle')

function errorOutput(err: unknown): RunnerOutput {
  const message = (err instanceof Error ? err.message : undefined) ?? 'Request failed'
  return { errors: [{ message, code: 'REQUEST_FAILED' }] }
}

async function runPreview() {
  if (!props.construct || !props.preview) return
  previewStatus.value = 'loading'
  previewOutput.value = null
  try {
    previewOutput.value = await props.preview(
      props.construct.name,
      {},
      { startedFrom: 'workbench' },
    )
    previewStatus.value = previewOutput.value.errors?.length ? 'failed' : 'success'
  } catch (err: unknown) {
    previewOutput.value = errorOutput(err)
    previewStatus.value = 'failed'
  }
}

async function runExplain() {
  if (!props.construct || !props.explain) return
  explainStatus.value = 'loading'
  explainOutput.value = null
  try {
    explainOutput.value = await props.explain(
      props.construct.name,
      {},
      { startedFrom: 'workbench' },
    )
    explainStatus.value = explainOutput.value.errors?.length ? 'failed' : 'success'
  } catch (err: unknown) {
    explainOutput.value = errorOutput(err)
    explainStatus.value = 'failed'
  }
}

const isControlled = computed(() => props.code !== undefined)

const bodyCode = computed<string>({
  get: () => (isControlled.value ? (props.code ?? '') : internalCode.value),
  set: (value: string) => {
    if (isControlled.value) {
      emit('update:code', value)
    } else {
      internalCode.value = value
    }
  },
})

watch(
  () => props.construct?.name,
  () => {
    steps.value = []
    previewOutput.value = null
    previewStatus.value = 'idle'
    explainOutput.value = null
    explainStatus.value = 'idle'
    if (!isControlled.value) {
      internalCode.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="flex flex-col h-full bg-white">
    <div class="flex items-center border-b border-gray-200 px-2">
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'structure' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'structure'"
      >
        Structure
      </button>
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'code' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'code'"
      >
        Code
      </button>
      <button
        v-if="preview"
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'preview' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'preview'"
      >
        Preview
      </button>
      <button
        v-if="explain"
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'explain' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        @click="tab = 'explain'"
      >
        Explain
      </button>
    </div>
    <div class="flex-1 overflow-auto">
      <StructureTab v-show="tab === 'structure'" :steps="steps" />
      <CodeTab v-show="tab === 'code'" v-model:code="bodyCode" :read-only="!construct" />
      <PreviewTab
        v-if="tab === 'preview'"
        :output="previewOutput"
        :status="previewStatus"
        @run="runPreview"
      />
      <ExplainTab
        v-if="tab === 'explain'"
        :output="explainOutput"
        :status="explainStatus"
        @run="runExplain"
      />
    </div>
  </div>
</template>
