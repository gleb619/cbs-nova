import { type ComputedRef, type Ref, computed, customRef, onUnmounted, ref } from 'vue'
import { createEmitter } from '../utils/createEmitter'

export interface WorkbenchDraftPayload {
  body: string
  savedAt: number
}

const DRAFT_TTL_MS = 24 * 60 * 60 * 1000
const SAVE_DEBOUNCE_MS = 250

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

function readDraft(name: string): WorkbenchDraftPayload | null {
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

  if (parsed.savedAt + DRAFT_TTL_MS < Date.now()) {
    // Stale (older than 24h) — clear on read per T201 TTL requirement.
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
  /** True when `body` was just restored from a fresh localStorage draft — drive the recovery banner off this. */
  restoredFromDraft: Ref<boolean>
  /** Switch to a different draft key and load its persisted body. */
  setName: (name: string) => void
  /** Listen for debounced localStorage save events. */
  onSaved: (handler: (payload: WorkbenchDraftPayload) => void) => () => void
  /** Listen for draft-restore events. */
  onRestored: (handler: (payload: WorkbenchDraftPayload) => void) => () => void
  /** Listen for draft-clear events. */
  onCleared: (handler: () => void) => () => void
}

export function useWorkbenchDraft(name: string | Ref<string> = ''): UseWorkbenchDraftReturn {
  const currentName = ref(typeof name === 'string' ? name : name.value)
  const emitter = createEmitter<WorkbenchDraftEvents>()

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
  const lastSavedAt = ref<number | null>(null)
  const restoredFromDraft = ref(false)

  let saveTimer: ReturnType<typeof setTimeout> | null = null

  function clearSaveTimer(): void {
    if (saveTimer != null) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
  }

  function loadFor(currentNameVal: string): void {
    clearSaveTimer()
    const draft = currentNameVal ? readDraft(currentNameVal) : null
    if (draft) {
      setBodySilently(draft.body)
      savedBody.value = draft.body
      lastSavedAt.value = draft.savedAt
      restoredFromDraft.value = true
      emitter.emit('restored', draft)
    } else {
      setBodySilently('')
      savedBody.value = ''
      lastSavedAt.value = null
      restoredFromDraft.value = false
    }
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
    }, SAVE_DEBOUNCE_MS)
  })

  const dirty = computed(() => body.value !== savedBody.value)

  function setName(next: string): void {
    if (currentName.value === next) return
    currentName.value = next
    emitter.emit('nameChanged', next)
  }

  function clearDraft(): void {
    clearSaveTimer()
    removeDraft(currentName.value)
    setBodySilently('')
    savedBody.value = ''
    lastSavedAt.value = null
    restoredFromDraft.value = false
    emitter.emit('cleared')
  }

  onUnmounted(() => {
    clearSaveTimer()
    stopNameListener()
    stopBodyListener()
  })

  return {
    body,
    dirty,
    clearDraft,
    lastSavedAt,
    restoredFromDraft,
    setName,
    onSaved: (handler) => emitter.on('saved', handler),
    onRestored: (handler) => emitter.on('restored', handler),
    onCleared: (handler) => emitter.on('cleared', handler),
  }
}
