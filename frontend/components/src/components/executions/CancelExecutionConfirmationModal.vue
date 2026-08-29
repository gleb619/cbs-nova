<script setup lang="ts">
const props = defineProps<{
  show: boolean
  executionId?: string
  busy?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

function onConfirm() {
  if (props.busy) return
  emit('confirm')
}

function onCancel() {
  if (props.busy) return
  emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click dismisses modal -->
    <div
      v-if="props.show"
      data-testid="cancel-confirmation-modal"
      class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cancel-confirm-title"
      @click.self="onCancel"
    >
      <div class="bg-white rounded-xl shadow-xl max-w-md w-full flex flex-col">
        <header class="px-6 py-4 border-b border-gray-200">
          <h2 id="cancel-confirm-title" class="text-lg font-semibold text-gray-900">
            Cancel execution?
          </h2>
          <p class="text-sm text-gray-600 mt-1">
            This stops the workflow and transitions the run to
            <span class="font-mono">Cancelled</span>. In-flight activities cannot be undone.
          </p>
        </header>

        <div v-if="props.executionId" class="px-6 py-3 text-xs text-gray-500 font-mono">
          {{ props.executionId }}
        </div>

        <footer class="px-6 py-4 border-t border-gray-200 flex justify-end gap-2">
          <button
            type="button"
            data-testid="cancel-confirmation-modal-cancel"
            class="px-4 py-2 rounded-lg text-sm font-medium border border-gray-300 text-gray-700 hover:bg-gray-100"
            :disabled="props.busy"
            @click="onCancel"
          >
            Keep running
          </button>
          <button
            type="button"
            data-testid="cancel-confirmation-modal-confirm"
            class="px-4 py-2 rounded-lg text-sm font-medium text-white"
            :class="
              props.busy
                ? 'bg-red-300 cursor-not-allowed'
                : 'bg-red-600 hover:bg-red-700'
            "
            :disabled="props.busy"
            @click="onConfirm"
          >
            {{ props.busy ? 'Cancelling…' : 'Cancel execution' }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
