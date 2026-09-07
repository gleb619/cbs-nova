<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { JsonSchema, JsonSchemaType } from '../../types/jsonSchema'
import SchemaForm from './SchemaForm.vue'

const props = defineProps<{
  name: string
  schema: JsonSchema
  required?: boolean
  modelValue: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
  'remove': []
}>()

const inputId = computed(() => `schema-field-${props.name}`)
const touched = ref(false)
const jsonError = ref<string | null>(null)

const effectiveType = computed<JsonSchemaType>(() => {
  const t = props.schema.type
  if (!t) return 'any'
  return t as JsonSchemaType
})

const isEmpty = computed(() => {
  const v = props.modelValue
  if (v === null || v === undefined) return true
  if (typeof v === 'string') return v.length === 0
  return false
})

const showError = computed(() => Boolean(props.required) && touched.value && isEmpty.value)

const enumOptions = computed(() => props.schema.enum ?? [])

const jsonText = computed({
  get: () => {
    const v = props.modelValue
    if (v === undefined || v === null) return ''
    try {
      return JSON.stringify(v, null, 2)
    } catch {
      return String(v)
    }
  },
  set: (raw: string) => {
    if (!raw.trim()) {
      jsonError.value = null
      emit('update:modelValue', undefined)
      return
    }
    try {
      const parsed = JSON.parse(raw)
      jsonError.value = null
      emit('update:modelValue', parsed)
    } catch (e) {
      jsonError.value = (e as Error).message
    }
  },
})

function onTextInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function onNumberInput(event: Event) {
  const raw = (event.target as HTMLInputElement).value
  if (raw === '') {
    emit('update:modelValue', undefined)
    return
  }
  emit('update:modelValue', Number(raw))
}

function onCheckboxInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}

function onSelectInput(event: Event) {
  const target = event.target as HTMLSelectElement
  const raw = target.value
  if (raw === '__NULL__') {
    emit('update:modelValue', null)
    return
  }
  try {
    emit('update:modelValue', JSON.parse(raw))
  } catch {
    emit('update:modelValue', raw)
  }
}

function onBlur() {
  touched.value = true
}

function remove() {
  emit('remove')
}

watch(() => props.modelValue, () => {
  jsonError.value = null
}, { immediate: true })
</script>

<template>
  <div data-testid="schema-form-field" class="flex flex-col gap-1">
    <label
      v-if="effectiveType !== 'boolean'"
      :for="inputId"
      class="text-xs text-[#5A6470]"
    >
      {{ name }}<span v-if="required" class="text-[#B42318] ml-0.5" aria-hidden="true">*</span>
    </label>

    <!-- enum string -->
    <select
      v-if="effectiveType === 'string' && enumOptions.length > 0"
      :id="inputId"
      :value="JSON.stringify(modelValue)"
      :data-testid="`schema-field-${name}`"
      class="w-full px-2 py-1 text-xs border border-[#E1E4E8] rounded-sm focus:outline-none focus:ring-1 focus:ring-[#1F8F8A] bg-white"
      :aria-required="required"
      @change="onSelectInput"
    >
      <option v-if="!required" value="__NULL__">—</option>
      <option
        v-for="(opt, index) in enumOptions"
        :key="index"
        :value="JSON.stringify(opt)"
      >
        {{ typeof opt === 'string' ? opt : JSON.stringify(opt) }}
      </option>
    </select>

    <!-- string -->
    <input
      v-else-if="effectiveType === 'string'"
      :id="inputId"
      type="text"
      :value="(modelValue as string | undefined) ?? ''"
      :data-testid="`schema-field-${name}`"
      class="w-full px-2 py-1 text-xs border border-[#E1E4E8] rounded-sm focus:outline-none focus:ring-1 focus:ring-[#1F8F8A]"
      :class="showError ? 'border-[#B42318]' : ''"
      :aria-invalid="showError"
      :aria-required="required"
      @input="onTextInput"
      @blur="onBlur"
    >

    <!-- number -->
    <input
      v-else-if="effectiveType === 'number'"
      :id="inputId"
      type="number"
      :value="(modelValue as number | undefined) ?? ''"
      :data-testid="`schema-field-${name}`"
      class="w-full px-2 py-1 text-xs border border-[#E1E4E8] rounded-sm focus:outline-none focus:ring-1 focus:ring-[#1F8F8A]"
      :class="showError ? 'border-[#B42318]' : ''"
      :aria-invalid="showError"
      :aria-required="required"
      @input="onNumberInput"
      @blur="onBlur"
    >

    <!-- boolean -->
    <!-- biome-ignore lint/a11y/noLabelWithoutControl: boolean label wraps its checkbox and text -->
    <label
      v-else-if="effectiveType === 'boolean'"
      :for="inputId"
      class="inline-flex items-center gap-2 text-xs text-[#5A6470]"
    >
      <input
        :id="inputId"
        type="checkbox"
        :data-testid="`schema-field-${name}`"
        :checked="Boolean(modelValue)"
        class="w-3.5 h-3.5 rounded border-[#E1E4E8] text-[#1F8F8A] focus:ring-[#1F8F8A]"
        @change="onCheckboxInput"
      >
      <span>{{ name }}<span v-if="required" class="text-[#B42318] ml-0.5" aria-hidden="true">*</span></span>
    </label>

    <!-- null -->
    <input
      v-else-if="effectiveType === 'null'"
      :id="inputId"
      type="hidden"
      :data-testid="`schema-field-${name}`"
      value="null"
    >

    <!-- object with properties -->
    <fieldset
      v-else-if="effectiveType === 'object' && schema.properties"
      class="border border-[#E1E4E8] rounded-sm p-3 flex flex-col gap-2"
    >
      <legend class="text-xs text-[#5A6470] px-1">{{ name }}<span v-if="required" class="text-[#B42318] ml-0.5">*</span></legend>
      <SchemaForm
        :schema="schema"
        :model-value="modelValue ?? {}"
        @update:model-value="emit('update:modelValue', $event)"
      />
    </fieldset>

    <!-- any / object without properties / fallback -->
    <label v-else :for="inputId" class="flex flex-col gap-1">
      <textarea
        :id="inputId"
        v-model="jsonText"
        :data-testid="`schema-field-${name}`"
        rows="4"
        class="w-full px-2 py-1 text-xs font-mono border border-[#E1E4E8] rounded-sm focus:outline-none focus:ring-1 focus:ring-[#1F8F8A]"
        :class="jsonError ? 'border-[#B42318]' : ''"
        :aria-invalid="Boolean(jsonError)"
        :aria-required="required"
        @blur="onBlur"
      />
      <span v-if="jsonError" class="text-xs text-[#B42318]">Invalid JSON: {{ jsonError }}</span>
    </label>

    <span v-if="showError" class="text-xs text-[#B42318]">{{ name }} is required</span>
  </div>
</template>
