<script setup lang="ts">
import { ref } from 'vue'
import {
  type Notification,
  type NotificationKind,
  useNotifications,
} from '../composables/useNotifications'
import CbsDrawer from './CbsDrawer.vue'

withDefaults(
  defineProps<{
    drawerTitle?: string
    drawerWidthClass?: string
  }>(),
  {
    drawerTitle: 'Notifications',
    drawerWidthClass: 'w-96',
  },
)

const emit = defineEmits<{
  (event: 'open'): void
  (event: 'select', id: string): void
}>()

const drawerOpen = ref(false)
// The widget renders every notification — persistent and toast — because a
// "toast" is just a transient form of the same record. Toasts that are still
// on screen appear here with an `ephemeral` flag so the user can tell them
// apart.
const { notifications, unreadCount, markRead, markAllRead, dismiss } = useNotifications()

function open() {
  drawerOpen.value = true
  emit('open')
}

function handleSelect(note: Notification) {
  markRead(note.id)
  emit('select', note.id)
}

const kindLabels: Record<NotificationKind, string> = {
  info: 'Info',
  success: 'Success',
  warning: 'Warning',
  error: 'Error',
}

const kindBadgeClasses: Record<NotificationKind, string> = {
  info: 'bg-blue-900/50 text-blue-200 border-blue-800',
  success: 'bg-green-900/50 text-green-200 border-green-800',
  warning: 'bg-yellow-900/50 text-yellow-200 border-yellow-800',
  error: 'bg-red-900/50 text-red-200 border-red-800',
}

function timeAgo(ts: number): string {
  const diff = Math.max(0, Date.now() - ts)
  const s = Math.floor(diff / 1000)
  if (s < 60) return `${s}s ago`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h ago`
  const d = Math.floor(h / 24)
  return `${d}d ago`
}

function isEphemeral(note: Notification): boolean {
  return note.expiresAt !== undefined
}
</script>

<template>
  <div
    data-testid="notifications-widget"
    class="inline-flex items-center gap-2 rounded-md border border-neutral-300 bg-white px-2.5 py-1 text-xs text-neutral-700 shadow-sm"
  >
    <span class="font-medium uppercase tracking-wide text-neutral-500" aria-hidden="true">🔔</span>
    <span
      class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-semibold"
      :class="unreadCount > 0 ? 'bg-primary-500 text-white' : 'bg-neutral-200 text-neutral-700'"
      data-testid="notifications-widget-count"
      :title="`${unreadCount} unread`"
    >
      {{ unreadCount }}
    </span>
    <button
      type="button"
      class="px-2 py-0.5 rounded text-xs font-medium border border-neutral-300 hover:bg-neutral-100"
      data-testid="notifications-widget-details"
      aria-label="Open notifications"
      @click="open"
    >
      Details
    </button>

    <CbsDrawer
      v-model:open="drawerOpen"
      :title="drawerTitle"
      close-label="Close notifications"
      test-id="notifications-drawer"
      :width-class="drawerWidthClass"
    >
      <div
        class="flex items-center justify-between px-3 py-2 border-b border-gray-800 text-xs text-gray-400"
      >
        <span class="uppercase tracking-wide">
          {{ notifications.length }}
          total ·
          {{ unreadCount }}
          unread
        </span>
        <button
          type="button"
          class="px-2 py-0.5 rounded border border-gray-700 text-gray-300 hover:bg-gray-800 disabled:opacity-50"
          data-testid="notifications-drawer-mark-all"
          :disabled="unreadCount === 0"
          @click="markAllRead"
        >
          Mark all read
        </button>
      </div>
      <p
        v-if="notifications.length === 0"
        class="px-3 py-6 text-center text-xs text-gray-500"
        data-testid="notifications-drawer-empty"
      >
        No notifications yet.
      </p>
      <ul v-else class="divide-y divide-gray-800" data-testid="notifications-drawer-list">
        <li
          v-for="note in notifications"
          :key="note.id"
          :data-testid="'notifications-drawer-item-' + note.id"
          :class="[
            'px-3 py-2 flex items-start justify-between gap-2 cursor-pointer hover:bg-gray-800/50',
            note.read ? 'opacity-60' : '',
          ]"
          @click="handleSelect(note)"
          @keydown.enter="handleSelect(note)"
          @keydown.space.prevent="handleSelect(note)"
        >
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <span
                :class="[
                  'inline-flex items-center px-1.5 py-0.5 rounded border text-[10px] uppercase tracking-wide',
                  kindBadgeClasses[note.kind] ?? kindBadgeClasses.info,
                ]"
              >
                {{ kindLabels[note.kind] ?? kindLabels.info }}
              </span>
              <span
                v-if="isEphemeral(note)"
                class="inline-flex items-center px-1 py-0.5 rounded border border-gray-700 text-[10px] uppercase tracking-wide text-gray-400"
                :data-testid="'notifications-drawer-toast-' + note.id"
                title="This is a transient toast — also shown in the floating stack."
              >
                toast
              </span>
              <span class="text-[10px] text-gray-500">{{ timeAgo(note.createdAt) }}</span>
            </div>
            <p class="mt-1 text-xs text-gray-100 break-words">{{ note.message }}</p>
          </div>
          <button
            type="button"
            class="shrink-0 px-1 text-gray-500 hover:text-gray-200"
            :aria-label="'Dismiss ' + note.kind + ' notification'"
            :data-testid="'notifications-drawer-dismiss-' + note.id"
            @click.stop="dismiss(note.id)"
          >
            ✕
          </button>
        </li>
      </ul>
    </CbsDrawer>
  </div>
</template>
