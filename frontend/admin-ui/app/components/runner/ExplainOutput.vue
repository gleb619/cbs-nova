<script setup lang="ts">
defineOptions({ name: 'ExplainOutput' })

const props = defineProps<{
  description: string | undefined
  mermaidDiagram: string | undefined
}>()
</script>

<template>
  <div class="flex flex-col gap-4">
    <section v-if="props.description">
      <h3 class="text-sm font-semibold text-gray-700 mb-2">Description</h3>
      <p class="text-sm text-gray-900 whitespace-pre-wrap break-words">{{ props.description }}</p>
    </section>

    <section v-if="props.mermaidDiagram">
      <h3 class="text-sm font-semibold text-gray-700 mb-2">Mermaid Diagram</h3>
      <pre class="bg-gray-900 text-gray-100 text-xs rounded-lg p-4 overflow-auto max-h-[60vh] whitespace-pre-wrap break-words">{{ props.mermaidDiagram }}</pre>
    </section>

    <div v-if="!props.description && !props.mermaidDiagram" class="text-sm text-gray-500">
      No explanation available.
    </div>
  </div>
</template>