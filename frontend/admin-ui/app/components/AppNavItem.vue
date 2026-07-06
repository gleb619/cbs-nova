<script setup lang="ts">
const props = defineProps<{ to: string; label: string; icon?: string }>()
</script>

<template>
  <NuxtLink
    :to="props.to"
    class="flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors text-gray-300 hover:bg-gray-800 hover:text-white"
    active-class="bg-gray-800 text-white"
  >
    <span v-if="props.icon" class="text-lg">{{ props.icon }}</span>
    <span class="truncate">{{ props.label }}</span>
  </NuxtLink>
</template>