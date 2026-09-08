<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

const parseError = ref<string | null>(null)
const mode = ref<'form' | 'json'>('json')
const formValue = ref<unknown>(undefined)

const { schema, inputType, loading, error, hasSchema } = useConstructSchema({
  name: () => props.name,
  type: () => props.type,
})

const canUseForm = computed(() => {
  return !error.value && hasSchema.value && schema.value != null
})

function format() {
  try {
    text.value = `${JSON.stringify(JSON.parse(text.value), null, 2)}\n`
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

function generate() {
  if (!schema.value) return
  const fake = generateFakeValue(schema.value)
  formValue.value = fake
  try {
    text.value = `${JSON.stringify(fake ?? {}, null, 2)}\n`
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

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
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

function setMode(next: 'form' | 'json') {
  if (next === mode.value) return
  if (next === 'form') {
    syncJsonToForm()
    mode.value = 'form'
  } else {
    syncFormToJson()
    mode.value = 'json'
  }
}

function onFormUpdate(value: unknown) {
  formValue.value = value
  try {
    text.value = `${JSON.stringify(value ?? {}, null, 2)}\n`
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

watch(
  text,
  (v) => {
    if (!v.trim()) {
      parseError.value = null
      return
    }
    try {
      JSON.parse(v)
      parseError.value = null
    } catch (e) {
      parseError.value = (e as Error).message
    }
  },
  { immediate: true },
)

watch(
  () => props.name,
  () => {
    mode.value = 'json'
    formValue.value = undefined
  },
)

watch(
  () => canUseForm.value,
  (available) => {
    if (!available && mode.value === 'form') {
      syncFormToJson()
      mode.value = 'json'
    }
  },
)

const hasFormError = computed(() => {
  if (mode.value === 'json') return !!parseError.value
  return false
})

const footerStatus = computed(() => {
  if (parseError.value)
    return { text: `Invalid JSON: ${parseError.value}`, type: 'danger' as const }
  if (error.value)
    return { text: `Schema unavailable — JSON only: ${error.value}`, type: 'danger' as const }
  if (loading.value) return { text: 'Loading schema…', type: 'muted' as const }
  if (mode.value === 'form') return { text: 'Form input', type: 'muted' as const }
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

    <div class="flex-1 min-h-0 p-3 overflow-auto">
      <div v-if="loading" class="space-y-2" data-testid="input-skeleton">
        <div v-for="i in 6" :key="i" class="h-3 bg-gray-200 rounded animate-pulse" />
      </div>
      <div v-else-if="mode === 'form' && schema">
        <SchemaForm :schema="schema" :model-value="formValue" @update:model-value="onFormUpdate" />
      </div>
      <textarea
        v-else
        v-model="text"
        spellcheck="false"
        autocomplete="off"
        autocapitalize="off"
        data-testid="json-textarea"
        class="w-full h-full min-h-[6rem] p-0 font-mono text-xs leading-relaxed text-ink bg-white resize-none focus:outline-none focus:ring-1 focus:ring-accent-500"
      />
    </div>

    <footer class="flex items-center justify-between px-3 py-2 border-t border-line gap-3">
      <div class="flex items-center shrink-0">
        <div
          v-if="canUseForm"
          class="flex items-center border border-line rounded-sm overflow-hidden"
        >
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="mode === 'form' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-form"
            @click="setMode('form')"
          >
            Form
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="mode === 'json' ? 'bg-accent-500 text-white' : 'hover:bg-surface text-ink'"
            data-testid="mode-json"
            @click="setMode('json')"
          >
            JSON
          </button>
        </div>
        <span v-else class="text-xs text-ink-muted">JSON</span>
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
          :disabled="busy || mode === 'form'"
          @click="format"
        >
          Format
        </button>
      </div>
    </footer>
  </section>
</template>
