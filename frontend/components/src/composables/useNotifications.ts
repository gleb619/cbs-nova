import {
  type App,
  type ComputedRef,
  computed,
  getCurrentInstance,
  type Ref,
  reactive,
  toRef,
} from 'vue'

/** Visual treatment for a notification — toast, snackbar, bell entry all share these. */
export type NotificationKind = 'info' | 'success' | 'warning' | 'error'

/**
 * A unified notification record. Every surface that gives the user feedback
 * (toast stack, bell widget, snackbar, push, …) reads from the same list.
 * Whether a record is rendered as a toast vs. only in history is the `toast`
 * flag; whether it auto-dismisses is `expiresAt`. Both are independent —
 * sticky toasts have `toast: true, expiresAt: undefined`.
 */
export interface Notification {
  id: string
  kind: NotificationKind
  message: string
  createdAt: number
  read: boolean
  /** When true, the floating toast stack renders this record. */
  toast: boolean
  /** When set, this record auto-removes at this epoch-ms. */
  expiresAt?: number
}

export interface PushOptions {
  /** Auto-dismiss delay in ms. > 0 schedules removal; 0 or undefined = sticky/persistent. */
  duration?: number
  /** Mark as a toast (shown in the floating stack). Default false. */
  toast?: boolean
}

export interface UseNotificationsReturn {
  /** Every notification currently known, newest first. */
  notifications: Ref<Notification[]>
  /** Notifications shown as toasts right now (sticky + non-expired). */
  activeToasts: ComputedRef<Notification[]>
  /** Non-toast notifications — the bell widget history. */
  history: ComputedRef<Notification[]>
  unreadCount: ComputedRef<number>
  /** Push a notification. Returns the created record. */
  push: (message: string, kind?: NotificationKind, options?: PushOptions) => Notification
  /** Removes a notification from every surface. */
  dismiss: (id: string) => void
  markRead: (id: string) => void
  markAllRead: () => void
  clear: () => void
}

interface NotificationsStore {
  state: { notifications: Notification[] }
  timers: Map<string, ReturnType<typeof setTimeout>>
  /** Cap so the list cannot grow without bound. */
  maxItems: number
}

/**
 * Stores are keyed by Vue app instance rather than a module singleton, mirroring
 * `useSavedDrafts`: any consumer in the same app sees the same queue regardless
 * of mount order, and SSR requests build isolated apps so notifications cannot
 * leak between requests.
 */
let stores = new WeakMap<App, NotificationsStore>()
let ambientStore: NotificationsStore | undefined

const DEFAULT_MAX_ITEMS = 50

function createStore(): NotificationsStore {
  return {
    state: reactive<{ notifications: Notification[] }>({ notifications: [] }),
    timers: new Map(),
    maxItems: DEFAULT_MAX_ITEMS,
  }
}

function resolveStore(): NotificationsStore {
  const app = getCurrentInstance()?.appContext.app
  if (app) {
    let store = stores.get(app)
    if (!store) {
      store = createStore()
      stores.set(app, store)
    }
    ambientStore = store
    return store
  }
  if (!ambientStore) ambientStore = createStore()
  return ambientStore
}

/** Test helper — drops every store so cases cannot leak into each other. */
export function resetNotificationsState(): void {
  stores = new WeakMap()
  ambientStore = undefined
}

function newId(): string {
  return `n_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function scheduleDismiss(store: NotificationsStore, note: Notification): void {
  if (note.expiresAt === undefined) return
  const delay = Math.max(0, note.expiresAt - Date.now())
  if (delay <= 0) {
    removeNotification(store, note.id)
    return
  }
  const handle = setTimeout(() => removeNotification(store, note.id), delay)
  store.timers.set(note.id, handle)
}

function cancelTimer(store: NotificationsStore, id: string): void {
  const handle = store.timers.get(id)
  if (handle) {
    clearTimeout(handle)
    store.timers.delete(id)
  }
}

function removeNotification(store: NotificationsStore, id: string): void {
  cancelTimer(store, id)
  store.state.notifications = store.state.notifications.filter((n) => n.id !== id)
}

export function useNotifications(): UseNotificationsReturn {
  const store = resolveStore()
  const { state } = store

  function push(
    message: string,
    kind: NotificationKind = 'info',
    options: PushOptions = {},
  ): Notification {
    const toast = options.toast ?? false
    const expiresAt =
      options.duration !== undefined && options.duration > 0
        ? Date.now() + options.duration
        : undefined
    const note: Notification = {
      id: newId(),
      kind,
      message,
      createdAt: Date.now(),
      read: false,
      toast,
      expiresAt,
    }
    state.notifications.unshift(note)
    while (state.notifications.length > store.maxItems) state.notifications.pop()
    scheduleDismiss(store, note)
    return note
  }

  function dismiss(id: string): void {
    removeNotification(store, id)
  }

  function markRead(id: string): void {
    const note = state.notifications.find((n) => n.id === id)
    if (note) note.read = true
  }

  function markAllRead(): void {
    state.notifications.forEach((n) => {
      n.read = true
    })
  }

  function clear(): void {
    // Cancel every pending timer so an early expiry cannot resurrect state.
    state.notifications.forEach((n) => {
      cancelTimer(store, n.id)
    })
    state.notifications = []
  }

  const activeToasts = computed(() =>
    state.notifications.filter(
      (n) => n.toast && (n.expiresAt === undefined || n.expiresAt > Date.now()),
    ),
  )
  const history = computed(() => state.notifications.filter((n) => !n.toast))
  const unreadCount = computed(() => history.value.filter((n) => !n.read).length)

  return {
    notifications: toRef(state, 'notifications'),
    activeToasts,
    history,
    unreadCount,
    push,
    dismiss,
    markRead,
    markAllRead,
    clear,
  }
}
