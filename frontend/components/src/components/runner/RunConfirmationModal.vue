<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { useModalDialog } from '../../composables/useModalDialog'

const props = defineProps<{
  show: boolean
  payload: unknown
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const acknowledged = ref(false)
const skipPreference = ref(false)

const dialogRef = ref<HTMLElement | null>(null)
const { open: openDialog, close: closeDialog } = useModalDialog(dialogRef, {
  onClose: onCancel,
})

watch(
  () => props.show,
  (open) => {
    if (open) {
      acknowledged.value = false
      skipPreference.value = readSkipPreference()
      openDialog()
    } else {
      closeDialog()
    }
  },
  { immediate: true },
)

function readSkipPreference(): boolean {
  if (typeof window === 'undefined') return false
  try {
    return window.sessionStorage.getItem('skip-run-confirm') === '1'
  } catch {
    return false
  }
}

function writeSkipPreference(value: boolean) {
  if (typeof window === 'undefined') return
  try {
    if (value) window.sessionStorage.setItem('skip-run-confirm', '1')
    else window.sessionStorage.removeItem('skip-run-confirm')
  } catch {
    // ignore storage failures
  }
}

watch(skipPreference, (value) => writeSkipPreference(value))

// biome-ignore lint/correctness/noUnusedVariables: used in the modal template
const payloadText = computed(() => {
  try {
    return JSON.stringify(props.payload, null, 2)
  } catch {
    return String(props.payload)
  }
})

function onConfirm() {
  if (!acknowledged.value) return
  emit('confirm')
}

function onCancel() {
  emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <!-- biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click dismisses modal -->
    <div
      v-if="props.show"
      ref="dialogRef"
      data-testid="run-confirmation-modal"
      class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="run-confirm-title"
      tabindex="-1"
      @click.self="onCancel"
    >
      <div class="bg-white rounded-xl shadow-xl max-w-lg w-full flex flex-col max-h-[90vh]">
        <header class="px-6 py-4 border-b border-gray-200">
          <h2 id="run-confirm-title" class="text-lg font-semibold text-gray-900">Confirm Run</h2>
          <p class="text-sm text-gray-600 mt-1">This will execute the workflow on the backend.</p>
        </header>

        <div class="px-6 py-4 overflow-y-auto flex-1">
          <h3 class="text-sm font-semibold text-gray-700 mb-2">Payload</h3>
          <pre
            class="bg-gray-900 text-gray-100 text-xs rounded-lg p-3 overflow-auto max-h-64 whitespace-pre-wrap break-words"
          >{{ payloadText }}</pre>

          <label class="mt-4 inline-flex items-center gap-2 text-sm text-gray-700">
            <input
              v-model="acknowledged"
              type="checkbox"
              class="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            >
            I understand this will run the workflow.
          </label>

          <label class="mt-2 inline-flex items-center gap-2 text-sm text-gray-500">
            <input
              v-model="skipPreference"
              type="checkbox"
              class="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            >
            Don't ask again this session
          </label>
        </div>

        <footer class="px-6 py-4 border-t border-gray-200 flex justify-end gap-2">
          <button
            type="button"
            data-testid="run-confirmation-modal-cancel"
            class="px-4 py-2 rounded-lg text-sm font-medium border border-gray-300 text-gray-700 hover:bg-gray-100"
            @click="onCancel"
          >
            Cancel
          </button>
          <button
            type="button"
            data-testid="run-confirmation-modal-confirm"
            class="px-4 py-2 rounded-lg text-sm font-medium text-white"
            :class="acknowledged ? 'bg-blue-600 hover:bg-blue-700' : 'bg-blue-300 cursor-not-allowed'"
            :disabled="!acknowledged"
            @click="onConfirm"
          >
            Confirm Run
          </button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>
