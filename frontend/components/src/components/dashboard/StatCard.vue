<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    label: string
    count: number
    to: string
    icon?: string
    linkComponent?: 'a' | unknown
  }>(),
  {
    linkComponent: 'a',
  },
)
</script>

<template>
  <div
    data-testid="stat-card"
    class="bg-white rounded-lg shadow-sm border border-gray-200 p-6 flex items-center gap-4 hover:shadow-md transition-shadow"
  >
    <span v-if="icon" class="text-3xl">{{ icon }}</span>
    <div class="flex-1">
      <div class="text-3xl font-bold text-gray-900">{{ count }}</div>
      <div class="text-sm text-gray-600 mt-1">{{ label }}</div>
    </div>
    <a
      v-if="props.linkComponent === 'a'"
      :href="props.to"
      data-testid="stat-card-link"
      class="text-sm text-blue-600 hover:text-blue-800 font-medium"
      >View →</a
    >
    <component
      :is="props.linkComponent"
      v-else
      :to="props.to"
      data-testid="stat-card-link"
      class="text-sm text-blue-600 hover:text-blue-800 font-medium"
      >View →</component
    >
  </div>
</template>
