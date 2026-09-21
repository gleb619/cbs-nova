import type { ComputedRef, Ref } from 'vue'
import {
  type Notification,
  type NotificationKind,
  type UseNotificationsReturn,
  useNotifications,
} from './useNotifications'

export type { Notification, NotificationKind }

/** Default duration for ephemeral toasts in ms. Errors default to sticky (0). */
const DEFAULT_DURATION = 4000

export interface UseToastReturn {
  /** Every notification, newest first — toasts live here too. */
  notifications: Ref<Notification[]>
  /** Notifications shown as toasts right now (sticky + non-expired). */
  activeToasts: ComputedRef<Notification[]>
  /** Push an ephemeral or sticky toast. Errors default to sticky. */
  push: (message: string, kind?: NotificationKind, duration?: number) => Notification
  /** Dismiss a toast (and the underlying notification). */
  dismiss: (id: string) => void
  /** Convenience helpers. */
  success: (message: string, duration?: number) => Notification
  error: (message: string, duration?: number) => Notification
  info: (message: string, duration?: number) => Notification
  warning: (message: string, duration?: number) => Notification
}

/**
 * A "toast" is a notification with `toast: true`. Every push through this
 * composable creates one record in the same list the bell widget reads from,
 * so the same `dismiss(id)` removes it from every surface. There is no
 * separate toast queue — the underlying `useNotifications` store is the only
 * source of truth.
 *
 * Use `useNotifications` directly when you need a persistent record without
 * the floating-stack behaviour.
 */
export function useToast(): UseToastReturn {
  const notifications: UseNotificationsReturn = useNotifications()

  function push(message: string, kind: NotificationKind = 'info', duration?: number): Notification {
    const d = duration ?? (kind === 'error' ? 0 : DEFAULT_DURATION)
    return notifications.push(message, kind, { duration: d, toast: true })
  }

  function dismiss(id: string): void {
    notifications.dismiss(id)
  }

  return {
    notifications: notifications.notifications,
    activeToasts: notifications.activeToasts,
    push,
    dismiss,
    success: (msg, dur) => push(msg, 'success', dur),
    error: (msg, dur) => push(msg, 'error', dur ?? 0),
    info: (msg, dur) => push(msg, 'info', dur),
    warning: (msg, dur) => push(msg, 'warning', dur),
  }
}
