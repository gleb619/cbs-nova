<script setup lang="ts">
import { computed, inject, onScopeDispose, ref } from 'vue'
import type { JsonSchema, JsonSchemaType } from '../../types/jsonSchema'
import SchemaForm from './SchemaForm.vue'
import { SCHEMA_FIELD_EVENTS_KEY } from './schemaFieldEvents'

const props = defineProps<{
  name: string
  schema: JsonSchema
  required?: boolean
  modelValue: unknown
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
  remove: []
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

function showError() { return Boolean(props.required) && touched.value && isEmpty.value }

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
    if (props.readonly) return
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
  if (props.readonly) return
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}

function onNumberInput(event: Event) {
  if (props.readonly) return
  const raw = (event.target as HTMLInputElement).value
  if (raw === '') {
    emit('update:modelValue', undefined)
    return
  }
  emit('update:modelValue', Number(raw))
}

function onCheckboxInput(event: Event) {
  if (props.readonly) return
  emit('update:modelValue', (event.target as HTMLInputElement).checked)
}

function onSelectInput(event: Event) {
  if (props.readonly) return
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
  if (props.readonly) return
  touched.value = true
}

function remove() {
  if (props.readonly) return
  emit('remove')
}

const fieldEvents = inject(SCHEMA_FIELD_EVENTS_KEY, null)
if (fieldEvents) {
  onScopeDispose(
    fieldEvents.onClearError((name) => {
      if (name === '*' || name === props.name) {
        jsonError.value = null
      }
    }),
  )
}
</script>

<template>
  <div data-testid="schema-form-field" class="flex flex-col gap-1">
    <label v-if="effectiveType !== 'boolean'" :for="inputId" class="text-xs text-ink-muted">
      {{ name }}<span v-if="required" class="text-danger ml-0.5" aria-hidden="true">*</span>
    </label>

    <!-- enum string -->
    <select
      v-if="effectiveType === 'string' && enumOptions.length > 0"
      :id="inputId"
      :value="JSON.stringify(modelValue)"
      :data-testid="`schema-field-${name}`"
      class="w-full px-2 py-1 text-xs border border-line rounded-sm focus:outline-none focus:ring-1 focus:ring-accent-500 bg-white disabled:opacity-60 disabled:bg-surface"
      :aria-required="required"
      :disabled="readonly"
      @change="onSelectInput"
    >
      <option v-if="!required" value="__NULL__">—</option>
      <option v-for="(opt, index) in enumOptions" :key="index" :value="JSON.stringify(opt)">
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
      class="w-full px-2 py-1 text-xs border border-line rounded-sm focus:outline-none focus:ring-1 focus:ring-accent-500 disabled:opacity-60 disabled:bg-surface"
      :class="showError() ? 'border-danger' : ''"
      :aria-invalid="showError()"
      :aria-required="required"
      :disabled="readonly"
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
      class="w-full px-2 py-1 text-xs border border-line rounded-sm focus:outline-none focus:ring-1 focus:ring-accent-500 disabled:opacity-60 disabled:bg-surface"
      :class="showError() ? 'border-danger' : ''"
      :aria-invalid="showError()"
      :aria-required="required"
      :disabled="readonly"
      @input="onNumberInput"
      @blur="onBlur"
    >

    <!-- boolean -->
    <!-- biome-ignore lint/a11y/noLabelWithoutControl: boolean label wraps its checkbox and text -->
    <label
      v-else-if="effectiveType === 'boolean'"
      :for="inputId"
      class="inline-flex items-center gap-2 text-xs text-ink-muted"
    >
      <input
        :id="inputId"
        type="checkbox"
        :data-testid="`schema-field-${name}`"
        :checked="Boolean(modelValue)"
        class="w-3.5 h-3.5 rounded border-line text-accent-500 focus:ring-accent-500 disabled:opacity-60"
        :disabled="readonly"
        @change="onCheckboxInput"
      >
      <span
        >{{ name }}
        <span v-if="required" class="text-danger ml-0.5" aria-hidden="true">*</span></span
      >
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
      class="border border-line rounded-sm p-3 flex flex-col gap-2"
    >
      <legend class="text-xs text-ink-muted px-1">
        {{ name }}<span v-if="required" class="text-danger ml-0.5">*</span>
      </legend>
      <SchemaForm
        :schema="schema"
        :model-value="modelValue ?? {}"
        :readonly="readonly"
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
        class="w-full px-2 py-1 text-xs font-mono border border-line rounded-sm focus:outline-none focus:ring-1 focus:ring-accent-500 disabled:opacity-60 disabled:bg-surface"
        :class="jsonError ? 'border-danger' : ''"
        :aria-invalid="Boolean(jsonError)"
        :aria-required="required"
        :disabled="readonly"
        @blur="onBlur"
      />
      <span v-if="jsonError" class="text-xs text-danger">Invalid JSON: {{ jsonError }}</span>
    </label>

    <span v-if="showError()" class="text-xs text-danger">{{ name }} is required</span>
  </div>
</template>
