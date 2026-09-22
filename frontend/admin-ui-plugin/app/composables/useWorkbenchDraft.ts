import { type ComputedRef, computed, customRef, onUnmounted, type Ref, ref } from 'vue'
import { useRuntimeConfig } from 'nuxt/app'
import { createEmitter } from '../utils/createEmitter'

export interface WorkbenchDraftPayload {
  body: string
  savedAt: number
}

/**
 * Hardcoded fallbacks for the three workbench-draft tuning knobs. They are
 * registered as defaults in `module.ts` `resolveRuntimeConfig` (so a host can
 * override them through `NUXT_PUBLIC_*` env vars or the host `nuxt.config`)
 * and read at composable setup time via `useRuntimeConfig().public`. Values
 * here are the *last* line of defence — non-positive overrides fall through
 * to these constants.
 */

/** Local-draft entries older than this are dropped on read (TTL). Unit: ms. ENV: `NUXT_PUBLIC_WORKBENCH_DRAFT_TTL_MS`. */
export const DEFAULT_DRAFT_TTL_MS = 24 * 60 * 60 * 1000
/** Debounce window for writing the body to localStorage. Unit: ms. ENV: `NUXT_PUBLIC_WORKBENCH_SAVE_DEBOUNCE_MS`. */
export const DEFAULT_SAVE_DEBOUNCE_MS = 250
/** Debounce window for the optional server autosave. Unit: ms. ENV: `NUXT_PUBLIC_WORKBENCH_SERVER_SAVE_DEBOUNCE_MS`. */
export const DEFAULT_SERVER_SAVE_DEBOUNCE_MS = 3000

/**
 * Resolve the workbench-draft TTL (ms) from `useRuntimeConfig().public`,
 * falling back to {@link DEFAULT_DRAFT_TTL_MS} when missing or non-positive.
 * Tolerates non-Nuxt contexts (test/SSR utility scripts): a thrown
 * `useRuntimeConfig` is swallowed and the fallback is returned.
 */
export function resolveDraftTtlMs(): number {
  try {
    const value = useRuntimeConfig().public?.workbenchDraftTtlMs
    if (typeof value === 'number' && value > 0) return value
  } catch {
    // not in a Nuxt context — fall through to default
  }
  return DEFAULT_DRAFT_TTL_MS
}

/**
 * Resolve the local-storage save debounce (ms) from `useRuntimeConfig().public`,
 * falling back to {@link DEFAULT_SAVE_DEBOUNCE_MS} when missing or non-positive.
 */
export function resolveSaveDebounceMs(): number {
  try {
    const value = useRuntimeConfig().public?.workbenchSaveDebounceMs
    if (typeof value === 'number' && value > 0) return value
  } catch {
    // not in a Nuxt context — fall through to default
  }
  return DEFAULT_SAVE_DEBOUNCE_MS
}

/**
 * Resolve the server autosave debounce (ms) from `useRuntimeConfig().public`,
 * falling back to {@link DEFAULT_SERVER_SAVE_DEBOUNCE_MS} when missing or non-positive.
 */
export function resolveServerSaveDebounceMs(): number {
  try {
    const value = useRuntimeConfig().public?.workbenchServerSaveDebounceMs
    if (typeof value === 'number' && value > 0) return value
  } catch {
    // not in a Nuxt context — fall through to default
  }
  return DEFAULT_SERVER_SAVE_DEBOUNCE_MS
}

export interface WorkbenchDraftServerSync {
  /**
   * Persist the body server-side. Resolves to the server-echoed `savedAt`
   * (server clock) when the backend provides one, otherwise undefined and the
   * caller falls back to the local clock.
   */
  save: (body: string) => Promise<number | undefined>
  /** Fetch the server-side draft payload, or null when none exists. */
  load: (name: string) => Promise<WorkbenchDraftPayload | null>
}

export interface UseWorkbenchDraftOptions {
  server?: WorkbenchDraftServerSync
}

// Tuning knobs are resolved from `useRuntimeConfig().public` at composable
// setup time (see resolveDraftTtlMs / resolveSaveDebounceMs /
// resolveServerSaveDebounceMs below). Defaults are registered in `module.ts`
// `resolveRuntimeConfig` so a host can override them via `NUXT_PUBLIC_*` env
// vars (workbenchDraftTtlMs, workbenchSaveDebounceMs,
// workbenchServerSaveDebounceMs) without touching code. Defaults stay
// identical to the previous hardcoded values so behaviour is unchanged without
// an explicit override.

function draftKey(name: string): string {
  return `cbs.nova.draft.${name}`
}

function hasLocalStorage(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function removeDraft(name: string): void {
  if (!hasLocalStorage()) return
  try {
    window.localStorage.removeItem(draftKey(name))
  } catch {
    // storage disabled/unavailable — nothing to clean up
  }
}

function readDraft(name: string, ttlMs: number): WorkbenchDraftPayload | null {
  if (!hasLocalStorage()) return null

  let raw: string | null = null
  try {
    raw = window.localStorage.getItem(draftKey(name))
  } catch {
    return null
  }
  if (!raw) return null

  let parsed: Partial<WorkbenchDraftPayload> | null = null
  try {
    parsed = JSON.parse(raw) as Partial<WorkbenchDraftPayload>
  } catch {
    parsed = null
  }

  if (!parsed || typeof parsed.body !== 'string' || typeof parsed.savedAt !== 'number') {
    // Malformed entry — drop it so it doesn't linger forever.
    removeDraft(name)
    return null
  }

  if (parsed.savedAt + ttlMs < Date.now()) {
    // Stale (older than the configured TTL) — clear on read. The server
    // draft (no TTL) remains the durable copy.
    removeDraft(name)
    return null
  }

  return { body: parsed.body, savedAt: parsed.savedAt }
}

function writeDraft(name: string, payload: WorkbenchDraftPayload): void {
  if (!hasLocalStorage()) return
  try {
    window.localStorage.setItem(draftKey(name), JSON.stringify(payload))
  } catch {
    // quota exceeded / private browsing — draft just won't persist
  }
}

interface WorkbenchDraftEvents {
  nameChanged: string
  bodyChanged: string
  restored: WorkbenchDraftPayload
  saved: WorkbenchDraftPayload
  cleared: undefined
  [key: string]: unknown
}

export interface UseWorkbenchDraftReturn {
  body: Ref<string>
  dirty: ComputedRef<boolean>
  clearDraft: () => void
  lastSavedAt: Ref<number | null>
  /** True when `body` was just restored from a fresh draft — drive the recovery banner off this. */
  restoredFromDraft: Ref<boolean>
  /** True when the last server autosave failed — the local copy is the only one until it recovers. */
  autosaveOffline: Ref<boolean>
  /** Switch to a different draft key and load its persisted body. */
  setName: (name: string) => void
  /** Listen for debounced localStorage save events. */
  onSaved: (handler: (payload: WorkbenchDraftPayload) => void) => () => void
  /** Listen for draft-restore events. */
  onRestored: (handler: (payload: WorkbenchDraftPayload) => void) => () => void
  /** Listen for draft-clear events. */
  onCleared: (handler: () => void) => () => void
}

export function useWorkbenchDraft(
  name: string | Ref<string> = '',
  options: UseWorkbenchDraftOptions = {},
): UseWorkbenchDraftReturn {
  const server = options.server
  const currentName = ref(typeof name === 'string' ? name : name.value)
  const emitter = createEmitter<WorkbenchDraftEvents>()

  // Resolve the three tuning knobs once at composable setup. Snapshotting
  // them locally keeps the resolved values stable for the lifetime of this
  // composable instance even if the host reconfigures `useRuntimeConfig`
  // later (e.g. a hot-reloaded module).
  const draftTtlMs = resolveDraftTtlMs()
  const saveDebounceMs = resolveSaveDebounceMs()
  const serverSaveDebounceMs = resolveServerSaveDebounceMs()

  // Underlying storage for the body; the public `body` is a customRef that
  // emits a `bodyChanged` event whenever it is mutated through the public
  // setter. Programmatic writes can suppress the emit so they never re-arm
  // the debounced save timer.
  const _body = ref('')
  const emitBodyChanges = ref(true)

  const body = customRef<string>((track, trigger) => ({
    get() {
      track()
      return _body.value
    },
    set(value) {
      const changed = _body.value !== value
      _body.value = value
      if (changed && emitBodyChanges.value) {
        emitter.emit('bodyChanged', value)
      }
      trigger()
    },
  }))

  function setBodySilently(value: string): void {
    emitBodyChanges.value = false
    body.value = value
    emitBodyChanges.value = true
  }

  const savedBody = ref('')
  const serverSavedBody = ref('')
  const lastSavedAt = ref<number | null>(null)
  const restoredFromDraft = ref(false)
  const autosaveOffline = ref(false)

  let saveTimer: ReturnType<typeof setTimeout> | null = null
  let serverTimer: ReturnType<typeof setTimeout> | null = null
  let serverSavePending = false
  let serverSaveQueued = false
  let serverLoadToken = 0

  function clearSaveTimer(): void {
    if (saveTimer != null) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
  }

  function clearServerTimer(): void {
    if (serverTimer != null) {
      clearTimeout(serverTimer)
      serverTimer = null
    }
  }

  function clearServerState(): void {
    clearServerTimer()
    serverSavePending = false
    serverSaveQueued = false
    autosaveOffline.value = false
  }

  async function flushServerSave(): Promise<void> {
    if (serverSavePending) {
      serverSaveQueued = true
      return
    }
    const nameAtFlush = currentName.value
    const value = _body.value
    if (!nameAtFlush || value === serverSavedBody.value) return
    serverSavePending = true
    try {
      const echo = await server?.save(value)
      if (currentName.value !== nameAtFlush) return
      serverSavedBody.value = value
      autosaveOffline.value = false
      const savedAt = typeof echo === 'number' ? echo : Date.now()
      writeDraft(nameAtFlush, { body: value, savedAt })
      savedBody.value = value
      lastSavedAt.value = savedAt
    } catch {
      // The localStorage write from the local debounce already happened, so
      // the draft survives; surface the degraded mode instead.
      autosaveOffline.value = true
    } finally {
      serverSavePending = false
      if (serverSaveQueued) {
        serverSaveQueued = false
        void flushServerSave()
      }
    }
  }

  function armServerTimer(): void {
    if (!server) return
    clearServerTimer()
    serverTimer = setTimeout(() => {
      serverTimer = null
      void flushServerSave()
    }, serverSaveDebounceMs)
  }

  async function resolveServerDraft(nameAtLoad: string, local: WorkbenchDraftPayload | null) {
    if (!server || !nameAtLoad) return
    const token = ++serverLoadToken
    let serverDraft: WorkbenchDraftPayload | null = null
    try {
      serverDraft = await server.load(nameAtLoad)
    } catch {
      return
    }
    if (token !== serverLoadToken || currentName.value !== nameAtLoad) return
    if (!serverDraft) return
    if (local && serverDraft.savedAt <= local.savedAt) return
    setBodySilently(serverDraft.body)
    savedBody.value = serverDraft.body
    serverSavedBody.value = serverDraft.body
    lastSavedAt.value = serverDraft.savedAt
    restoredFromDraft.value = true
    writeDraft(nameAtLoad, serverDraft)
    emitter.emit('restored', serverDraft)
  }

  function loadFor(currentNameVal: string): void {
    clearSaveTimer()
    clearServerState()
    serverLoadToken++
    const draft = currentNameVal ? readDraft(currentNameVal, draftTtlMs) : null
    if (draft) {
      setBodySilently(draft.body)
      savedBody.value = draft.body
      serverSavedBody.value = draft.body
      lastSavedAt.value = draft.savedAt
      restoredFromDraft.value = true
      emitter.emit('restored', draft)
    } else {
      setBodySilently('')
      savedBody.value = ''
      serverSavedBody.value = ''
      lastSavedAt.value = null
      restoredFromDraft.value = false
    }
    void resolveServerDraft(currentNameVal, draft)
  }

  // Restore synchronously on setup so the caller sees the restored banner
  // state as soon as the composable returns.
  loadFor(currentName.value)

  // Event-driven side effects replace the previous `watch` usage.
  const stopNameListener = emitter.on('nameChanged', (next: string) => {
    loadFor(next)
  })

  const stopBodyListener = emitter.on('bodyChanged', (value: string) => {
    clearSaveTimer()
    if (!currentName.value) return
    if (value === savedBody.value) return
    saveTimer = setTimeout(() => {
      saveTimer = null
      const savedAt = Date.now()
      writeDraft(currentName.value, { body: value, savedAt })
      savedBody.value = value
      lastSavedAt.value = savedAt
      emitter.emit('saved', { body: value, savedAt })
    }, saveDebounceMs)
    armServerTimer()
  })

  const dirty = computed(() => body.value !== savedBody.value)

  function setName(next: string): void {
    if (currentName.value === next) return
    currentName.value = next
    emitter.emit('nameChanged', next)
  }

  function clearDraft(): void {
    clearSaveTimer()
    clearServerState()
    serverLoadToken++
    removeDraft(currentName.value)
    setBodySilently('')
    savedBody.value = ''
    serverSavedBody.value = ''
    lastSavedAt.value = null
    restoredFromDraft.value = false
    emitter.emit('cleared')
  }

  onUnmounted(() => {
    clearSaveTimer()
    clearServerTimer()
    stopNameListener()
    stopBodyListener()
  })

  return {
    body,
    dirty,
    clearDraft,
    lastSavedAt,
    restoredFromDraft,
    autosaveOffline,
    setName,
    onSaved: (handler) => emitter.on('saved', handler),
    onRestored: (handler) => emitter.on('restored', handler),
    onCleared: (handler) => emitter.on('cleared', handler),
  }
}
