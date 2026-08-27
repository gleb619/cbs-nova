<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    sticky?: boolean
  }>(),
  {
    sticky: true,
  },
)
</script>

<template>
  <header
    data-testid="app-navbar"
    :class="[
      'z-10 h-16 bg-neutral-50 border-b border-neutral-200 flex items-center px-4 gap-4 shrink-0',
      props.sticky ? 'sticky top-0' : '',
    ]"
  >
    <div class="flex items-center gap-3">
      <slot name="toggle" />
    </div>
    <div class="flex items-center gap-2">
      <slot name="brand" />
    </div>
    <div class="ml-auto flex items-center gap-2">
      <slot name="trailing" />
    </div>
  </header>
</template>
