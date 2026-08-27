<script setup lang="ts">
defineProps<{ diagram: string | undefined }>()
</script>

<template>
  <div
    data-testid="execution-diagram-tab"
    class="bg-white border border-gray-200 rounded-lg p-4"
  >
    <div v-if="!diagram" class="text-sm text-gray-500 text-center py-12">
      No diagram available for this execution.
    </div>
    <pre
      v-else
      class="text-xs bg-gray-50 border border-gray-200 rounded p-3 overflow-auto max-h-[600px] whitespace-pre"
    ><code>{{ diagram }}</code></pre>
  </div>
</template>
