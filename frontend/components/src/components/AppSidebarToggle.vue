<script setup lang="ts">
import { useSidebar } from '../composables/useSidebar'

const props = withDefaults(
  defineProps<{
    label?: string
  }>(),
  {
    label: 'Toggle navigation',
  },
)

const { toggle, openMobile } = useSidebar()

function onClick() {
  //TODO: make useSidebar reuse a `frontend/components/src/composables/useLocalStorageState.ts`
  if (typeof window !== 'undefined' && window.innerWidth < 768) {
    openMobile()
  } else {
    toggle()
  }
}
</script>

<template>
  <button
    type="button"
    class="p-2 rounded-lg text-neutral-600 hover:text-primary-600 hover:bg-primary-50 focus:outline-none focus:ring-2 focus:ring-primary-300"
    :aria-label="props.label"
    @click="onClick"
  >
    <svg
      class="w-6 h-6"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 12h16M4 18h16" />
    </svg>
  </button>
</template>
