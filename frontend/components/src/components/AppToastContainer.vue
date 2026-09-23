<script setup lang="ts">
import { type NotificationKind, useToast } from '../composables/useToast'

const { activeToasts, dismiss } = useToast()

const typeClasses: Record<NotificationKind, string> = {
  info: 'bg-blue-50 border-blue-200 text-blue-800',
  success: 'bg-green-50 border-green-200 text-green-800',
  warning: 'bg-yellow-50 border-yellow-200 text-yellow-800',
  error: 'bg-red-50 border-red-200 text-red-800',
}

const typeIcons: Record<NotificationKind, string> = {
  info: 'ℹ',
  success: '✓',
  warning: '⚠',
  error: '✕',
}

function classesFor(kind: NotificationKind): string {
  return typeClasses[kind] ?? typeClasses.info
}

function iconFor(kind: NotificationKind): string {
  return typeIcons[kind] ?? typeIcons.info
}
</script>

<template>
  <!-- Teleport so the stack sits above every layout regardless of overflow. -->
  <Teleport to="body">
    <section
      data-testid="app-toast-container"
      class="fixed top-5 right-5 z-50 flex flex-col gap-3 w-full max-w-sm pointer-events-none"
      aria-label="Notifications"
    >
      <TransitionGroup
        enter-active-class="transform ease-out duration-300 transition"
        enter-from-class="translate-y-2 opacity-0 sm:translate-y-0 sm:translate-x-2"
        enter-to-class="translate-y-0 opacity-100 sm:translate-x-0"
        leave-active-class="transition ease-in duration-200"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0 translate-y-1"
      >
        <div
          v-for="toast in activeToasts"
          :key="toast.id"
          data-testid="app-toast"
          :data-toast-kind="toast.kind"
          :class="[
            'pointer-events-auto flex items-start justify-between gap-3 p-4 rounded-lg shadow-lg border text-sm font-medium',
            classesFor(toast.kind),
          ]"
          role="status"
        >
          <div class="flex items-start gap-2">
            <span aria-hidden="true" class="text-base leading-5">{{ iconFor(toast.kind) }}</span>
            <span class="leading-5">{{ toast.message }}</span>
          </div>
          <button
            type="button"
            class="shrink-0 -m-1 p-1 rounded opacity-70 hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-current"
            data-testid="app-toast-dismiss"
            aria-label="Dismiss notification"
            @click="dismiss(toast.id)"
          >
            ✕
          </button>
        </div>
      </TransitionGroup>
    </section>
  </Teleport>
</template>
