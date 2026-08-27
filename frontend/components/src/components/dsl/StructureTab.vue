<script setup lang="ts">
import type { StepDef } from '../../types/dsl'

defineProps<{ steps: StepDef[] }>()

const typeIcon: Record<StepDef['type'], string> = {
  helper: '🔧',
  function: 'ƒ',
  transaction: '⇄',
  step: '•',
}
</script>

<template>
  <div class="p-3" data-testid="structure-tab">
    <div v-if="steps.length === 0" class="text-sm text-gray-500 italic py-6 text-center">
      No steps defined yet.
    </div>
    <ol v-else class="space-y-1">
      <li
        v-for="(step, idx) in steps"
        :key="step.id"
        class="flex items-center gap-2 px-2 py-1.5 rounded bg-gray-50 border border-gray-200"
        :data-testid="`structure-tab-node-${step.id}`"
      >
        <span class="text-gray-400 cursor-grab select-none" title="Drag handle">⋮⋮</span>
        <span class="text-base w-5 text-center" :title="step.type">{{ typeIcon[step.type] }}</span>
        <span class="text-xs text-gray-400 w-6 text-right">{{ idx + 1 }}.</span>
        <span class="text-sm text-gray-900 font-medium flex-1 truncate">{{ step.name }}</span>
        <span class="text-[10px] uppercase text-gray-500">{{ step.type }}</span>
      </li>
    </ol>
  </div>
</template>
