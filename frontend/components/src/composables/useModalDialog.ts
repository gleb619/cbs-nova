import { nextTick, onUnmounted, type Ref, ref } from 'vue'

export interface UseModalDialogOptions {
  /** Called when the user requests close via Escape or programmatic close(). */
  onClose: () => void
  /** Whether Escape closes the dialog. Default: true. */
  closeOnEsc?: boolean
  /** Optional element to receive initial focus. Defaults to the dialog root. */
  initialFocus?: Ref<HTMLElement | null>
  /** Whether to restore focus to the previously focused element on close. Default: true. */
  returnFocus?: boolean
}

interface InertState {
  element: HTMLElement
  inert: boolean
  ariaHidden: string | null
}

const TABBABLE_SELECTOR = [
  'button:not([disabled])',
  'a[href]',
  'area[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  'object',
  'embed',
  '[contenteditable]:not([contenteditable="false"])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

/**
 * Accessible modal dialog behavior for a single modal root element.
 *
 * Features:
 * - Focus is moved into the dialog when it opens.
 * - Tab / Shift+Tab cycle focus within the dialog (focus trap).
 * - Escape closes the dialog (configurable).
 * - Focus returns to the previously focused element on close.
 * - Background content is made inert via `inert` + `aria-hidden` while open.
 *
 * The component is responsible for rendering `role="dialog"`, `aria-modal="true"`,
 * `aria-labelledby`, and any visibility toggling.
 */
export function useModalDialog(dialogRef: Ref<HTMLElement | null>, options: UseModalDialogOptions) {
  const { onClose, closeOnEsc = true, initialFocus, returnFocus = true } = options

  const isOpen = ref(false)
  let previousActiveElement: Element | null = null
  let inertStates: InertState[] = []

  function getTabbable(): HTMLElement[] {
    const dialog = dialogRef.value
    if (!dialog) return []
    return Array.from(dialog.querySelectorAll(TABBABLE_SELECTOR)).filter(
      (el) => el instanceof HTMLElement,
    ) as HTMLElement[]
  }

  function trapFocus(event: KeyboardEvent) {
    if (event.key !== 'Tab') return
    const dialog = dialogRef.value
    if (!dialog) return

    const tabbable = getTabbable()
    if (tabbable.length === 0) {
      event.preventDefault()
      return
    }

    const current = document.activeElement as HTMLElement | null
    const currentIndex = tabbable.indexOf(current as HTMLElement)

    if (event.shiftKey) {
      const nextIndex = currentIndex <= 0 ? tabbable.length - 1 : currentIndex - 1
      event.preventDefault()
      tabbable[nextIndex].focus()
    } else {
      const nextIndex =
        currentIndex < 0 || currentIndex >= tabbable.length - 1 ? 0 : currentIndex + 1
      event.preventDefault()
      tabbable[nextIndex].focus()
    }
  }

  function onKeydown(event: KeyboardEvent) {
    if (closeOnEsc && event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      onClose()
      return
    }
    trapFocus(event)
  }

  function makeBackgroundInert() {
    const dialog = dialogRef.value
    if (!dialog || !document.body.contains(dialog)) return

    const children = Array.from(document.body.children)
    inertStates = []
    for (const child of children) {
      // Skip the dialog itself, and any ancestor that merely contains it (e.g. a test
      // mount wrapper when Teleport is stubbed and the dialog isn't a direct body child).
      if (child === dialog || child.contains(dialog)) continue
      if (child.tagName === 'SCRIPT' || child.tagName === 'STYLE') continue
      if (!(child instanceof HTMLElement)) continue

      inertStates.push({
        element: child,
        inert: child.inert,
        ariaHidden: child.getAttribute('aria-hidden'),
      })
      child.inert = true
      child.setAttribute('aria-hidden', 'true')
    }
  }

  function restoreBackground() {
    for (const state of inertStates) {
      state.element.inert = state.inert
      if (state.ariaHidden === null) {
        state.element.removeAttribute('aria-hidden')
      } else {
        state.element.setAttribute('aria-hidden', state.ariaHidden)
      }
    }
    inertStates = []
  }

  async function open() {
    if (isOpen.value) return
    isOpen.value = true

    previousActiveElement = document.activeElement
    await nextTick()

    const dialog = dialogRef.value
    if (!dialog) return

    if (!dialog.hasAttribute('tabindex')) {
      dialog.setAttribute('tabindex', '-1')
    }
    dialog.addEventListener('keydown', onKeydown)

    const target = initialFocus?.value ?? getTabbable()[0] ?? dialog
    target.focus()

    makeBackgroundInert()
  }

  function close() {
    if (!isOpen.value) return
    isOpen.value = false

    dialogRef.value?.removeEventListener('keydown', onKeydown)
    restoreBackground()

    if (returnFocus && previousActiveElement instanceof HTMLElement) {
      previousActiveElement.focus()
    }
    previousActiveElement = null
  }

  onUnmounted(() => {
    close()
  })

  return {
    open,
    close,
    focusDialog: () => dialogRef.value?.focus(),
  }
}
