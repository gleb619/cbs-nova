<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  schema: Record<string, unknown> | undefined
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  'update:modelValue': [data: Record<string, unknown>]
}>()

interface FieldSpec {
  name: string
  type: string
  required: boolean
}

const freeform = ref(JSON.stringify(props.modelValue ?? {}, null, 2))
const freeformError = ref<string | null>(null)

watch(freeform, (next) => {
  if (!isSchemaEmpty.value) return
  try {
    const parsed = next.trim() ? JSON.parse(next) : {}
    freeformError.value = null
    emit(
      'update:modelValue',
      parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {},
    )
  } catch (err) {
    freeformError.value = (err as Error).message
  }
})

const isSchemaEmpty = computed(() => {
  if (!props.schema || typeof props.schema !== 'object') return true
  const props_ = (props.schema as { properties?: Record<string, unknown> }).properties
  return !props_ || Object.keys(props_).length === 0
})

const fields = computed<FieldSpec[]>(() => {
  const props_ = (
    props.schema as
      | { properties?: Record<string, Record<string, unknown>>; required?: string[] }
      | undefined
  )?.properties
  if (!props_) return []
  const required = new Set(
    (props.schema && (props.schema as { required?: string[] }).required) || [],
  )
  return Object.entries(props_).map(([name, spec]) => ({
    name,
    type: typeof spec.type === 'string' ? spec.type : 'string',
    required: required.has(name),
  }))
})

function _getFieldType(name: string): string {
  return fields.value.find((f) => f.name === name)?.type ?? 'string'
}

function _isRequired(name: string): boolean {
  return fields.value.find((f) => f.name === name)?.required ?? false
}

function updateField(name: string, value: unknown) {
  const next = { ...props.modelValue, [name]: value }
  emit('update:modelValue', next)
}
</script>

<template>
  <div data-testid="input-form" class="flex flex-col gap-4">
    <template v-if="isSchemaEmpty">
      <label class="flex flex-col gap-1 text-sm">
        <span class="text-gray-700 font-medium">Input (JSON)</span>
        <textarea
          v-model="freeform"
          rows="10"
          class="px-3 py-2 border rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
          :class="freeformError ? 'border-red-400' : 'border-gray-300'"
        />
        <span v-if="freeformError" class="text-xs text-red-600"
          >Invalid JSON: {{ freeformError }}</span
        >
      </label>
    </template>

    <template v-else>
      <InputField
        v-for="field in fields"
        :key="field.name"
        :name="field.name"
        :type="field.type"
        :required="field.required"
        :model-value="props.modelValue[field.name]"
        @update:model-value="(val) => updateField(field.name, val)"
      />
      <div v-if="fields.length === 0" class="text-sm text-gray-500">No inputs required.</div>
    </template>
  </div>
</template>
