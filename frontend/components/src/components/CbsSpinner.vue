<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    loading?: boolean
    size?: 'sm' | 'md' | 'lg'
    label?: string
  }>(),
  {
    loading: true,
    size: 'md',
    label: 'Loading...',
  },
)

const sizeClass = {
  sm: 'w-4 h-4 border-2',
  md: 'w-6 h-6 border-2',
  lg: 'w-8 h-8 border-4',
}[props.size]
</script>

<template>
  <div v-if="loading" class="inline-flex items-center gap-2" role="status" aria-live="polite">
    <div class="rounded-full border-current border-t-transparent animate-spin" :class="sizeClass" />
    <span v-if="label" class="text-sm text-current">{{ label }}</span>
  </div>
</template>
