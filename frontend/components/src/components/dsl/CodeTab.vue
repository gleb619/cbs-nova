<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCrc32 } from '../../composables/useCrc32'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { DslConstruct, HelperCatalogEntry } from '../../types/dsl'
import HotkeyTooltip from '../HotkeyTooltip.vue'
import MonacoEditor, { type EditorMarker } from './MonacoEditor.vue'
import SaveAction, { type AutosaveOption } from './SaveAction.vue'

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

const autosaveOptions: readonly AutosaveOption[] = [
  { value: 'off', label: 'Off' },
  { value: '5s', label: '5s' },
  { value: '30s', label: '30s' },
  { value: '1min', label: '1 min' },
  { value: 'blur', label: 'On blur' },
]

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
      <SaveAction
        testid-prefix="code-tab"
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
