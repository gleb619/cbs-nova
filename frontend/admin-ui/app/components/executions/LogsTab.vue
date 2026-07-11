<script setup lang="ts">
const props = defineProps<{
  logs: Array<{ timestamp: string; step?: string; severity: string; message: string }> | undefined
}>()

const severityFilter = ref<string>('all')
const search = ref<string>('')

const filtered = computed(() => {
  if (!props.logs) return []
  return props.logs.filter((l) => {
    if (severityFilter.value !== 'all' && l.severity !== severityFilter.value) return false
    if (search.value && !l.message.toLowerCase().includes(search.value.toLowerCase())) return false
    return true
  })
})

const severityColor: Record<string, string> = {
  info: 'bg-blue-100 text-blue-800',
  warn: 'bg-yellow-100 text-yellow-800',
  error: 'bg-red-100 text-red-800',
  debug: 'bg-gray-100 text-gray-800',
}

function formatTime(s: string) {
  return new Date(s).toLocaleString()
}
</script>

<template>
  <div class="bg-white border border-gray-200 rounded-lg">
    <div class="p-3 border-b border-gray-200 flex flex-wrap items-center gap-3">
      <div>
        <label for="log-severity" class="text-xs text-gray-600 mr-1">Severity:</label>
        <select
          id="log-severity"
          v-model="severityFilter"
          class="border border-gray-300 rounded px-2 py-1 text-sm"
        >
          <option value="all">All</option>
          <option value="info">info</option>
          <option value="warn">warn</option>
          <option value="error">error</option>
          <option value="debug">debug</option>
        </select>
      </div>
      <div class="flex-1 min-w-[200px]">
        <input
          v-model="search"
          type="text"
          placeholder="Search messages…"
          class="w-full border border-gray-300 rounded px-2 py-1 text-sm"
        >
      </div>
    </div>
    <div class="max-h-[600px] overflow-auto">
      <div v-if="filtered.length === 0" class="p-6 text-center text-sm text-gray-500">
        No log entries.
      </div>
      <div
        v-for="(log, idx) in filtered"
        :key="idx"
        class="px-3 py-2 border-b border-gray-100 text-xs flex gap-3"
      >
        <span class="text-gray-500 w-44 shrink-0">{{ formatTime(log.timestamp) }}</span>
        <span
          :class="['px-1.5 py-0.5 rounded text-[10px] uppercase font-medium w-14 text-center shrink-0',
                       severityColor[log.severity] ?? 'bg-gray-100 text-gray-800']"
          >{{ log.severity }}</span
        >
        <span v-if="log.step" class="text-gray-600 w-40 shrink-0 truncate" :title="log.step"
          >{{ log.step }}</span
        >
        <span class="text-gray-800 break-all">{{ log.message }}</span>
      </div>
    </div>
  </div>
</template>
