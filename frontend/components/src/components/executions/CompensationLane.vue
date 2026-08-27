<script setup lang="ts">
import type { TraceStep } from '../../types/execution'

const props = defineProps<{ steps: TraceStep[] }>()
</script>

<template>
  <div
    v-if="props.steps.length > 0"
    data-testid="compensation-lane"
    class="bg-orange-50 border-2 border-dashed border-orange-300 rounded-lg p-3"
  >
    <h3 class="text-sm font-semibold text-orange-700 mb-2">Compensation</h3>
    <ExecutionsTraceNode v-for="s in props.steps" :key="s.id" :step="s" :depth="0" />
  </div>
</template>
