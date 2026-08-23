<script setup lang="ts">
withDefaults(
  defineProps<{
    message: string
    retryLabel?: string
  }>(),
  { retryLabel: 'Retry' },
)

const emit = defineEmits<{
  retry: []
}>()

function onRetry() {
  emit('retry')
}
</script>

<template>
  <div
    role="alert"
    data-testid="error-banner"
    class="flex items-center justify-between gap-3 px-3 py-2 text-sm rounded border border-red-300 bg-red-100 text-red-900"
  >
    <div class="flex items-center gap-2">
      <span aria-hidden="true">⚠</span>
      <span>{{ message }}</span>
    </div>
    <button
      type="button"
      class="px-2 py-1 text-xs font-medium rounded border border-red-400 text-red-800 hover:bg-red-200 transition-colors"
      @click="onRetry"
    >
      {{ retryLabel }}
    </button>
  </div>
</template>
