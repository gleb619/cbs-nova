<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import {
  createNamespacedLocalStorageState,
  type UseCookieFactory,
} from '../../composables/useLocalStorageState'
import type { DslConstruct, HelperCatalogEntry, StepDef, ValidationError } from '../../types/dsl'
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import type { EditorMarker } from './MonacoEditor.vue'
import CodeTab from './CodeTab.vue'
import ExplainTab from './ExplainTab.vue'
import PreviewTab from './PreviewTab.vue'
import ProblemsPanel from './ProblemsPanel.vue'
import StructureTab from './StructureTab.vue'

const props = withDefaults(
  defineProps<{
    construct: DslConstruct | null
    /**
     * Optional controlled body content (e.g. driven by `useWorkbenchDraft`).
     * When omitted, the editor falls back to its own internal stub state so
     * existing callers keep working unchanged.
     */
    code?: string
    saveStatus?: string
    lastSavedAt?: Date | null
    savedHash?: number | null
    helperCatalogFetch?: () => Promise<HelperCatalogEntry[]>
    explain?: (
      name: string,
      body: unknown,
      metadata?: Record<string, unknown>,
    ) => Promise<RunnerOutput>
    preview?: (
      name: string,
      body: unknown,
      metadata?: Record<string, unknown>,
    ) => Promise<RunnerOutput> | RunnerOutput
    /** Inline diagnostic markers — forwarded to the Code tab. */
    markers?: EditorMarker[]
    /** Validation errors — surfaced under the Problems tab. */
    errors?: ValidationError[]
  }>(),
  { markers: () => [], errors: () => [] },
)

const emit = defineEmits<{
  'update:code': [value: string]
  save: [value: string]
  select: [payload: { index: number; error: ValidationError }]
}>()

type BodyEditorTab = 'structure' | 'code' | 'preview' | 'explain' | 'problems'
const BODY_EDITOR_TABS: readonly BodyEditorTab[] = [
  'structure',
  'code',
  'preview',
  'explain',
  'problems',
]

declare const useCookie: UseCookieFactory | undefined

const useBodyEditorStorage = createNamespacedLocalStorageState('cbs-nova:body-editor')

const tab = useBodyEditorStorage<BodyEditorTab>('active-tab', 'structure', {
  useCookie:
    typeof useCookie !== 'undefined'
      ? (name, options) => useCookie<BodyEditorTab>(name, options)
      : undefined,
  read: (raw) => {
    if (raw === null) return undefined
    try {
      const parsed = JSON.parse(raw) as unknown
      return typeof parsed === 'string' && (BODY_EDITOR_TABS as readonly string[]).includes(parsed)
        ? (parsed as BodyEditorTab)
        : undefined
    } catch {
      return undefined
    }
  },
})

// stub — future: derive from construct introspection
const steps = ref<StepDef[]>([])

// Internal fallback body, used only when the caller doesn't pass `code`.
const internalCode = ref('')

const explainOutput = ref<RunnerOutput | null>(null)
const explainStatus = ref<RunnerStatus>('idle')

function errorOutput(err: unknown): RunnerOutput {
  const message = (err instanceof Error ? err.message : undefined) ?? 'Request failed'
  return { errors: [{ message, code: 'REQUEST_FAILED' }] }
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
    explainOutput.value = null
    explainStatus.value = 'idle'
    if (!isControlled.value) {
      internalCode.value = ''
    }
  },
  { immediate: true },
)

const codeTabRef = ref<InstanceType<typeof CodeTab> | null>(null)

function revealPosition(line: number, column = 1) {
  codeTabRef.value?.revealPosition(line, column)
}

function insertAtCursor(text: string) {
  tab.value = 'code'
  codeTabRef.value?.insertAtCursor(text)
}

// Pick a problem from the Problems tab — jump to the Code tab and reveal the offending line.
function selectProblem(payload: { index: number; error: ValidationError }) {
  emit('select', payload)
  const line = payload.error.line
  if (typeof line === 'number' && line > 0) {
    tab.value = 'code'
    codeTabRef.value?.revealPosition(line, payload.error.column ?? 1)
  }
}

defineExpose({ revealPosition, insertAtCursor, selectProblem })
</script>

<template>
  <div class="flex flex-col h-full bg-white" data-testid="body-editor">
    <div class="flex items-center border-b border-gray-200 px-2" data-testid="body-editor-tabs">
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'structure' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        data-testid="body-editor-tab-structure"
        @click="tab = 'structure'"
      >
        Structure
      </button>
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'code' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        data-testid="body-editor-tab-code"
        @click="tab = 'code'"
      >
        Code
      </button>
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'preview' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        data-testid="body-editor-tab-preview"
        @click="tab = 'preview'"
      >
        Preview
      </button>
      <button
        v-if="explain"
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'explain' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        data-testid="body-editor-tab-explain"
        @click="tab = 'explain'"
      >
        Explain
      </button>
      <button
        type="button"
        class="px-3 py-2 text-sm font-medium border-b-2 transition-colors"
        :class="tab === 'problems' ? 'border-blue-500 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'"
        data-testid="body-editor-tab-problems"
        @click="tab = 'problems'"
      >
        Problems
        <span
          v-if="props.errors.length"
          class="ml-1 inline-flex items-center justify-center min-w-[1.25rem] px-1 rounded-full text-xs"
          :class="tab === 'problems' ? 'bg-blue-100 text-blue-700' : 'bg-red-100 text-red-700'"
        >
          {{ props.errors.length }}
        </span>
      </button>
    </div>
    <div class="flex-1 overflow-auto" data-testid="body-editor-content">
      <StructureTab v-show="tab === 'structure'" :steps="steps" />
      <CodeTab
        ref="codeTabRef"
        v-show="tab === 'code'"
        v-model:code="bodyCode"
        :read-only="!construct"
        :save-status="saveStatus"
        :last-saved-at="lastSavedAt"
        :saved-hash="savedHash"
        :helper-catalog-fetch="helperCatalogFetch"
        :markers="markers"
        @save="emit('save', $event)"
      />
      <PreviewTab
        v-if="tab === 'preview'"
        :name="construct?.name ?? ''"
        :type="construct?.type as ConstructType | undefined"
        endpoint="preview"
        :preview="props.preview"
      />
      <ExplainTab
        v-if="tab === 'explain'"
        :output="explainOutput"
        :status="explainStatus"
        @run="runExplain"
      />
      <ProblemsPanel
        v-show="tab === 'problems'"
        :errors="props.errors"
        @select="selectProblem"
      />
    </div>
  </div>
</template>
