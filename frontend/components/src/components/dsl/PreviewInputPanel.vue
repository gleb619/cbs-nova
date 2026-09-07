<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useConstructSchema, type ConstructType } from '../../composables/useConstructSchema'
import type { JsonSchema } from '../../types/jsonSchema'
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

const { schema, loading, error, hasSchema } = useConstructSchema({
  name: computed(() => props.name).value,
  type: computed(() => props.type).value,
})

const canUseForm = computed(() => {
  const ok = !loading.value && !error.value && hasSchema.value && schema.value != null
  return ok
})

function format() {
  try {
    text.value = JSON.stringify(JSON.parse(text.value), null, 2) + '\n'
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
    text.value = JSON.stringify(formValue.value ?? {}, null, 2) + '\n'
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
    text.value = JSON.stringify(value ?? {}, null, 2) + '\n'
    parseError.value = null
  } catch (e) {
    parseError.value = (e as Error).message
  }
}

watch(
  text,
  (v) => {
    if (!v.trim()) { parseError.value = null; return }
    try {
      JSON.parse(v)
      parseError.value = null
    } catch (e) {
      parseError.value = (e as Error).message
    }
  },
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
</script>

<template>
  <section class="flex flex-col h-full min-h-0 border border-[#E1E4E8] rounded-sm bg-white">
    <header class="flex items-center justify-between px-3 py-2 border-b border-[#E1E4E8]">
      <div class="flex items-baseline gap-2 min-w-0">
        <span class="text-xs text-[#5A6470]">Input</span>
        <span class="font-mono text-xs text-[#0E1116] truncate">{{ name }}</span>
        <span class="text-xs text-[#5A6470]">· {{ endpoint ?? 'preview' }}</span>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <div v-if="canUseForm" class="flex items-center border border-[#E1E4E8] rounded-sm overflow-hidden">
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="mode === 'form' ? 'bg-[#1F8F8A] text-white' : 'hover:bg-[#F4F5F7] text-[#0E1116]'"
            data-testid="mode-form"
            @click="setMode('form')"
          >
            Form
          </button>
          <button
            type="button"
            class="text-xs px-2 py-1"
            :class="mode === 'json' ? 'bg-[#1F8F8A] text-white' : 'hover:bg-[#F4F5F7] text-[#0E1116]'"
            data-testid="mode-json"
            @click="setMode('json')"
          >
            JSON
          </button>
        </div>

        <button
          type="button"
          class="text-xs px-2 py-1 border border-[#E1E4E8] hover:bg-[#F4F5F7] disabled:opacity-50"
          :disabled="busy || mode === 'form'"
          @click="format"
        >
          Format
        </button>
        <button
          type="button"
          class="text-xs px-3 py-1 bg-[#1F8F8A] text-white hover:bg-[#196E6A] disabled:opacity-50"
          :disabled="busy || hasFormError"
          @click="emit('submit')"
        >
          Run
        </button>
      </div>
    </header>

    <div class="flex-1 min-h-0 p-3 overflow-auto">
      <div v-if="mode === 'form' && schema">
        <SchemaForm :schema="schema" :model-value="formValue" @update:model-value="onFormUpdate" />
      </div>
      <textarea
        v-else
        v-model="text"
        spellcheck="false"
        autocomplete="off"
        autocapitalize="off"
        data-testid="json-textarea"
        class="w-full h-full min-h-[6rem] p-0 font-mono text-xs leading-relaxed text-[#0E1116] bg-white resize-none focus:outline-none focus:ring-1 focus:ring-[#1F8F8A]"
      />
    </div>

    <footer
      v-if="parseError"
      class="px-3 py-1.5 border-t border-[#E1E4E8] text-xs font-mono text-[#B42318] truncate"
    >
      {{ parseError }}
    </footer>

    <footer
      v-else-if="error"
      class="px-3 py-1.5 border-t border-[#E1E4E8] text-xs text-[#B42318] truncate"
    >
      Schema unavailable — JSON only: {{ error }}
    </footer>
  </section>
</template>
