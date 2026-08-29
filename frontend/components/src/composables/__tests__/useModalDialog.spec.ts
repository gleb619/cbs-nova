import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, ref, watch } from 'vue'

import { useModalDialog } from '../useModalDialog'

function mountDialog(options: { closeOnEsc?: boolean; returnFocus?: boolean } = {}) {
  const onClose = vi.fn()
  const Comp = defineComponent({
    props: {
      show: { type: Boolean, default: false },
    },
    setup(props) {
      const dialogRef = ref<HTMLElement | null>(null)
      const { open, close } = useModalDialog(dialogRef, {
        onClose,
        closeOnEsc: options.closeOnEsc,
        returnFocus: options.returnFocus,
      })

      watch(
        () => props.show,
        (visible) => {
          if (visible) open()
          else close()
        },
      )

      return () => {
        if (!props.show) return null
        return h(
          'div',
          {
            ref: dialogRef,
            role: 'dialog',
            'aria-modal': 'true',
            'data-testid': 'dialog',
            tabindex: '-1',
          },
          [
            h('button', { type: 'button', 'data-testid': 'first-button' }, 'First'),
            h('input', { type: 'text', 'data-testid': 'text-input' }),
            h('button', { type: 'button', 'data-testid': 'last-button' }, 'Last'),
          ],
        )
      }
    },
  })

  const trigger = document.createElement('button')
  trigger.setAttribute('data-testid', 'trigger')
  document.body.appendChild(trigger)
  trigger.focus()

  const wrapper = mount(Comp, {
    props: { show: false },
    attachTo: document.body,
  })

  return { wrapper, onClose, trigger }
}

describe('useModalDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('moves focus to the first tabbable element when opened', async () => {
    const { wrapper } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()

    const firstButton = wrapper.find('[data-testid="first-button"]').element
    expect(document.activeElement).toBe(firstButton)
  })

  it('traps focus cycling forward with Tab', async () => {
    const { wrapper } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()

    const first = wrapper.find('[data-testid="first-button"]').element as HTMLElement
    const last = wrapper.find('[data-testid="last-button"]').element as HTMLElement

    last.focus()
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }),
    )
    await nextTick()

    expect(document.activeElement).toBe(first)
  })

  it('traps focus cycling backward with Shift+Tab', async () => {
    const { wrapper } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()

    const first = wrapper.find('[data-testid="first-button"]').element as HTMLElement
    const last = wrapper.find('[data-testid="last-button"]').element as HTMLElement

    first.focus()
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }),
    )
    await nextTick()

    expect(document.activeElement).toBe(last)
  })

  it('calls onClose when Escape is pressed', async () => {
    const { wrapper, onClose } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()

    const dialog = wrapper.find('[data-testid="dialog"]').element
    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not close on Escape when closeOnEsc is false', async () => {
    const { wrapper, onClose } = mountDialog({ closeOnEsc: false })

    await wrapper.setProps({ show: true })
    await nextTick()

    const dialog = wrapper.find('[data-testid="dialog"]').element
    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()

    expect(onClose).not.toHaveBeenCalled()
  })

  it('returns focus to the trigger element when closed', async () => {
    const { wrapper, trigger } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()
    expect(document.activeElement).not.toBe(trigger)

    await wrapper.setProps({ show: false })
    await nextTick()

    expect(document.activeElement).toBe(trigger)
  })

  it('does not return focus when returnFocus is false', async () => {
    const { wrapper, trigger } = mountDialog({ returnFocus: false })

    await wrapper.setProps({ show: true })
    await nextTick()

    await wrapper.setProps({ show: false })
    await nextTick()

    expect(document.activeElement).not.toBe(trigger)
  })

  it('makes background siblings inert when the dialog is a body child', async () => {
    document.body.innerHTML = ''
    const sibling = document.createElement('div')
    sibling.setAttribute('data-testid', 'sibling')
    document.body.appendChild(sibling)

    const { wrapper } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()

    expect(sibling.inert).toBe(true)
    expect(sibling.getAttribute('aria-hidden')).toBe('true')

    await wrapper.setProps({ show: false })
    await nextTick()

    expect(sibling.inert).toBe(false)
    expect(sibling.hasAttribute('aria-hidden')).toBe(false)
  })

  it('restores previous aria-hidden value on background siblings when closing', async () => {
    document.body.innerHTML = ''
    const sibling = document.createElement('div')
    sibling.setAttribute('aria-hidden', 'false')
    document.body.appendChild(sibling)

    const { wrapper } = mountDialog()

    await wrapper.setProps({ show: true })
    await nextTick()
    expect(sibling.getAttribute('aria-hidden')).toBe('true')

    await wrapper.setProps({ show: false })
    await nextTick()

    expect(sibling.getAttribute('aria-hidden')).toBe('false')
  })
})
