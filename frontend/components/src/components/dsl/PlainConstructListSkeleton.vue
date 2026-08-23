<script setup lang="ts">
const GROUPS = ['Process', 'Transaction', 'Function', 'Helper'] as const

withDefaults(
  defineProps<{
    rowsPerGroup?: number
  }>(),
  {
    rowsPerGroup: 3,
  },
)
</script>

<template>
  <div class="space-y-3">
    <div v-for="type in GROUPS" :key="type">
      <div class="w-full flex items-center justify-between px-2 py-1">
        <span class="text-xs font-semibold uppercase text-gray-400 tracking-wide">
          {{ type }}
        </span>
        <div class="h-3 w-5 bg-gray-800 rounded animate-pulse" />
      </div>

      <ul class="space-y-0.5">
        <li v-for="i in rowsPerGroup" :key="i">
          <div
            class="w-full flex items-center justify-between gap-2 px-2 py-1.5 rounded animate-pulse"
          >
            <div class="h-4 flex-1 bg-gray-800 rounded" />
            <div class="h-4 w-12 bg-gray-800 rounded" />
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>
