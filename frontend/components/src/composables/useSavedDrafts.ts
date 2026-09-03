import {
  type App,
  getCurrentInstance,
  getCurrentScope,
  onScopeDispose,
  type Ref,
  reactive,
  toRef,
} from 'vue'

export interface SavedDraftSummary {
  name: string
  type?: string
  status?: string
  version?: string
  updatedAt: number
}

export interface UseSavedDraftsOptions {
  /** Loads the draft list. The most recently registered fetcher wins. */
  fetcher?: () => Promise<unknown>
  /** Notified when a refresh fails. */
  onError?: (message: string) => void
  /**
   * Handles a draft being picked. Registered for the lifetime of the calling
   * component's scope, so a page can own selection while the navbar widget
   * merely dispatches to it.
   */
  onSelect?: (name: string) => void
}

export interface UseSavedDraftsReturn {
  drafts: Ref<SavedDraftSummary[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  selectedName: Ref<string | null>
  refresh: () => Promise<void>
  /** Dispatches to the registered handler. Returns false when nobody handled it. */
  select: (name: string) => boolean
}

interface ReactiveDraftsState {
  drafts: SavedDraftSummary[]
  loading: boolean
  error: string | null
  selectedName: string | null
}

interface DraftsStore {
  state: ReactiveDraftsState
  fetcher?: () => Promise<unknown>
  onError?: (message: string) => void
  selectHandler?: (name: string) => void
  inFlight: Promise<void> | null
  refreshPending: boolean
}

/**
 * Stores are keyed by Vue app instance rather than held in a module-level
 * singleton. Two consequences matter:
 *
 *  - Consumers in the same app share state regardless of setup order, so a
 *    layout may read state that a page registers later. Provide/inject cannot
 *    do this: a layout's `inject` runs before its page's `provide`.
 *  - Each SSR request builds its own Vue app, so requests cannot see each
 *    other's drafts.
 */
let stores = new WeakMap<App, DraftsStore>()
/** Fallback for calls made outside a component (tests, plain scripts). */
let ambientStore: DraftsStore | undefined

function createStore(): DraftsStore {
  return {
    state: reactive<ReactiveDraftsState>({
      drafts: [],
      loading: false,
      error: null,
      selectedName: null,
    }),
    inFlight: null,
    refreshPending: false,
  }
}

function resolveStore(): DraftsStore {
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
export function resetSavedDraftsState(): void {
  stores = new WeakMap()
  ambientStore = undefined
}

export function useSavedDrafts(options: UseSavedDraftsOptions = {}): UseSavedDraftsReturn {
  const store = resolveStore()
  const { state } = store

  if (options.fetcher) store.fetcher = options.fetcher
  if (options.onError) store.onError = options.onError

  if (options.onSelect) {
    const handler = options.onSelect
    store.selectHandler = handler
    if (getCurrentScope()) {
      onScopeDispose(() => {
        if (store.selectHandler === handler) store.selectHandler = undefined
      })
    }
  }

  async function refresh(): Promise<void> {
    const fetcher = store.fetcher
    if (!fetcher) {
      // A consumer wants data before anyone can load it — replay once a
      // fetcher registers. Lets the navbar widget auto-load even though it
      // mounts before the page that owns the API client.
      store.refreshPending = true
      return
    }
    if (store.inFlight) return store.inFlight
    state.loading = true
    state.error = null
    store.inFlight = (async () => {
      try {
        const result = (await fetcher()) as SavedDraftSummary[] | null | undefined
        state.drafts = Array.isArray(result) ? result : []
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err)
        state.error = message
        state.drafts = []
        store.onError?.(message)
      } finally {
        state.loading = false
        store.inFlight = null
      }
    })()
    return store.inFlight
  }

  if (options.fetcher && store.refreshPending) {
    store.refreshPending = false
    void refresh()
  }

  function select(name: string): boolean {
    const handler = store.selectHandler
    if (!handler) return false
    handler(name)
    return true
  }

  return {
    drafts: toRef(state, 'drafts'),
    loading: toRef(state, 'loading'),
    error: toRef(state, 'error'),
    selectedName: toRef(state, 'selectedName'),
    refresh,
    select,
  }
}
