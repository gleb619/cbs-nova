<script setup lang="ts">
const props = defineProps<{
  calls: Array<Record<string, unknown>>
}>()

function label(call: Record<string, unknown>): string {
  const type = String(call.type ?? '')
  const target = String(call.target ?? '')
  const operation = String(call.operation ?? '')
  return `[${type}] ${target} — ${operation}`
}
</script>

<template>
  <div class="flex flex-wrap gap-1.5" data-testid="external-calls-badge">
    <span
      v-for="(call, idx) in props.calls"
      :key="idx"
      class="bg-gray-100 text-gray-600 text-xs rounded px-2 py-0.5"
    >
      {{ label(call) }}
    </span>
  </div>
</template>
