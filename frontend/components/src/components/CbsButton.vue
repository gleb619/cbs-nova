<script setup lang="ts">
withDefaults(defineProps<{ variant?: 'primary' | 'secondary' }>(), { variant: 'primary' })
</script>

<template>
  <button
    class="px-4 py-2 rounded font-medium transition-colors focus:outline-none focus:ring-2"
    :class="variant === 'primary'
      ? 'bg-primary-500 text-white hover:bg-primary-600 focus:ring-primary-300'
      : 'bg-neutral-100 text-neutral-800 hover:bg-neutral-200 focus:ring-neutral-300'"
  >
    <slot />
  </button>
</template>
