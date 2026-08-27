<script setup lang="ts">
import type { RunnerOutput, RunnerStatus } from '../../types/runner'
import ResultTab from '../runner/ResultTab.vue'

defineProps<{ output: RunnerOutput | null; status: RunnerStatus }>()

defineEmits<{ run: [] }>()
</script>

<template>
  <div class="p-3 h-full overflow-auto">
    <div class="flex items-center gap-3 mb-3">
      <button
        type="button"
        class="px-3 py-1.5 text-sm font-medium rounded border border-gray-300 hover:bg-gray-50"
        @click="$emit('run')"
      >
        Run preview
      </button>
      <span v-if="status === 'loading'" class="text-sm text-gray-500">Loading…</span>
      <span v-else-if="status === 'success'" class="text-sm text-green-600">Done</span>
      <span v-else-if="status === 'failed'" class="text-sm text-red-600">Failed</span>
    </div>

    <div v-if="output?.errors?.length" class="text-sm text-red-600">
      <p v-for="(err, i) in output.errors" :key="i">{{ err.message }}</p>
    </div>
    <ResultTab v-else :result="output?.result" />
  </div>
</template>
