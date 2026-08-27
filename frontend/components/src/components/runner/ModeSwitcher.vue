<script setup lang="ts">
import type { RunnerMode } from '../../types/runner'

const props = defineProps<{ modelValue: RunnerMode }>()
const emit = defineEmits<{ 'update:modelValue': [mode: RunnerMode] }>()

const modes: { value: RunnerMode; label: string }[] = [
  { value: 'preview', label: 'Preview' },
  { value: 'run', label: 'Run' },
  { value: 'explain', label: 'Explain' },
]

function pick(value: RunnerMode) {
  emit('update:modelValue', value)
}
</script>

<template>
  <div
    data-testid="mode-switcher"
    class="inline-flex rounded-lg border border-gray-300 overflow-hidden"
    role="radiogroup"
    aria-label="Runner mode"
  >
    <!-- biome-ignore lint/a11y/useSemanticElements: styled segmented toggle buttons -->
    <button
      v-for="m in modes"
      :key="m.value"
      type="button"
      role="radio"
      :data-testid="`mode-switcher-${m.value}`"
      :aria-checked="props.modelValue === m.value"
      :class="[
        'px-4 py-2 text-sm font-medium transition-colors',
        props.modelValue === m.value
          ? 'bg-blue-600 text-white'
          : 'bg-white text-gray-700 hover:bg-gray-100',
      ]"
      @click="pick(m.value)"
    >
      {{ m.label }}
    </button>
  </div>
</template>
