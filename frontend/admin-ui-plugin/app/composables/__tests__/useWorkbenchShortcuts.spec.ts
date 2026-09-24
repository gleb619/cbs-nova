import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, h, ref } from 'vue'
import {
  clickDropdownTrigger,
  isEditableTarget,
  useWorkbenchShortcuts,
} from '../useWorkbenchShortcuts'

// Mounting a trivial wrapper gives `useEventListener` the Vue instance it
// needs to register the `keydown` listener; calling the composable outside a
// setup() leaves the listener unattached (and `computed` warns).

function buildHarness() {
  const draftDirty = { isDirty: ref(true) }
  const draftSave = { save: vi.fn() }
  const openNewPanel = vi.fn()
  const isAnyModalOpen = ref(false)
  const wrapper = mount(
    defineComponent({
      setup() {
        useWorkbenchShortcuts({
          draftDirty,
          draftSave,
          isAnyModalOpen,
          openNewPanel,
        })
        return () => h('div')
      },
    }),
    { attachTo: document.body },
  )
  return { wrapper, draftDirty, draftSave, openNewPanel, isAnyModalOpen }
}

function fire(opts: {
  key: string
  ctrlKey?: boolean
  metaKey?: boolean
  altKey?: boolean
  shiftKey?: boolean
  target?: EventTarget | null
  cancelable?: boolean
  prevented?: boolean
}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', {
    key: opts.key,
    ctrlKey: opts.ctrlKey ?? false,
    metaKey: opts.metaKey ?? false,
    altKey: opts.altKey ?? false,
    shiftKey: opts.shiftKey ?? false,
    bubbles: true,
    cancelable: opts.cancelable ?? true,
  })
  if (opts.target) {
    Object.defineProperty(event, 'target', { value: opts.target })
  }
  if (opts.prevented) event.preventDefault()
  window.dispatchEvent(event)
  return event
}

describe('useWorkbenchShortcuts — Ctrl/Cmd+S', () => {
  it('saves when dirty and no modal is open', () => {
    const { draftSave, wrapper } = buildHarness()
    const event = fire({ key: 's', ctrlKey: true })
    expect(event.defaultPrevented).toBe(true)
    expect(draftSave.save).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('saves on Cmd+S (metaKey)', () => {
    const { draftSave, wrapper } = buildHarness()
    fire({ key: 'S', metaKey: true })
    expect(draftSave.save).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('ignores the shortcut when not dirty', () => {
    const harness = buildHarness()
    harness.draftDirty.isDirty.value = false
    const event = fire({ key: 's', ctrlKey: true })
    expect(event.defaultPrevented).toBe(false)
    expect(harness.draftSave.save).not.toHaveBeenCalled()
    harness.wrapper.unmount()
  })

  it('ignores the shortcut when a modal is open', () => {
    const harness = buildHarness()
    harness.isAnyModalOpen.value = true
    fire({ key: 's', ctrlKey: true })
    expect(harness.draftSave.save).not.toHaveBeenCalled()
    harness.wrapper.unmount()
  })

  it('ignores the shortcut when the event was default-prevented by an editor', () => {
    const { draftSave, wrapper } = buildHarness()
    fire({ key: 's', ctrlKey: true, prevented: true })
    expect(draftSave.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('ignores unrelated letters', () => {
    const { draftSave, wrapper } = buildHarness()
    fire({ key: 'x', ctrlKey: true })
    expect(draftSave.save).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('useWorkbenchShortcuts — Ctrl/⌘+Alt+<N|A|M>', () => {
  it('Ctrl+Alt+N opens the new panel', () => {
    const { openNewPanel, wrapper } = buildHarness()
    const event = fire({ key: 'n', ctrlKey: true, altKey: true })
    expect(event.defaultPrevented).toBe(true)
    expect(openNewPanel).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('Ctrl+Alt without alt does not trigger', () => {
    const { openNewPanel, wrapper } = buildHarness()
    const event = fire({ key: 'n', ctrlKey: true })
    expect(event.defaultPrevented).toBe(false)
    expect(openNewPanel).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('ignores typing in input targets', () => {
    const { openNewPanel, wrapper } = buildHarness()
    const input = document.createElement('input')
    document.body.appendChild(input)
    const event = fire({ key: 'n', ctrlKey: true, altKey: true, target: input })
    expect(event.defaultPrevented).toBe(false)
    expect(openNewPanel).not.toHaveBeenCalled()
    input.remove()
    wrapper.unmount()
  })

  it('ignores when a modal is open', () => {
    const harness = buildHarness()
    harness.isAnyModalOpen.value = true
    fire({ key: 'n', ctrlKey: true, altKey: true })
    expect(harness.openNewPanel).not.toHaveBeenCalled()
    harness.wrapper.unmount()
  })

  it('Ctrl+Alt+A clicks the Actions dropdown', () => {
    const trigger = document.createElement('button')
    trigger.setAttribute('data-testid', 'dropdown-menu-trigger')
    const wrap = document.createElement('div')
    wrap.setAttribute('data-testid', 'workbench-hotkey-actions')
    wrap.appendChild(trigger)
    document.body.appendChild(wrap)
    const clickSpy = vi.spyOn(trigger, 'click')
    const { wrapper } = buildHarness()
    const event = fire({ key: 'a', ctrlKey: true, altKey: true })
    expect(event.defaultPrevented).toBe(true)
    expect(clickSpy).toHaveBeenCalledTimes(1)
    wrap.remove()
    wrapper.unmount()
  })

  it('clickDropdownTrigger is a no-op when the document has no matching trigger', () => {
    expect(() => clickDropdownTrigger('does-not-exist')).not.toThrow()
  })
})

describe('isEditableTarget', () => {
  it('recognises INPUT, TEXTAREA, SELECT, and contenteditable', () => {
    for (const tag of ['INPUT', 'TEXTAREA', 'SELECT']) {
      const el = document.createElement(tag)
      expect(isEditableTarget(el)).toBe(true)
    }
    const editable = document.createElement('div')
    editable.contentEditable = 'true'
    expect(isEditableTarget(editable)).toBe(true)
  })

  it('rejects non-editable elements', () => {
    const div = document.createElement('div')
    expect(isEditableTarget(div)).toBe(false)
    expect(isEditableTarget(null)).toBe(false)
  })
})

describe('useWorkbenchShortcuts — shortcut labels', () => {
  it('exposes the platform-aware shortcuts via the return value', () => {
    let labels: { newShortcut: string; actionsShortcut: string; miscShortcut: string } | null = null
    mount(
      defineComponent({
        setup() {
          return () => {
            labels = useWorkbenchShortcuts({
              draftDirty: { isDirty: ref(false) },
              draftSave: { save: vi.fn() },
              isAnyModalOpen: computed(() => false),
              openNewPanel: vi.fn(),
            })
            return h('div')
          }
        },
      }),
      { attachTo: document.body },
    )
    const expectedMod = /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent || '')
      ? '⌘'
      : 'Ctrl'
    expect(labels?.newShortcut.value).toBe(`${expectedMod}+Alt+N`)
    expect(labels?.actionsShortcut.value).toBe(`${expectedMod}+Alt+A`)
    expect(labels?.miscShortcut.value).toBe(`${expectedMod}+Alt+M`)
  })
})
