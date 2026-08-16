<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    pad?: boolean
  }>(),
  {
    pad: true,
  },
)
</script>

<template>
  <main
    :class="[
      'flex-1 overflow-y-auto bg-background',
      props.pad ? 'p-4 md:p-6' : '',
    ]"
  >
    <slot />
  </main>
</template>
