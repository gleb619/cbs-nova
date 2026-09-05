<script setup lang="ts">
import type * as Monaco from 'monaco-editor'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useHelperCompletion } from '../../composables/useHelperCompletion'
import { useMonacoHelperCompletion } from '../../composables/useMonacoHelperCompletion'
import type { HelperCatalogEntry } from '../../types/dsl'

const props = withDefaults(
  defineProps<{
    modelValue: string
    language?: string
    readOnly?: boolean
    placeholder?: string
    helperCatalogFetch?: () => Promise<HelperCatalogEntry[]>
  }>(),
  { language: 'java', readOnly: false, placeholder: '' },
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

onBeforeUnmount(() => {
  destroyed = true
  editor?.getModel()?.dispose()
  editor?.dispose()
  releaseHelperCompletion?.()
  releaseHelperCompletion = null
})

defineExpose({ focus: () => editor?.focus() })
</script>

<template>
  <div ref="container" data-testid="monaco-editor" class="h-full w-full min-h-[300px] text-left" />
</template>
