<script setup lang="ts">
const props = defineProps<{ mappings: Record<string, string> }>()

const entries = computed(() => Object.entries(props.mappings))
</script>

<template>
  <div class="border border-gray-200 rounded overflow-hidden bg-white">
    <table class="w-full text-sm">
      <thead class="bg-gray-50 text-xs uppercase text-gray-500">
        <tr>
          <th class="text-left px-3 py-2 w-1/3">Field</th>
          <th class="text-left px-3 py-2">Mapped Value</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="entries.length === 0">
          <td colspan="2" class="px-3 py-3 text-gray-500 italic text-center">No mappings</td>
        </tr>
        <tr v-for="[ field, value ] in entries" :key="field" class="border-t border-gray-100">
          <td class="px-3 py-2 font-mono text-xs text-gray-700">{{ field }}</td>
          <td class="px-3 py-2 text-gray-900 break-all">{{ value }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
