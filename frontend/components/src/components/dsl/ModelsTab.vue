<script setup lang="ts">
import { computed, ref } from 'vue'
import { useEditorTabState } from '../../composables/useEditorTabState'
import type { DslConstruct, HelperCatalogEntry } from '../../types/dsl'
import HotkeyTooltip from '../HotkeyTooltip.vue'
import MonacoEditor, { type EditorMarker } from './MonacoEditor.vue'
import SaveAction, { type AutosaveOption } from './SaveAction.vue'

const props = withDefaults(
  defineProps<{
    /** Java source for the model classes — re-keyed by construct name when selected. */
    code: string
    /** Display name for the header ("Models for {name}"). */
    name?: string
    readOnly?: boolean
    saveStatus?: string
    lastSavedAt?: Date | null
    savedHash?: number | null
    helperCatalogFetch?: () => Promise<HelperCatalogEntry[]>
    constructsFetch?: () => Promise<DslConstruct[]>
    markers?: EditorMarker[]
    /** Reload the source backing the editor (e.g. refetch definitions). */
    refresh?: () => void | Promise<void>
    /** Run validation for the current construct and surface diagnostics. */
    validate?: () => void | Promise<void>
    /** Disable both refresh and validate (e.g. page is mid-save). */
    busy?: boolean
  }>(),
  {
    name: '',
    saveStatus: 'idle',
    lastSavedAt: null,
    savedHash: null,
    markers: () => [],
    refresh: undefined,
    validate: undefined,
    busy: false,
  },
)

const emit = defineEmits<{
  'update:code': [value: string]
  save: [value: string]
}>()

const placeholder = computed(() =>
  props.readOnly ? 'No models available' : 'Write model classes here...',
)

const autosaveOptions: readonly AutosaveOption[] = [
  { value: 'off', label: 'Manual' },
  { value: '5s', label: '5s' },
  { value: '30s', label: '30s' },
  { value: '1min', label: '1 min' },
  { value: 'blur', label: 'On blur' },
]

const {
  localContent: localCode,
  setLocalContent,
  isDirty,
  mode: autosaveMode,
  setAutosaveMode,
  requestSave,
  handleBlur,
} = useEditorTabState({
  initialContent: props.code,
  namespace: 'cbs-nova:models-tab',
  onSave: (value) => emit('save', value),
  onChange: (value) => emit('update:code', value),
  isReadOnly: () => props.readOnly,
  isBusy: () => props.busy,
  onRefresh: () => props.refresh?.(),
  onValidate: () => props.validate?.(),
})

const headerLabel = computed(() => (props.name ? `Models for ${props.name}` : 'Models'))

const refreshDisabled = computed(() => props.busy || !props.refresh)
const validateDisabled = computed(() => props.busy || !props.validate)

function runRefresh() {
  if (props.busy || !props.refresh) return
  void props.refresh()
}

function runValidate() {
  if (props.busy || !props.validate) return
  void props.validate()
}

// Platform-aware modifier label: ⌘ on Mac, Ctrl elsewhere.
const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')

const MOD = computed(() => (isMac ? '⌘' : 'Ctrl'))

const saveShortcut = computed(() => `${MOD.value}+S`)
const refreshShortcut = computed(() => `${MOD.value}+Shift+R`)
const validateShortcut = computed(() => `${MOD.value}+Enter`)

function onEditorInput(value: string) {
  setLocalContent(value)
}

const monacoRef = ref<InstanceType<typeof MonacoEditor> | null>(null)

function revealPosition(line: number, column = 1) {
  monacoRef.value?.revealPosition(line, column)
}

function insertAtCursor(text: string) {
  monacoRef.value?.insertAtCursor(text)
}

defineExpose({ revealPosition, insertAtCursor })
</script>

<template>
  <div class="flex flex-col h-full" data-testid="models-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="models-tab-toolbar"
    >
      <span
        class="px-2 py-1 text-xs font-medium rounded bg-slate-100 text-slate-700"
        data-testid="models-tab-header-label"
      >
        {{ headerLabel }}
      </span>
      <HotkeyTooltip :keys="refreshShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="refreshDisabled"
          data-testid="models-tab-refresh"
          @click="runRefresh"
        >
          Refresh
        </button>
      </HotkeyTooltip>
      <HotkeyTooltip :keys="validateShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="validateDisabled"
          data-testid="models-tab-validate"
          @click="runValidate"
        >
          Validate
        </button>
      </HotkeyTooltip>
      <div class="ml-auto flex items-center gap-2">
        <SaveAction
          testid-prefix="models-tab"
          :is-dirty="isDirty"
          :busy="busy"
          :save-status="saveStatus"
          :last-saved-at="lastSavedAt"
          :autosave-options="autosaveOptions"
          :autosave-mode="autosaveMode"
          :save-shortcut="saveShortcut"
          @save="requestSave"
          @update:autosave-mode="setAutosaveMode"
        />
      </div>
    </div>
    <div class="p-3 flex-1 min-h-0">
      <div
        data-testid="models-tab-editor"
        class="relative h-full min-h-[300px] overflow-hidden rounded border border-neutral-200"
        :class="readOnly ? 'bg-neutral-50' : 'bg-white'"
      >
        <MonacoEditor
          ref="monacoRef"
          :model-value="localCode"
          language="java"
          :read-only="readOnly"
          :placeholder="placeholder"
          :helper-catalog-fetch="helperCatalogFetch"
          :constructs-fetch="constructsFetch"
          :markers="markers"
          @update:model-value="onEditorInput"
          @blur="handleBlur"
        />
      </div>
    </div>
  </div>
</template>
