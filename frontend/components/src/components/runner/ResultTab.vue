<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ result: unknown }>()

const _formatted = computed(() => {
  if (props.result === null || props.result === undefined) return ''
  try {
    return JSON.stringify(props.result, null, 2)
  } catch {
    return String(props.result)
  }
})

const isEmpty = computed(() => props.result === null || props.result === undefined)
</script>

<template>
  <div data-testid="runner-result-tab">
    <div v-if="isEmpty" class="text-sm text-gray-500">No result yet.</div>
    <pre
      v-else
      data-testid="runner-result-tab-output"
      class="bg-gray-900 text-gray-100 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words"
    >{{ _formatted }}</pre>
  </div>
</template>
