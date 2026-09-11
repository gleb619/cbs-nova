<script setup lang="ts">
import { computed, onBeforeUpdate, ref } from 'vue'
import {
  type ConstructType,
  generateFakeValue,
  useConstructSchema,
} from '../../composables/useConstructSchema'
import SchemaForm from './SchemaForm.vue'

const props = defineProps<{
  name: string
  type?: ConstructType
  endpoint?: 'preview' | 'run' | 'explain'
  modelValue: string
  busy?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: []
}>()

const text = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const mode = ref<'form' | 'json' | 'schema'>('json')
let previousModelValue = props.modelValue

const { schema, inputType, loading, error, hasSchema } = useConstructSchema({
  name: () => props.name,
  type: () => props.type,
})

const canUseForm = computed(() => {
  return !error.value && hasSchema.value && schema.value != null
})

const parseError = computed(() => {
  const v = text.value
  if (!v.trim()) return null
  try {
    JSON.parse(v)
    return null
  } catch (e) {
    return (e as Error).message
  }
})

const effectiveMode = computed(() => {
  if (mode.value === 'form' && !canUseForm.value) return 'json'
  if (mode.value === 'schema' && !hasSchema.value) return 'json'
  return mode.value
})

const formValue = ref<unknown>(undefined)

function parseTextToForm(): unknown {
  if (!text.value.trim()) return {}
  try {
    return JSON.parse(text.value)
  } catch {
    return {}
  }
}

function syncJsonToForm() {
  formValue.value = parseTextToForm()
}

function syncFormToJson() {
  try {
    text.value = `${JSON.stringify(formValue.value ?? {}, null, 2)}\n`
  } catch (_e) {
    // ignore serialization failures from unserializable values
  }
}

function format() {
  try {
    text.value = `${JSON.stringify(JSON.parse(text.value), null, 2)}\n`
  } catch (_e) {
    // leave invalid JSON as-is; parseError already reflects the error
  }
}

function generate() {
  if (!schema.value) return
  formValue.value = generateFakeValue(schema.value)
  syncFormToJson()
}

function setMode(next: 'form' | 'json' | 'schema') {
  if (next === mode.value) return
  if (next === 'form') {
    syncJsonToForm()
    mode.value = 'form'
  } else if (next === 'json') {
    if (mode.value === 'form') {
      syncFormToJson()
    }
    mode.value = 'json'
  } else {
    mode.value = 'schema'
  }
}

function onFormUpdate(value: unknown) {
  formValue.value = value
  syncFormToJson()
}

onBeforeUpdate(() => {
  if (props.modelValue !== previousModelValue) {
    previousModelValue = props.modelValue
    if (effectiveMode.value === 'form') {
      syncJsonToForm()
    }
  }
})

const hasFormError = computed(() => {
  if (effectiveMode.value === 'json') return !!parseError.value
  return false
})

const footerStatus = computed(() => {
  if (parseError.value)
    return { text: `Invalid JSON: ${parseError.value}`, type: 'danger' as const }
  if (error.value)
    return { text: `Schema unavailable — JSON only: ${error.value}`, type: 'danger' as const }
  if (loading.value) return { text: 'Loading schema…', type: 'muted' as const }
  if (effectiveMode.value === 'form') return { text: 'Form input', type: 'muted' as const }
  if (effectiveMode.value === 'schema') return { text: 'Input schema', type: 'muted' as const }
  return { text: 'Valid JSON', type: 'muted' as const }
})
</script>

<template>
  <section class="flex flex-col h-full min-h-0 border border-line rounded-sm bg-white">
    <header class="flex items-center justify-between px-3 py-2 border-b border-line">
      <div class="flex items-center gap-2 min-w-0">
        <span class="text-xs font-medium text-ink">Input</span>
        <span class="font-mono text-xs text-ink truncate">{{ inputType ?? name }}</span>
        <span class="text-xs text-ink-muted">| {{ endpoint ?? 'preview' }}</span>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <button
          type="button"
          class="text-xs px-3 py-1 bg-accent-500 text-white hover:bg-accent-600 disabled:opacity-50"
          data-testid="run-button"
          :disabled="busy || hasFormError"
          @click="emit('submit')"
        >
          Run
        </button>
      </div>
    </header>

    <div class="flex-1 min-h-0 overflow-hidden">
      <div v-if="loading" class="h-full overflow-auto p-3 space-y-2" data-testid="input-skeleton">
        <div v-for="i in 6" :key="i" class="h-3 bg-gray-200 rounded animate-pulse" />
      </div>
      <div v-else-if="effectiveMode === 'form' && schema" class="h-full overflow-auto p-3">
        <SchemaForm
          :key="inputType ?? name"
          :schema="schema"
          :model-value="formValue"
          @update:model-value="onFormUpdate"
        />
      </div>
      <div v-else-if="effectiveMode === 'schema'" class="h-full overflow-auto p-3">
        <pre
          data-testid="schema-view"
          class="w-full h-full min-h-0 overflow-auto font-mono text-xs leading-relaxed text-ink bg-white whitespace-pre-wrap break-words"
        >{{ JSON.stringify(schema, null, 2) }}</pre>
      </div>
      <textarea
        v-else
        v-model="text"
        spellcheck="false"
        autocomplete="off"
        autocapitalize="off"
        data-testid="json-textarea"
        class="w-full h-full min-h-0 p-3 font-mono text-xs leading-relaxed text-ink bg-white resize-none focus:outline-none focus:ring-1 focus:ring-accent-500"
      />
    </div>

    <footer class="flex items-center justify-between px-3 py-2 border-t border-line gap-3">
      <div class="flex items-center shrink-0 gap-2">
        <div
          v-if="canUseForm"
          class="flex items-center border border-line rounded-sm overflow-hidden"
        >
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'form' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-form"
            @click="setMode('form')"
          >
            Form
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="effectiveMode === 'json' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-json"
            @click="setMode('json')"
          >
            JSON
          </button>
        </div>
        <span v-else class="text-xs text-ink-muted">JSON</span>

        <button
          v-if="hasSchema"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          :class="effectiveMode === 'schema' ? 'bg-accent-500 text-white' : 'text-ink'"
          data-testid="mode-schema"
          @click="setMode('schema')"
        >
          Schema
        </button>
      </div>

      <div class="flex-1 min-w-0 text-center">
        <span
          data-testid="input-status"
          class="text-xs truncate"
          :class="{
            'text-danger': footerStatus.type === 'danger',
            'text-ink-muted': footerStatus.type === 'muted',
          }"
        >
          {{ footerStatus.text }}
        </span>
      </div>

      <div class="flex items-center gap-2 shrink-0">
        <button
          v-if="hasSchema"
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="generate-input"
          :disabled="busy"
          @click="generate"
        >
          Generate
        </button>
        <button
          type="button"
          class="text-xs px-2 py-1 border border-line hover:bg-surface disabled:opacity-50"
          data-testid="format-input"
          :disabled="busy || effectiveMode !== 'json'"
          @click="format"
        >
          Format
        </button>
      </div>
    </footer>
  </section>
</template>
