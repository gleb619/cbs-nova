<script setup lang="ts">
/**
 * DraftRestoreBanner
 *
 * Small recovery notice shown when `useWorkbenchDraft` restores a body from
 * a local (browser-only) draft. Purely presentational — the caller decides
 * when to show it (typically `v-if="restoredFromDraft"`) and owns the
 * discard behavior (typically `clearDraft()` from the same composable).
 */
defineProps<{
  savedAt?: number | null
}>()

const emit = defineEmits<{
  discard: []
}>()

function onDiscard() {
  emit('discard')
}
</script>

<template>
  <div
    role="status"
    class="flex items-center justify-between gap-3 px-3 py-2 text-sm rounded border border-blue-200 bg-blue-50 text-blue-800"
  >
    <div class="flex items-center gap-2">
      <span aria-hidden="true">↺</span>
      <span>Restored from local draft</span>
      <span v-if="savedAt" class="text-xs text-blue-500">
        (saved {{ new Date(savedAt).toLocaleTimeString() }})
      </span>
    </div>
    <button
      type="button"
      class="px-2 py-1 text-xs font-medium rounded border border-blue-300 text-blue-700 hover:bg-blue-100 transition-colors"
      @click="onDiscard"
    >
      Discard
    </button>
  </div>
</template>
