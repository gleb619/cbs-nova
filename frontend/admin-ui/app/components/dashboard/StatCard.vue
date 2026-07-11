<script setup lang="ts">
defineProps<{ label: string; count: number; to: string; icon?: string }>()
</script>

<template>
  <div
    class="bg-white rounded-lg shadow-sm border border-gray-200 p-6 flex items-center gap-4 hover:shadow-md transition-shadow"
  >
    <span v-if="icon" class="text-3xl">{{ icon }}</span>
    <div class="flex-1">
      <div class="text-3xl font-bold text-gray-900">{{ count }}</div>
      <div class="text-sm text-gray-600 mt-1">{{ label }}</div>
    </div>
    <NuxtLink :to="to" class="text-sm text-blue-600 hover:text-blue-800 font-medium"
      >View →</NuxtLink
    >
  </div>
</template>
