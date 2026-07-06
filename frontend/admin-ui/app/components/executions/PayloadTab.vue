<script setup lang="ts">
defineProps<{ input: unknown; output: unknown }>()

function format(v: unknown): string {
  if (v === undefined) return '—'
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
    <div class="bg-white border border-gray-200 rounded-lg p-4">
      <h3 class="text-sm font-semibold text-gray-700 mb-2">Initial Input</h3>
      <pre class="text-xs bg-gray-50 border border-gray-200 rounded p-3 overflow-auto max-h-[500px]"><code>{{ format(input) }}</code></pre>
    </div>
    <div class="bg-white border border-gray-200 rounded-lg p-4">
      <h3 class="text-sm font-semibold text-gray-700 mb-2">Final Output</h3>
      <pre class="text-xs bg-gray-50 border border-gray-200 rounded p-3 overflow-auto max-h-[500px]"><code>{{ format(output) }}</code></pre>
    </div>
  </div>
</template>
