<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useModalDialog } from '../../composables/useModalDialog'

const props = defineProps<{
  show: boolean
  runId?: string
  route?: string
  busy?: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const dialogRef = ref<HTMLElement | null>(null)
const { open: openDialog } = useModalDialog(dialogRef, {
  onClose: onCancel,
})

onMounted(() => {
  if (props.show) void openDialog()
})

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
      ref="dialogRef"
      data-testid="vhs-delete-confirmation-modal"
      class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="vhs-delete-confirm-title"
      tabindex="-1"
      @click.self="onCancel"
    >
      <div class="bg-white rounded-xl shadow-xl max-w-md w-full flex flex-col">
        <header class="px-6 py-4 border-b border-neutral-200">
          <h2 id="vhs-delete-confirm-title" class="text-lg font-semibold text-neutral-900">
            Delete tape?
          </h2>
          <p class="text-sm text-neutral-600 mt-1">
            This permanently removes the recorded tape. The original execution is not affected.
          </p>
        </header>

        <div v-if="props.runId" class="px-6 py-3 space-y-1">
          <div v-if="props.route" class="text-sm text-neutral-700">
            <span class="text-neutral-500">Route:</span> {{ props.route }}
          </div>
          <div class="text-xs text-neutral-500 font-mono">{{ props.runId }}</div>
        </div>

        <footer class="px-6 py-4 border-t border-neutral-200 flex justify-end gap-2">
          <button
            type="button"
            data-testid="vhs-delete-confirmation-modal-cancel"
            class="px-4 py-2 rounded-lg text-sm font-medium border border-neutral-300 text-neutral-700 hover:bg-neutral-100"
            :disabled="props.busy"
            @click="onCancel"
          >
            Keep tape
          </button>
          <button
            type="button"
            data-testid="vhs-delete-confirmation-modal-confirm"
            class="px-4 py-2 rounded-lg text-sm font-medium text-white"
            :class="
              props.busy
                ? 'bg-error-300 cursor-not-allowed'
                : 'bg-error-600 hover:bg-error-700'
            "
            :disabled="props.busy"
            @click="onConfirm"
          >
            {{ props.busy ? 'Deleting…' : 'Delete tape' }}
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
