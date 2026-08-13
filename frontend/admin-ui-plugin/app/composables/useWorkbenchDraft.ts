import type { ComputedRef, Ref } from 'vue'

/**
 * useWorkbenchDraft
 *
 * Client-side auto-save for the DSL Workbench body editor. Persists the
 * in-progress construct body to `localStorage` (debounced) so a browser
 * refresh doesn't wipe out unsaved edits, and restores it on the next visit
 * to the same construct while the draft is still fresh (< 24h old).
 *
 * Scope is deliberately single-tab: there is no `storage` event listener,
 * so concurrent tabs editing the same construct do not sync with each
 * other. Cross-tab sync is a separate feature.
 *
 * SSR-safe: every `window`/`localStorage` touch is guarded by a
 * `typeof window !== 'undefined'` check (the convention already used by
 * `useStalePolling`'s `typeof document`/`typeof window` guards in this
 * directory), so calling this composable during SSR is a safe no-op.
 *
 * Usage:
 *   const name = computed(() => selectedConstruct.value?.name ?? '')
 *   const { body, dirty, clearDraft, lastSavedAt, restoredFromDraft } =
 *     useWorkbenchDraft(name)
 *
 *   // in template:
 *   <DraftRestoreBanner v-if="restoredFromDraft" @discard="clearDraft" />
 */

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

export interface UseWorkbenchDraftReturn {
  body: Ref<string>
  dirty: ComputedRef<boolean>
  clearDraft: () => void
  lastSavedAt: Ref<number | null>
  /** True when `body` was just restored from a fresh localStorage draft — drive the recovery banner off this. */
  restoredFromDraft: Ref<boolean>
}

export function useWorkbenchDraft(name: string | Ref<string>): UseWorkbenchDraftReturn {
  const nameRef = typeof name === 'string' ? ref(name) : name

  const body = ref('')
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

  function loadFor(currentName: string): void {
    clearSaveTimer()
    const draft = currentName ? readDraft(currentName) : null
    if (draft) {
      body.value = draft.body
      savedBody.value = draft.body
      lastSavedAt.value = draft.savedAt
      restoredFromDraft.value = true
    } else {
      body.value = ''
      savedBody.value = ''
      lastSavedAt.value = null
      restoredFromDraft.value = false
    }
  }

  // Restore synchronously on setup (not gated behind onMounted) so the
  // caller sees the restored body/banner state as soon as the composable
  // returns — matching the immediate-setup style of useStalePolling.
  loadFor(nameRef.value)

  watch(nameRef, (next) => {
    loadFor(next)
  })

  // `watch` is default-flushed (async, batched on the microtask queue), so
  // this callback runs *after* any synchronous programmatic reset above
  // (loadFor / clearDraft) has already brought `savedBody` back in sync
  // with `body`. Comparing against `savedBody` here — rather than a
  // "was this our own write" flag — means a body assignment that merely
  // restates the currently-saved value (restore, clear, or a user undo
  // back to the saved state) never re-arms a pointless save timer.
  watch(body, (value) => {
    clearSaveTimer()
    if (!nameRef.value) return
    if (value === savedBody.value) return
    saveTimer = setTimeout(() => {
      saveTimer = null
      const savedAt = Date.now()
      writeDraft(nameRef.value, { body: value, savedAt })
      savedBody.value = value
      lastSavedAt.value = savedAt
    }, SAVE_DEBOUNCE_MS)
  })

  const dirty = computed(() => body.value !== savedBody.value)

  function clearDraft(): void {
    clearSaveTimer()
    removeDraft(nameRef.value)
    body.value = ''
    savedBody.value = ''
    lastSavedAt.value = null
    restoredFromDraft.value = false
  }

  onUnmounted(() => {
    clearSaveTimer()
  })

  return {
    body,
    dirty,
    clearDraft,
    lastSavedAt,
    restoredFromDraft,
  }
}
