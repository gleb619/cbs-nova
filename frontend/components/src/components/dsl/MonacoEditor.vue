<script setup lang="ts">
import type * as Monaco from 'monaco-editor'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
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

const container = ref<HTMLElement | null>(null)

let monaco: typeof Monaco | undefined
let editor: Monaco.editor.IStandaloneCodeEditor | undefined
let destroyed = false
let releaseHelperCompletion: (() => void) | null = null

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

function severityFor(monacoNs: typeof Monaco, kind: EditorMarker['severity']): Monaco.MarkerSeverity {
  if (kind === 'warning') return monacoNs.MarkerSeverity.Warning
  return monacoNs.MarkerSeverity.Error
}

function toMonacoMarkers(monacoNs: typeof Monaco, markers: EditorMarker[]): Monaco.editor.IMarkerData[] {
  return markers.map((m) => {
    const startLineNumber = m.line && m.line > 0 ? m.line : 1
    const startColumn = m.column && m.column > 0 ? m.column : 1
    return {
      severity: severityFor(monacoNs, m.severity),
      message: m.message,
      startLineNumber,
      startColumn,
      endLineNumber: startLineNumber,
      endColumn: startColumn + 1,
    }
  })
}

function applyMarkers(markers: EditorMarker[] | undefined) {
  if (!monaco || !editor) return
  const model = editor.getModel()
  if (!model) return
  const list = markers ? toMonacoMarkers(monaco, markers) : []
  monaco.editor.setModelMarkers(model, MARKER_OWNER, list)
}

onMounted(async () => {
  installWorkerlessEnvironment()
  monaco = await import('monaco-editor')
  if (destroyed || !container.value) return

  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: props.language,
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
    theme: 'vs',
  })

  editor.onDidChangeModelContent(() => {
    const value = editor?.getValue() ?? ''
    if (value !== props.modelValue) emit('update:modelValue', value)
  })
  editor.onDidBlurEditorText(() => emit('blur'))

  if (props.helperCatalogFetch) {
    const { getCatalog } = useHelperCompletion({ fetch: props.helperCatalogFetch })
    releaseHelperCompletion = useMonacoHelperCompletion({
      monaco,
      getCatalog,
      language: props.language,
    })
  }

  applyMarkers(props.markers)
})

watch(
  () => props.modelValue,
  (value) => {
    if (editor && value !== editor.getValue()) editor.setValue(value ?? '')
  },
)

watch(
  () => props.readOnly,
  (readOnly) => editor?.updateOptions({ readOnly }),
)

watch(
  () => props.language,
  (language) => {
    const model = editor?.getModel()
    if (model && monaco && language) monaco.editor.setModelLanguage(model, language)
  },
)

watch(
  () => props.markers,
  (markers) => applyMarkers(markers),
  { deep: true },
)

onBeforeUnmount(() => {
  destroyed = true
  // Best-effort marker cleanup so an unmounted model never keeps stale markers.
  if (monaco && editor) {
    const model = editor.getModel()
    if (model) monaco.editor.setModelMarkers(model, MARKER_OWNER, [])
  }
  editor?.getModel()?.dispose()
  editor?.dispose()
  releaseHelperCompletion?.()
  releaseHelperCompletion = null
})

function revealPosition(line: number, column = 1): void {
  if (!editor || !line || line < 1) return
  const safeColumn = column && column > 0 ? column : 1
  editor.revealLineInCenter(line)
  editor.setPosition({ lineNumber: line, column: safeColumn })
  editor.focus()
}

defineExpose({ focus: () => editor?.focus(), revealPosition })
</script>

<template>
  <div ref="container" data-testid="monaco-editor" class="h-full w-full min-h-[300px] text-left" />
</template>
