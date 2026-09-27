<script setup lang="ts">
import { marked } from 'marked'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCrc32 } from '../../composables/useCrc32'
import { createNamespacedLocalStorageState } from '../../composables/useLocalStorageState'
import type { JsonSchema } from '../../types/jsonSchema'
import { parseMarkdownDoc, serializeMarkdownDoc } from '../../utils/markdownDoc'
import MarkdownBodyEditor from './MarkdownBodyEditor.vue'
import MonacoEditor from './MonacoEditor.vue'
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
  if (!props.readOnly && !props.busy && isDirty.value) requestSave()
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
  if (isDirty.value) requestSave()
}

onMounted(() => {
  window.addEventListener('dsl:save', handleGlobalSave)
  window.addEventListener('keydown', handleHotkey)
})
onBeforeUnmount(() => {
  window.removeEventListener('dsl:save', handleGlobalSave)
  window.removeEventListener('keydown', handleHotkey)
})

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
    case 'error':
      return 'Save failed — Retry'
    default:
      return ''
  }
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="doc-tab">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-2 px-3 py-1.5 border-b border-neutral-200 bg-white"
      data-testid="doc-tab-header"
    >
      <div class="flex items-center gap-2 min-w-0">
        <span
          class="text-xs font-semibold uppercase tracking-wide text-neutral-700"
          data-testid="doc-tab-header-title"
        >
          Documentation
        </span>
        <span
          v-if="parsed.frontmatter.name"
          class="text-xs text-neutral-500 truncate"
          data-testid="doc-tab-header-name"
        >
          · {{ parsed.frontmatter.name }}
        </span>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <span
          v-if="saveStatusText"
          class="inline-flex items-center gap-1.5 text-xs rounded-full px-2.5 py-1"
          :class="saveStatusClasses"
          data-testid="doc-tab-save-status"
          role="status"
        >
          <span>{{ saveStatusText }}</span>
          <button
            v-if="saveStatus === 'error'"
            type="button"
            class="ml-1 underline hover:no-underline"
            data-testid="doc-tab-save-retry"
            @click="requestSave"
          >
            Retry
          </button>
        </span>
        <span
          v-if="isDirty"
          class="inline-flex items-center gap-1.5 text-xs text-amber-700"
          data-testid="doc-tab-dirty-indicator"
          role="status"
          aria-label="You have unsaved changes"
        >
          <span aria-hidden="true" class="inline-block h-2 w-2 rounded-full bg-amber-500"></span>
          <span>unsaved</span>
        </span>
        <button
          type="button"
          class="px-3 py-1 text-xs font-medium rounded border"
          :class="isDirty
            ? 'border-blue-600 bg-blue-600 text-white hover:bg-blue-700'
            : 'border-neutral-300 text-neutral-400 cursor-not-allowed'"
          :disabled="!isDirty || busy"
          data-testid="doc-tab-save"
          @click="requestSave"
        >
          Save
        </button>
      </div>
    </div>
    <div class="flex-1 min-h-0 overflow-auto">
      <div v-if="mode === 'form'" class="flex flex-col gap-4 p-3" data-testid="doc-tab-form">
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
      <div v-if="mode === 'raw'" class="p-3 h-full min-h-[300px]" data-testid="doc-tab-raw">
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
