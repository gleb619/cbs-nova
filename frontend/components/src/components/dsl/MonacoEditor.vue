<script setup lang="ts">
import type * as Monaco from 'monaco-editor'
import { type Component, computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useHelperCompletion } from '../../composables/useHelperCompletion'
import { useMonacoHelperCompletion } from '../../composables/useMonacoHelperCompletion'
import type { HelperCatalogEntry } from '../../types/dsl'

/** Monaco marker owner — shared between every code tab instance. */
const MARKER_OWNER = 'dsl'

export interface EditorMarker {
  /** 1-based source line; `null`/omitted lands the marker at line 1. */
  line?: number | null
  /** 1-based source column; `null`/omitted lands the marker at column 1. */
  column?: number | null
  message: string
  severity: 'error' | 'warning'
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: string
    readOnly?: boolean
    placeholder?: string
    helperCatalogFetch?: () => Promise<HelperCatalogEntry[]>
    /** Inline diagnostic markers — rendered via `monaco.editor.setModelMarkers`. */
    markers?: EditorMarker[]
  }>(),
  { language: 'java', readOnly: false, placeholder: '', markers: () => [] },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: []
}>()

const editor = ref<Monaco.editor.IStandaloneCodeEditor | null>(null)
let monaco: typeof Monaco | null = null
const codeEditorComponent = shallowRef<Component | null>(null)
let releaseHelperCompletion: (() => void) | null = null
let disposed = false

function installWorkerlessEnvironment() {
  const globalScope = self as unknown as { MonacoEnvironment?: unknown }
  if (globalScope.MonacoEnvironment) return
  try {
    const source = 'self.onmessage = () => {}'
    const url = URL.createObjectURL(new Blob([source], { type: 'text/javascript' }))
    globalScope.MonacoEnvironment = { getWorker: () => new Worker(url) }
  } catch {
    globalScope.MonacoEnvironment = { getWorker: () => undefined as unknown as Worker }
  }
}

async function loadEditor() {
  const monacoApi = await import('monaco-editor')
  const { CodeEditor } = await import('monaco-editor-vue3')
  if (disposed) return
  monaco = monacoApi
  installWorkerlessEnvironment()
  codeEditorComponent.value = CodeEditor as Component
}

onMounted(() => {
  void loadEditor()
})

const editorOptions = computed<Monaco.editor.IStandaloneEditorConstructionOptions>(() => ({
  readOnly: props.readOnly,
  placeholder: props.placeholder,
  lineNumbers: 'on',
  minimap: { enabled: false },
  automaticLayout: true,
  scrollBeyondLastLine: false,
  fontSize: 12,
  tabSize: 2,
  wordWrap: 'off',
  renderWhitespace: 'none',
  fixedOverflowWidgets: true,
}))

function applyMarkers(markers: EditorMarker[] | undefined) {
  const instance = editor.value
  const api = monaco
  if (!instance || !api) return
  const model = instance.getModel()
  if (!model) return
  const list = (markers ?? []).map((m) => {
    const startLineNumber = m.line && m.line > 0 ? m.line : 1
    const startColumn = m.column && m.column > 0 ? m.column : 1
    return {
      severity: m.severity === 'warning' ? api.MarkerSeverity.Warning : api.MarkerSeverity.Error,
      message: m.message,
      startLineNumber,
      startColumn,
      endLineNumber: startLineNumber,
      endColumn: startColumn + 1,
    }
  })
  api.editor.setModelMarkers(model, MARKER_OWNER, list)
}

function onEditorDidMount(instance: Monaco.editor.IStandaloneCodeEditor) {
  const api = monaco
  if (!api) return
  editor.value = instance
  instance.onDidBlurEditorText(() => emit('blur'))

  if (props.helperCatalogFetch) {
    const { getCatalog } = useHelperCompletion({ fetch: props.helperCatalogFetch })
    releaseHelperCompletion = useMonacoHelperCompletion({
      monaco: api,
      getCatalog,
      language: props.language,
    })
  }

  applyMarkers(props.markers)
}

// `monaco-editor-vue3` does not react to `language` changes — keep model
// language in sync here.
watch(
  () => props.language,
  (language) => {
    const model = editor.value?.getModel()
    if (model && language) monaco?.editor.setModelLanguage(model, language)
  },
)

watch(
  () => props.markers,
  (markers) => applyMarkers(markers),
)

onBeforeUnmount(() => {
  disposed = true
  const model = editor.value?.getModel()
  if (model) {
    monaco?.editor.setModelMarkers(model, MARKER_OWNER, [])
    model.dispose()
  }
  releaseHelperCompletion?.()
  releaseHelperCompletion = null
  editor.value = null
})

function revealPosition(line: number, column = 1): void {
  const instance = editor.value
  if (!instance || !line || line < 1) return
  const safeColumn = column && column > 0 ? column : 1
  instance.revealLineInCenter(line)
  instance.setPosition({ lineNumber: line, column: safeColumn })
  instance.focus()
}

function insertAtCursor(text: string): void {
  const instance = editor.value
  if (!instance || !text) return
  const selection = instance.getSelection()
  const position = selection ? null : instance.getPosition()
  const range = selection
    ? {
        startLineNumber: selection.startLineNumber,
        startColumn: selection.startColumn,
        endLineNumber: selection.endLineNumber,
        endColumn: selection.endColumn,
      }
    : {
        startLineNumber: position?.lineNumber ?? 1,
        startColumn: position?.column ?? 1,
        endLineNumber: position?.lineNumber ?? 1,
        endColumn: position?.column ?? 1,
      }
  instance.executeEdits('helper-insert', [{ range, text, forceMoveMarkers: true }])
  instance.focus()
}

defineExpose({ focus: () => editor.value?.focus(), revealPosition, insertAtCursor })
</script>

<template>
  <div data-testid="monaco-editor" class="h-full w-full min-h-[300px] text-left">
    <component
      :is="codeEditorComponent"
      v-if="codeEditorComponent"
      :value="modelValue"
      :language="language"
      :options="editorOptions"
      theme="vs"
      @update:value="emit('update:modelValue', $event)"
      @editor-did-mount="onEditorDidMount"
    />
  </div>
</template>
