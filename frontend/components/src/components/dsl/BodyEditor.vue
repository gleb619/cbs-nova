<script setup lang="ts">
import { computed, onBeforeUnmount, onBeforeUpdate, onMounted, ref } from 'vue'
import type { ConstructType } from '../../composables/useConstructSchema'
import {
  createNamespacedLocalStorageState,
  type UseCookieFactory,
} from '../../composables/useLocalStorageState'
import type {
  DiagnosticsPage,
  DslConstruct,
  HelperCatalogEntry,
  ObjectStructureDto,
  ValidationError,
} from '../../types/dsl'
import type { RunnerOutput } from '../../types/runner'
import CodeTab from './CodeTab.vue'
import ExplainTab from './ExplainTab.vue'
import type { EditorMarker } from './MonacoEditor.vue'
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
    constructsFetch?: () => Promise<DslConstruct[]>
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
    /** Validation errors — surfaced under the Problems tab badge and as markers. */
    errors?: ValidationError[]
    /** Fetcher for the persisted diagnostics that populate the Problems tab. */
    diagnosticsFetch?: (params: {
      definition?: string
      limit: number
      offset: number
    }) => Promise<DiagnosticsPage>
    /** Definition whose diagnostics should be shown in the Problems tab. */
    diagnosticsDefinition?: string
    /** Introspected structure of the selected DSL object (whole-object view). */
    structure?: ObjectStructureDto | null
    structureLoading?: boolean
    structureError?: string | null
  }>(),
  { markers: () => [], errors: () => [] },
)

const emit = defineEmits<{
  'update:code': [value: string]
  save: [value: string]
  select: [payload: { index: number; error: ValidationError }]
  'retry-structure': []
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
  useCookie: typeof useCookie !== 'undefined' ? useCookie : undefined,
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

const isControlled = computed(() => props.code !== undefined)

const codeEpoch = ref(0)
const lastEmittedCode = ref<string | null>(null)

const draftsByName = ref<Record<string, string>>({})
const draftName = computed(() => props.construct?.name ?? '')
const internalCode = computed<string>({
  get: () => draftsByName.value[draftName.value] ?? '',
  set: (value: string) => {
    draftsByName.value[draftName.value] = value
  },
})

const bodyCode = computed<string>({
  get: () => (isControlled.value ? (props.code ?? '') : internalCode.value),
  set: (value: string) => {
    if (isControlled.value) {
      lastEmittedCode.value = value
      emit('update:code', value)
    } else {
      internalCode.value = value
    }
  },
})

onBeforeUpdate(() => {
  if (props.code === undefined) return
  if (lastEmittedCode.value === null) {
    lastEmittedCode.value = props.code
    return
  }
  if (props.code !== lastEmittedCode.value) {
    codeEpoch.value++
    lastEmittedCode.value = props.code
  }
})

const codeTabRef = ref<InstanceType<typeof CodeTab> | null>(null)
const problemsCount = ref<number | null>(null)

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

function onProblemNavigate(payload: { line: number | null; column: number | null }) {
  if (typeof payload.line === 'number' && payload.line > 0) {
    tab.value = 'code'
    codeTabRef.value?.revealPosition(payload.line, payload.column ?? 1)
  }
}

function onProblemsCount(total: number) {
  problemsCount.value = total
}

const problemBadgeCount = computed(() => problemsCount.value ?? props.errors.length)

// Cross-component coordination uses window CustomEvents (emit/listen),
// not v-model/watch. The page dispatches `cbs:body-editor:set-tab` from a
// deep link; the editor subscribes while mounted and unsubscribes on
// teardown. Selector stays valid against BODY_EDITOR_TABS.
function handleSetTabEvent(event: Event) {
  const detail = (event as CustomEvent<BodyEditorTab>).detail
  if (typeof detail !== 'string') return
  if ((BODY_EDITOR_TABS as readonly string[]).includes(detail)) {
    tab.value = detail
  }
}

onMounted(() => {
  window.addEventListener('cbs:body-editor:set-tab', handleSetTabEvent)
})

onBeforeUnmount(() => {
  window.removeEventListener('cbs:body-editor:set-tab', handleSetTabEvent)
})

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
          v-if="problemBadgeCount"
          class="ml-1 inline-flex items-center justify-center min-w-[1.25rem] px-1 rounded-full text-xs"
          :class="tab === 'problems' ? 'bg-blue-100 text-blue-700' : 'bg-red-100 text-red-700'"
        >
          {{ problemBadgeCount }}
        </span>
      </button>
    </div>
    <div class="flex-1 overflow-auto" data-testid="body-editor-content">
      <StructureTab
        v-show="tab === 'structure'"
        :structure="structure"
        :structure-loading="structureLoading"
        :structure-error="structureError"
        @retry="emit('retry-structure')"
      />
      <CodeTab
        ref="codeTabRef"
        v-show="tab === 'code'"
        :key="`${construct?.name ?? 'none'}:${codeEpoch}`"
        v-model:code="bodyCode"
        :read-only="!construct"
        :save-status="saveStatus"
        :last-saved-at="lastSavedAt"
        :saved-hash="savedHash"
        :helper-catalog-fetch="helperCatalogFetch"
        :constructs-fetch="constructsFetch"
        :markers="markers"
        @save="emit('save', $event)"
      />
      <PreviewTab
        v-if="tab === 'preview'"
        :key="construct?.name ?? ''"
        :name="construct?.name ?? ''"
        :type="construct?.type as ConstructType | undefined"
        :preview="props.preview!"
      />
      <ExplainTab
        v-if="tab === 'explain'"
        :key="construct?.name ?? ''"
        :name="construct?.name ?? ''"
        :type="construct?.type as ConstructType | undefined"
        :explain="props.explain!"
      />
      <ProblemsPanel
        v-if="diagnosticsFetch && diagnosticsDefinition"
        v-show="tab === 'problems'"
        :fetch-page="diagnosticsFetch"
        :definition="diagnosticsDefinition"
        @navigate="onProblemNavigate"
        @count="onProblemsCount"
      />
    </div>
  </div>
</template>
