<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCrc32 } from '../../composables/useCrc32'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { DslConstruct, HelperCatalogEntry } from '../../types/dsl'
import HotkeyTooltip from '../HotkeyTooltip.vue'
import MonacoEditor, { type EditorMarker } from './MonacoEditor.vue'

const props = withDefaults(
  defineProps<{
    code: string
    readOnly?: boolean
    language?: string
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
    language: 'java',
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

const { calculateCrc32 } = useCrc32()

const localCode = ref(props.code)

const placeholder = computed(() => (props.readOnly ? 'No code available' : 'Write DSL here...'))

const AUTOSAVE_INTERVALS: Record<string, number> = {
  '5s': 5000,
  '30s': 30000,
  '1min': 60000,
}

const useCodeTabStorage = createNamespacedLocalStorageState('cbs-nova:code-tab')
const autosaveMode = useCodeTabStorage<string>('autosave-mode', 'off')

const savedHashInternal = ref(calculateCrc32(props.code))
const localHash = computed(() => calculateCrc32(localCode.value))
const baselineHash = computed(() =>
  typeof props.savedHash === 'number' ? props.savedHash : savedHashInternal.value,
)
const isDirty = computed(() => localHash.value !== baselineHash.value)

let autosaveTimer: ReturnType<typeof setInterval> | undefined

function clearAutosaveTimer() {
  if (autosaveTimer !== undefined) {
    clearInterval(autosaveTimer)
    autosaveTimer = undefined
  }
}

function requestSave() {
  savedHashInternal.value = localHash.value
  emit('save', localCode.value)
}

function applyAutosaveMode() {
  clearAutosaveTimer()
  const interval = AUTOSAVE_INTERVALS[autosaveMode.value]
  if (interval !== undefined && !props.readOnly) {
    autosaveTimer = setInterval(() => {
      if (isDirty.value) requestSave()
    }, interval)
  }
}

function setAutosaveMode(mode: string) {
  autosaveMode.value = mode
  applyAutosaveMode()
}

onMounted(applyAutosaveMode)
onBeforeUnmount(clearAutosaveTimer)

function handleBlur() {
  if (autosaveMode.value === 'blur' && isDirty.value) requestSave()
}

function handleGlobalSave() {
  if (!props.readOnly && isDirty.value) requestSave()
}

function runRefresh() {
  if (props.busy || !props.refresh) return
  void props.refresh()
}

function runValidate() {
  if (props.busy || !props.validate) return
  void props.validate()
}

const refreshDisabled = computed(() => props.busy || !props.refresh)
const validateDisabled = computed(() => props.busy || !props.validate)

// Platform-aware modifier label: ⌘ on Mac, Ctrl elsewhere.
const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')

const MOD = computed(() => (isMac ? '⌘' : 'Ctrl'))

const saveShortcut = computed(() => `${MOD.value}+S`)
const refreshShortcut = computed(() => `${MOD.value}+Shift+R`)
const validateShortcut = computed(() => `${MOD.value}+Enter`)

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

function handleHotkey(event: KeyboardEvent) {
  if (props.readOnly) return
  const mod = isMac ? event.metaKey : event.ctrlKey
  if (!mod) return
  // Skip when an editable element owns the key (e.g. Monaco textarea, native inputs).
  if (isEditableTarget(event.target)) return

  const key = event.key.toLowerCase()

  if (key === 's' && !event.shiftKey) {
    event.preventDefault()
    if (props.busy) return
    if (isDirty.value) requestSave()
    return
  }

  if (key === 'r' && event.shiftKey) {
    event.preventDefault()
    runRefresh()
    return
  }

  if (key === 'enter') {
    event.preventDefault()
    runValidate()
  }
}

onMounted(() => {
  window.addEventListener('dsl:save', handleGlobalSave)
  window.addEventListener('keydown', handleHotkey)
})
onBeforeUnmount(() => {
  window.removeEventListener('dsl:save', handleGlobalSave)
  window.removeEventListener('keydown', handleHotkey)
})

const autosaveOptions = [
  { value: 'off', label: 'Off' },
  { value: '5s', label: '5s' },
  { value: '30s', label: '30s' },
  { value: '1min', label: '1 min' },
  { value: 'blur', label: 'On blur' },
]

function formatSavedTime(date: Date | null): string {
  if (!date) return ''
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000)
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  return `${Math.floor(minutes / 60)}h ago`
}

const saveStatusClasses = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'bg-amber-50 text-amber-700 border border-amber-200'
    case 'saving':
      return 'bg-blue-50 text-blue-700 border border-blue-200'
    case 'saved':
      return 'bg-green-50 text-green-700 border border-green-200'
    case 'error':
      return 'bg-red-50 text-red-700 border border-red-200'
    default:
      return 'text-gray-500'
  }
})

const saveStatusText = computed(() => {
  switch (props.saveStatus) {
    case 'dirty':
      return 'Unsaved changes'
    case 'saving':
      return 'Saving…'
    case 'saved':
      return `Saved ${formatSavedTime(props.lastSavedAt ?? null)}`
    case 'error':
      return 'Save failed — Retry'
    default:
      return props.lastSavedAt ? 'Saved' : ''
  }
})

function onEditorInput(value: string) {
  localCode.value = value
  emit('update:code', value)
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
  <div class="flex flex-col h-full" data-testid="code-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="code-tab-toolbar"
    >
      <HotkeyTooltip :keys="refreshShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="refreshDisabled"
          data-testid="code-tab-refresh"
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
          data-testid="code-tab-validate"
          @click="runValidate"
        >
          Validate
        </button>
      </HotkeyTooltip>
      <HotkeyTooltip :keys="saveShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border"
          :class="isDirty
            ? 'border-blue-600 bg-blue-600 text-white hover:bg-blue-700'
            : 'border-neutral-300 text-neutral-400 cursor-not-allowed'"
          :disabled="!isDirty"
          data-testid="code-tab-save"
          @click="requestSave"
        >
          Save
        </button>
      </HotkeyTooltip>
      <div class="flex items-center rounded border border-neutral-300 overflow-hidden">
        <button
          v-for="option in autosaveOptions"
          :key="option.value"
          type="button"
          class="px-2 py-1 text-xs"
          :class="autosaveMode === option.value
            ? 'bg-neutral-800 text-white'
            : 'text-neutral-600 hover:bg-neutral-100'"
          :data-testid="`code-tab-autosave-${option.value}`"
          :aria-pressed="autosaveMode === option.value"
          @click="setAutosaveMode(option.value)"
        >
          {{ option.label }}
        </button>
      </div>
      <span
        v-if="saveStatusText"
        class="inline-flex items-center gap-1.5 text-xs rounded-full px-2.5 py-1"
        :class="saveStatusClasses"
        data-testid="draft-save-status"
        role="status"
      >
        <span
          v-if="saveStatus === 'saving'"
          class="inline-block h-3 w-3 rounded-full border-2 border-current border-t-transparent animate-spin"
          aria-hidden="true"
        ></span>
        <span>{{ saveStatusText }}</span>
        <button
          v-if="saveStatus === 'error'"
          type="button"
          class="ml-1 underline hover:no-underline"
          data-testid="draft-save-retry"
          @click="requestSave"
        >
          Retry
        </button>
      </span>
      <span
        v-if="isDirty"
        class="inline-flex items-center gap-1.5 text-xs text-amber-700"
        data-testid="workbench-dirty-indicator"
        role="status"
        aria-label="You have unsaved changes"
      >
        <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
        <span>unsaved changes</span>
      </span>
    </div>
    <div class="p-3 flex-1 min-h-0">
      <div
        data-testid="code-tab-editor"
        class="relative h-full min-h-[300px] overflow-hidden rounded border"
        :class="readOnly ? 'border-neutral-200 bg-neutral-50' : 'border-neutral-200 bg-white'"
      >
        <MonacoEditor
          ref="monacoRef"
          :model-value="localCode"
          :language="language"
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
