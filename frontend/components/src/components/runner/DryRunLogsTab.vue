<script setup lang="ts">
import { computed } from 'vue'

import type { RunnerOutput } from '../../types/runner'

const props = defineProps<{
  logs?: RunnerOutput['dryRunLogs']
}>()

const list = computed(() => props.logs ?? [])

const levelClass = (level: string) => {
  switch (level) {
    case 'ERROR':
      return 'text-red-600 bg-red-50'
    case 'WARN':
      return 'text-yellow-700 bg-yellow-50'
    case 'INFO':
      return 'text-blue-600 bg-blue-50'
    default:
      return 'text-gray-500 bg-gray-100'
  }
}

const plainTextDump = computed(() =>
  list.value
    .map((log) => `[${log.timestamp}] ${log.level}  ${log.logger} — ${log.message}`)
    .join('\n'),
)

const copyAll = async () => {
  if (!navigator.clipboard) return
  await navigator.clipboard.writeText(plainTextDump.value)
}
</script>

<template>
  <div>
    <div v-if="list.length === 0" class="text-sm text-gray-500">
      No logs captured during dry run.
    </div>

    <div v-else class="flex flex-col gap-2">
      <div class="flex items-center justify-between gap-2">
        <span class="text-xs font-medium px-2 py-1 rounded-full bg-gray-100 text-gray-700">
          {{ list.length }}
          log{{ list.length === 1 ? '' : 's' }}
        </span>
        <button
          type="button"
          class="text-xs font-medium text-blue-600 hover:text-blue-700 px-2 py-1 rounded border border-blue-200 hover:bg-blue-50 transition-colors"
          @click="copyAll"
        >
          Copy all
        </button>
      </div>

      <ul class="max-h-[60vh] overflow-auto space-y-1 border border-gray-200 rounded-lg p-2">
        <li
          v-for="(log, idx) in list"
          :key="idx"
          class="font-mono text-xs flex items-start gap-2 py-1"
        >
          <span class="text-gray-500 shrink-0">[{{ log.timestamp }}]</span>
          <span
            class="px-1.5 py-0.5 rounded font-semibold shrink-0"
            :class="levelClass(log.level)"
            :data-level="log.level"
          >
            {{ log.level }}
          </span>
          <span class="text-gray-700 shrink-0">{{ log.logger }}</span>
          <span class="text-gray-400 shrink-0">—</span>
          <span class="text-gray-900 break-words">{{ log.message }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>
