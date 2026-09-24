import type { UseDraftDirtyReturn } from '@cbs/admin-ui-plugin/composables/useDraftDirty'
import { useDraftDirty } from '@cbs/admin-ui-plugin/composables/useDraftDirty'
import type { UseDraftSaveReturn } from '@cbs/admin-ui-plugin/composables/useDraftSave'
import { useEventListener } from '@vueuse/core'
import { type ComputedRef, computed } from 'vue'

export interface UseWorkbenchShortcutsOptions {
  draftDirty?: UseDraftDirtyReturn
  draftSave: UseDraftSaveReturn
  /** True when a blocking modal (new-definition or delete) is open. */
  isAnyModalOpen: ComputedRef<boolean>
  /** Opens the new-definition modal — bound to the New header button. */
  openNewPanel: () => void
  /** Toggles the object-search drawer — bound to the Objects explorer button. */
  toggleObjectsSearch: () => void
}

/**
 * Platform detection — re-evaluated on every call so tests that mutate
 * `navigator.platform` between mounts (e.g. the macOS UA spec) see the
 * updated value. The handler reads from the live navigator instead of a
 * module-load snapshot.
 */
function detectMac(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')
  )
}

/** Modifier suffix printed after the primary modifier (currently constant). */
export const ALT = 'Alt'

/** True when the event target is a focused form field / contenteditable. */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable
}

/**
 * Dispatch a synthetic click on the named DropdownMenu trigger. In real DOM
 * this opens the menu via the component's own toggle handler; in tests the
 * DropdownMenu is stub-replaced so this becomes a no-op (the keydown handler
 * is still verified to not throw).
 */
export function clickDropdownTrigger(testId: string) {
  if (typeof document === 'undefined') return
  const trigger = document.querySelector<HTMLButtonElement>(
    `[data-testid="${testId}"] [data-testid="dropdown-menu-trigger"]`,
  )
  trigger?.click()
}

/**
 * Header shortcuts + Ctrl/Cmd+S save shortcut.
 *
 * Decision: extracted from `dsl-workbench.vue`. Both handlers are pure with
 * respect to the page (they consume injected dependencies), so this composable
 * owns the only two `useEventListener(window, 'keydown', ...)` registrations
 * the page used to carry. Behavior is identical to the inline version:
 *
 *   - Ctrl/Cmd+S: triggers `draftSave.save()` only when dirty + no modal +
 *     the event has not been default-prevented by a focused editor.
 *   - Ctrl/⌘ + Alt + <N|A|M>: opens the New panel or synthesises clicks on
 *     the Actions / Misc dropdowns. Guards editable target, repeat, mixed
 *     modifiers, and blocking modals.
 */
export function useWorkbenchShortcuts(options: UseWorkbenchShortcutsOptions) {
  const {
    draftDirty = useDraftDirty(),
    draftSave,
    isAnyModalOpen,
    openNewPanel,
    toggleObjectsSearch,
  } = options

  // Recompute on every render so the tooltip label reflects the current UA.
  const MOD = computed(() => (detectMac() ? '⌘' : 'Ctrl'))

  const newShortcut = computed(() => `${MOD.value}+${ALT}+N`)
  const actionsShortcut = computed(() => `${MOD.value}+${ALT}+A`)
  const miscShortcut = computed(() => `${MOD.value}+${ALT}+M`)
  const objectsShortcut = computed(() => `${MOD.value}+${ALT}+O`)

  function handleSaveShortcut(event: KeyboardEvent) {
    if ((event.key !== 's' && event.key !== 'S') || (!event.metaKey && !event.ctrlKey)) {
      return
    }
    // Let Monaco / other focused editors consume the shortcut first.
    if (event.defaultPrevented) return
    // Only intercept when there are unsaved server-side changes and no modal is open.
    if (!draftDirty.isDirty.value || isAnyModalOpen.value) return
    event.preventDefault()
    void draftSave.save()
  }

  function handleHeaderShortcuts(event: KeyboardEvent) {
    // Ctrl/Cmd + Alt + <N|A|M>. Avoids browser-reserved chords (Ctrl+N, Cmd+M, etc.).
    const mac = detectMac()
    const mod = mac ? event.metaKey : event.ctrlKey
    if (!mod) return
    if (!event.altKey) return
    if (event.shiftKey) return
    // Reject the mixed case where both modifiers are held — only one platform's chord is meaningful.
    if (event.metaKey && event.ctrlKey) return
    if (event.repeat) return
    // Let focused editors own the key first.
    if (event.defaultPrevented) return
    // Don't intercept typing inside form fields / contenteditable surfaces.
    if (isEditableTarget(event.target)) return
    // No header action while a blocking modal is open.
    if (isAnyModalOpen.value) return

    const key = event.key.toLowerCase()
    if (key === 'n') {
      event.preventDefault()
      openNewPanel()
      return
    }
    if (key === 'o') {
      event.preventDefault()
      toggleObjectsSearch()
      return
    }
    if (key === 'a') {
      event.preventDefault()
      clickDropdownTrigger('workbench-hotkey-actions')
      return
    }
    if (key === 'm') {
      event.preventDefault()
      clickDropdownTrigger('workbench-hotkey-misc')
    }
  }

  useEventListener(window, 'keydown', handleSaveShortcut)
  useEventListener(window, 'keydown', handleHeaderShortcuts)

  return { newShortcut, actionsShortcut, miscShortcut }
}
