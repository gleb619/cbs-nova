<script setup lang="ts">
const props = defineProps<{ metadata: Record<string, unknown> | undefined }>()

const entries = computed(() => {
  if (!props.metadata) return []
  return Object.entries(props.metadata)
})

function format(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
</script>

<template>
  <div>
    <div v-if="entries.length === 0" class="text-sm text-gray-500">No metadata.</div>
    <table v-else class="w-full text-sm border-collapse">
      <thead>
        <tr class="text-left text-gray-500 border-b border-gray-200">
          <th class="py-2 pr-4 font-medium">Key</th>
          <th class="py-2 font-medium">Value</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="[ key, value ] in entries" :key="key" class="border-b border-gray-100">
          <td class="py-2 pr-4 font-mono text-gray-700 align-top">{{ key }}</td>
          <td class="py-2 font-mono text-gray-900 break-words whitespace-pre-wrap">
            {{ format(value) }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
