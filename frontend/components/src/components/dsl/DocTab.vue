<script setup lang="ts">
import { marked } from 'marked'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCrc32 } from '../../composables/useCrc32'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { JsonSchema } from '../../types/jsonSchema'
import { parseMarkdownDoc, serializeMarkdownDoc } from '../../utils/markdownDoc'
import MarkdownBodyEditor from './MarkdownBodyEditor.vue'
import MonacoEditor from './MonacoEditor.vue'
import SaveAction, { type AutosaveOption } from './SaveAction.vue'
import SchemaForm from './SchemaForm.vue'

const props = withDefaults(
  defineProps<{
    doc: string
    readOnly?: boolean
    saveStatus?: string
    lastSavedAt?: Date | null
    savedHash?: number | null
    busy?: boolean
  }>(),
  {
    readOnly: false,
    saveStatus: 'idle',
    lastSavedAt: null,
    savedHash: null,
    busy: false,
  },
)

const emit = defineEmits<{
  'update:doc': [value: string]
  save: [value: string]
}>()

const { calculateCrc32 } = useCrc32()

const localDoc = ref(props.doc)

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
    name: { type: 'string' },
    description: { type: 'string' },
  },
  required: ['name'],
}

const parsed = computed(() => parseMarkdownDoc(localDoc.value))

const renderedView = computed(() => {
  const body = parsed.value.body || ''
  return marked.parse(body, { async: false }) as string
})

function updateDoc(next: string) {
  localDoc.value = next
  emit('update:doc', next)
}

function updateFrontmatter(frontmatter: unknown) {
  updateDoc(
    serializeMarkdownDoc({
      frontmatter: (frontmatter ?? {}) as Record<string, string>,
      body: parsed.value.body,
    }),
  )
}

function updateBody(next: string) {
  updateDoc(
    serializeMarkdownDoc({
      frontmatter: parsed.value.frontmatter,
      body: next,
    }),
  )
}

const savedHashInternal = ref(calculateCrc32(props.doc))
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

const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

function handleHotkey(event: KeyboardEvent) {
  if (props.readOnly || props.busy) return
  const mod = isMac ? event.metaKey : event.ctrlKey
  if (!mod || event.shiftKey || event.key.toLowerCase() !== 's') return
  if (isEditableTarget(event.target)) return
  event.preventDefault()
  requestSave()
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
      <div class="ml-auto flex items-center gap-2">
        <SaveAction
          testid-prefix="doc-tab"
          :is-dirty="isDirty"
          :busy="busy"
          :save-status="saveStatus"
          :last-saved-at="lastSavedAt"
          :autosave-options="autosaveOptions"
          :autosave-mode="autosaveMode"
          @save="requestSave"
          @update:autosave-mode="setAutosaveMode"
        />
      </div>
    </div>
    <div class="flex-1 min-h-0 overflow-auto">
      <div v-if="mode === 'form'" class="flex flex-col gap-4 p-3" data-testid="doc-tab-form" @focusout="handleBlur">
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
      <div v-if="mode === 'raw'" class="p-3 h-full min-h-[300px]" data-testid="doc-tab-raw" @focusout="handleBlur">
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
