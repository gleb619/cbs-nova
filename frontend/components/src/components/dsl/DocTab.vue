<script setup lang="ts">
import { marked } from 'marked'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useCrc32 } from '../../composables/useCrc32'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { JsonSchema } from '../../types/jsonSchema'
import {
  createDefaultMarkdownDoc,
  DOC_NAME_KEY,
  parseMarkdownDoc,
  renameDocTitle,
  serializeMarkdownDoc,
} from '../../utils/markdownDoc'
import HotkeyTooltip from '../HotkeyTooltip.vue'
import MarkdownBodyEditor from './MarkdownBodyEditor.vue'
import MonacoEditor from './MonacoEditor.vue'
import SaveAction, { type AutosaveOption } from './SaveAction.vue'
import SchemaForm from './SchemaForm.vue'

const props = withDefaults(
  defineProps<{
    doc: string
    /**
     * Construct name of the doc. Used to seed a brand-new doc (frontmatter
     * `name` + a `# <name>` body heading) and to retitle the body when the
     * frontmatter name is edited in form mode.
     */
    name?: string
    readOnly?: boolean
    saveStatus?: string
    lastSavedAt?: Date | null
    savedHash?: number | null
    /** Reload the source backing the editor (e.g. refetch definitions). */
    refresh?: () => void | Promise<void>
    /** Run validation for the current construct and surface diagnostics. */
    validate?: () => void | Promise<void>
    /** Disable both refresh and validate (e.g. page is mid-save). */
    busy?: boolean
  }>(),
  {
    name: '',
    readOnly: false,
    saveStatus: 'idle',
    lastSavedAt: null,
    savedHash: null,
    refresh: undefined,
    validate: undefined,
    busy: false,
  },
)

const emit = defineEmits<{
  'update:doc': [value: string]
  save: [value: string]
}>()

const { calculateCrc32 } = useCrc32()

type DocMode = 'form' | 'raw' | 'view'
const DOC_MODES: readonly DocMode[] = ['form', 'raw', 'view']
const MODE_LABELS: Record<DocMode, string> = {
  form: 'Form',
  raw: 'Raw',
  view: 'View',
}

const useDocTabStorage = createNamespacedLocalStorageState('cbs-nova:doc-tab')
const mode = useDocTabStorage<DocMode>('mode', 'form', {
  read: (raw) => {
    if (raw === null) return undefined
    try {
      const parsed = JSON.parse(raw) as unknown
      return typeof parsed === 'string' && (DOC_MODES as readonly string[]).includes(parsed)
        ? (parsed as DocMode)
        : undefined
    } catch {
      return undefined
    }
  },
})

const AUTOSAVE_INTERVALS: Record<string, number> = {
  '5s': 5000,
  '30s': 30000,
  '1min': 60000,
}

const autosaveMode = useDocTabStorage<string>('autosave-mode', 'off')

const autosaveOptions: readonly AutosaveOption[] = [
  { value: 'off', label: 'Manual' },
  { value: '5s', label: '5s' },
  { value: '30s', label: '30s' },
  { value: '1min', label: '1 min' },
  { value: 'blur', label: 'On blur' },
]

const frontmatterSchema: JsonSchema = {
  type: 'object',
  properties: {
    [DOC_NAME_KEY]: { type: 'string' },
    description: { type: 'string' },
  },
  required: [DOC_NAME_KEY],
}

/**
 * A new doc starts from the canonical skeleton: the construct name in the
 * frontmatter (search) and a `# <name>` heading in the body (view). Existing
 * content — including a doc that already has a body but no frontmatter — is
 * left untouched.
 */
function seedNewDoc(current: string): string {
  if (current.trim() !== '') return current
  return props.name ? createDefaultMarkdownDoc(props.name) : current
}

const localDoc = ref(seedNewDoc(props.doc))

// The key changes with the selected construct (BodyEditor re-keys the tab), but
// guard anyway so a name change without a re-key still seeds a fresh doc.
watch(
  () => props.name,
  (next, previous) => {
    if (next && next !== previous) localDoc.value = seedNewDoc(props.doc)
  },
)

// Adopt docs that arrive from the parent (initial load, selection change,
// refresh): `doc` is only the starting value otherwise, so a slow read would
// leave the editor showing the seeded template. Never clobber local edits.
let lastSyncedDoc = props.doc
watch(
  () => props.doc,
  (next) => {
    if (next === lastSyncedDoc) return
    lastSyncedDoc = next
    if (isDirty.value) return
    localDoc.value = seedNewDoc(next)
    savedHashInternal.value = calculateCrc32(localDoc.value)
  },
)

const parsed = computed(() => parseMarkdownDoc(localDoc.value))

const renderedView = computed(() => {
  const body = parsed.value.body || ''
  return marked.parse(body, { async: false }) as string
})

function updateDoc(next: string) {
  localDoc.value = next
  // Our own emission coming back as a prop is not an external change.
  lastSyncedDoc = next
  emit('update:doc', next)
}

function updateFrontmatter(frontmatter: unknown) {
  const next = (frontmatter ?? {}) as Record<string, string>
  // The name lives twice (frontmatter for search, `# name` heading for view);
  // a form-mode rename retitles the heading when it still carries the old name.
  const body = renameDocTitle(
    parsed.value.body,
    parsed.value.frontmatter[DOC_NAME_KEY] ?? '',
    next[DOC_NAME_KEY] ?? '',
  )
  updateDoc(serializeMarkdownDoc({ frontmatter: next, body }))
}

function updateBody(next: string) {
  updateDoc(
    serializeMarkdownDoc({
      frontmatter: parsed.value.frontmatter,
      body: next,
    }),
  )
}

// Baseline covers the seeded skeleton too, so a brand-new doc reads as clean
// and a doc pushed by the parent is still adopted.
const savedHashInternal = ref(calculateCrc32(localDoc.value))
const localHash = computed(() => calculateCrc32(localDoc.value))
const baselineHash = computed(() =>
  typeof props.savedHash === 'number' ? props.savedHash : savedHashInternal.value,
)
const isDirty = computed(() => localHash.value !== baselineHash.value)

function requestSave() {
  savedHashInternal.value = localHash.value
  emit('save', localDoc.value)
}

function handleGlobalSave() {
  if (!props.readOnly && !props.busy) requestSave()
}

let autosaveTimer: ReturnType<typeof setInterval> | undefined

function clearAutosaveTimer() {
  if (autosaveTimer !== undefined) {
    clearInterval(autosaveTimer)
    autosaveTimer = undefined
  }
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

function handleBlur() {
  if (autosaveMode.value === 'blur' && isDirty.value) requestSave()
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

const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')

// Platform-aware modifier label: ⌘ on Mac, Ctrl elsewhere.
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
  if (isEditableTarget(event.target)) return

  const key = event.key.toLowerCase()

  if (key === 's' && !event.shiftKey) {
    event.preventDefault()
    if (props.busy) return
    requestSave()
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
  applyAutosaveMode()
})
onBeforeUnmount(() => {
  window.removeEventListener('dsl:save', handleGlobalSave)
  window.removeEventListener('keydown', handleHotkey)
  clearAutosaveTimer()
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="doc-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="doc-tab-header"
    >
      <HotkeyTooltip :keys="refreshShortcut">
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border border-neutral-300 text-neutral-700 hover:bg-neutral-100 disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="refreshDisabled"
          data-testid="doc-tab-refresh"
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
          data-testid="doc-tab-validate"
          @click="runValidate"
        >
          Validate
        </button>
      </HotkeyTooltip>
      <div class="ml-auto flex items-center gap-2">
        <SaveAction
          testid-prefix="doc-tab"
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
    <div class="flex-1 min-h-0 overflow-auto">
      <div
        v-if="mode === 'form'"
        class="flex flex-col gap-4 p-3"
        data-testid="doc-tab-form"
        @focusout="handleBlur"
      >
        <SchemaForm
          :schema="frontmatterSchema"
          :model-value="parsed.frontmatter"
          :readonly="readOnly"
          @update:model-value="updateFrontmatter"
        />
        <fieldset class="flex flex-col gap-1 border-0 p-0 m-0">
          <legend class="text-xs text-ink-muted px-0">Body</legend>
          <MarkdownBodyEditor :body="parsed.body" :read-only="readOnly" @update:body="updateBody" />
        </fieldset>
      </div>
      <div
        v-if="mode === 'raw'"
        class="p-3 h-full min-h-[300px]"
        data-testid="doc-tab-raw"
        @focusout="handleBlur"
      >
        <div
          class="relative h-full min-h-[300px] overflow-hidden rounded border border-neutral-200 bg-white"
        >
          <MonacoEditor
            :model-value="localDoc"
            language="markdown"
            :read-only="readOnly"
            :placeholder="readOnly ? 'No documentation available' : 'Write documentation here...'"
            @update:model-value="updateDoc"
          />
        </div>
      </div>
      <div
        v-if="mode === 'view'"
        class="p-6 h-full overflow-auto bg-white"
        data-testid="doc-tab-view"
      >
        <article class="max-w-3xl mx-auto">
          <header
            v-if="parsed.frontmatter.name || parsed.frontmatter.description"
            class="mb-6 pb-4 border-b border-neutral-200"
            data-testid="doc-tab-view-header"
          >
            <h1
              v-if="parsed.frontmatter.name"
              class="text-2xl font-semibold text-neutral-900"
              data-testid="doc-tab-view-name"
            >
              {{ parsed.frontmatter.name }}
            </h1>
            <p
              v-if="parsed.frontmatter.description"
              class="mt-1 text-sm text-neutral-600"
              data-testid="doc-tab-view-description"
            >
              {{ parsed.frontmatter.description }}
            </p>
          </header>
          <div
            class="prose prose-sm max-w-none text-neutral-800"
            data-testid="doc-tab-view-body"
            v-html="renderedView"
          />
          <footer
            v-if="!readOnly"
            class="mt-6 pt-4 border-t border-neutral-200 text-xs text-neutral-500"
            data-testid="doc-tab-view-footer"
          >
            <span>{{ parsed.body.split(/\s+/).filter(Boolean).length }} words</span>
            <span class="mx-2">·</span>
            <span>{{ parsed.body.length }} chars</span>
          </footer>
        </article>
      </div>
    </div>

    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-t border-neutral-200 bg-white"
      data-testid="doc-tab-footer"
    >
      <div class="flex items-center rounded border border-neutral-300 overflow-hidden">
        <button
          v-for="option in DOC_MODES"
          :key="option"
          type="button"
          class="px-3 py-1 text-xs font-medium"
          :class="mode === option
            ? 'bg-neutral-800 text-white'
            : 'text-neutral-600 hover:bg-neutral-100'"
          :data-testid="`doc-tab-mode-${option}`"
          :aria-pressed="mode === option"
          @click="mode = option"
        >
          {{ MODE_LABELS[option] }}
        </button>
      </div>
    </div>
  </div>
</template>
