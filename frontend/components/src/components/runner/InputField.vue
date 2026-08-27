<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  name: string
  type: string
  required?: boolean
  modelValue: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [val: unknown]
}>()

const touched = ref(false)

const normalizedType = computed(() => (props.type || 'string').toLowerCase())

const inputId = computed(() => `input-${props.name}`)

const isEmpty = computed(() => {
  const v = props.modelValue
  if (v === null || v === undefined) return true
  if (typeof v === 'string') return v.length === 0
  return false
})

const showError = computed(() => Boolean(props.required) && touched.value && isEmpty.value)

function emitValue(value: unknown) {
  emit('update:modelValue', value)
}

function onTextInput(event: Event) {
  emitValue((event.target as HTMLInputElement).value)
}

function onCheckboxInput(event: Event) {
  emitValue((event.target as HTMLInputElement).checked)
}

function onJsonInput(event: Event) {
  const raw = (event.target as HTMLTextAreaElement).value
  emitValue(raw)
}

function onBlur() {
  touched.value = true
}
</script>

<template>
  <div data-testid="input-field" class="flex flex-col gap-1">
    <label
      v-if="normalizedType !== 'boolean'"
      :for="inputId"
      class="text-sm font-medium text-gray-700"
    >
      {{ props.name }}<span v-if="props.required" class="text-red-500 ml-0.5">*</span>
    </label>

    <input
      v-if="normalizedType === 'string'"
      :id="inputId"
      type="text"
      :data-testid="`input-field-${props.name}`"
      :value="(props.modelValue as string | undefined) ?? ''"
      class="px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      :class="showError ? 'border-red-400' : 'border-gray-300'"
      :aria-invalid="showError"
      @input="onTextInput"
      @blur="onBlur"
    >

    <input
      v-else-if="normalizedType === 'number'"
      :id="inputId"
      type="number"
      :data-testid="`input-field-${props.name}`"
      :value="(props.modelValue as number | undefined) ?? ''"
      class="px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      :class="showError ? 'border-red-400' : 'border-gray-300'"
      :aria-invalid="showError"
      @input="onTextInput"
      @blur="onBlur"
    >

    <!-- biome-ignore lint/a11y/noLabelWithoutControl: boolean label wraps its checkbox and text -->
    <label
      v-else-if="normalizedType === 'boolean'"
      :for="inputId"
      class="inline-flex items-center gap-2 text-sm text-gray-700"
    >
      <input
        :id="inputId"
        type="checkbox"
        :data-testid="`input-field-${props.name}`"
        :checked="Boolean(props.modelValue)"
        class="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
        @change="onCheckboxInput"
      >
      <span>{{ props.modelValue ? 'true' : 'false' }}</span>
    </label>

    <textarea
      v-else
      :id="inputId"
      :value="typeof props.modelValue === 'string' ? props.modelValue : JSON.stringify(props.modelValue ?? '', null, 2)"
      :data-testid="`input-field-${props.name}`"
      rows="4"
      :placeholder="normalizedType === 'array' ? 'JSON array' : 'JSON object'"
      class="px-3 py-2 border rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
      :class="showError ? 'border-red-400' : 'border-gray-300'"
      :aria-invalid="showError"
      @input="onJsonInput"
      @blur="onBlur"
    />

    <span v-if="showError" class="text-xs text-red-600">{{ props.name }} is required</span>
  </div>
</template>
