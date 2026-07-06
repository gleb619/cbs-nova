<script setup lang="ts">
import type { DefinitionMeta } from '~/types/runner'

const props = defineProps<{
  definitions: DefinitionMeta[]
  modelValue: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [name: string]
}>()

function onChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value)
}
</script>

<template>
  <label class="flex flex-col gap-1 text-sm">
    <span class="text-gray-700 font-medium">Definition</span>
    <select
      :value="props.modelValue ?? ''"
      class="px-3 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
      @change="onChange"
    >
      <option value="" disabled>Select a definition…</option>
      <option
        v-for="def in props.definitions"
        :key="def.name"
        :value="def.name"
      >
        {{ def.name }} ({{ def.type }})
      </option>
    </select>
  </label>
</template>