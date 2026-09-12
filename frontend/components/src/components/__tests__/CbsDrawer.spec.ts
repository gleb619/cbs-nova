import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import CbsDrawer from '../CbsDrawer.vue'

const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))

// The drawer teleports its overlay and aside into <body>; stub Teleport so they
// render in place and stay queryable through the wrapper itself.
const mountDrawer = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) =>
  mount(CbsDrawer, {
    props: { title: 'Drafts', closeLabel: 'Close drawer', ...props },
    slots: slots as never,
    global: { stubs: { teleport: true } },
  })

describe('CbsDrawer', () => {
  it('renders neither overlay nor aside while open is false', () => {
    const wrapper = mountDrawer({ open: false })

    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(false)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })

  it('renders the overlay and the aside while open is true', () => {
    const wrapper = mountDrawer({ open: true })

    expect(wrapper.find('[aria-hidden="true"]').exists()).toBe(true)
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
  })

  it('emits update:open false when the overlay is clicked', async () => {
    const wrapper = mountDrawer({ open: true })

    await wrapper.get('[aria-hidden="true"]').trigger('click')

    expect(wrapper.emitted('update:open')!.at(-1)).toEqual([false])
  })

  it('emits update:open false when the close button is clicked', async () => {
    const wrapper = mountDrawer({ open: true })

    await wrapper.get('[data-testid="drawer-close-button"]').trigger('click')

    expect(wrapper.emitted('update:open')!.at(-1)).toEqual([false])
  })

  it('falls back to the title for the aria-label when ariaLabel is omitted', () => {
    const wrapper = mountDrawer({ open: true })

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('Drafts')
  })

  it('uses ariaLabel over the title when provided', () => {
    const wrapper = mountDrawer({ open: true, ariaLabel: 'Saved drafts dialog' })

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('Saved drafts dialog')
  })

  it('exposes the custom testId on the aside', () => {
    const wrapper = mountDrawer({ open: true, testId: 'dsl-saved-drafts-drawer' })

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
  })

  it('applies the widthClass to the aside', () => {
    const wrapper = mountDrawer({ open: true, widthClass: 'w-[32rem]' })

    expect(wrapper.get('[role="dialog"]').classes()).toContain('w-[32rem]')
  })

  it('marks the aside as a modal dialog', () => {
    const wrapper = mountDrawer({ open: true })

    const aside = wrapper.get('[role="dialog"]')
    expect(aside.attributes('role')).toBe('dialog')
    expect(aside.attributes('aria-modal')).toBe('true')
  })

  it('labels the close button with closeLabel', () => {
    const wrapper = mountDrawer({ open: true, closeLabel: 'Dismiss drafts' })

    expect(wrapper.get('[data-testid="drawer-close-button"]').attributes('aria-label')).toBe(
      'Dismiss drafts',
    )
  })

  it('renders the title in the header', () => {
    const wrapper = mountDrawer({ open: true, title: 'Saved drafts' })

    expect(wrapper.get('h3').text()).toBe('Saved drafts')
  })

  it('renders the default slot inside the aside', () => {
    const wrapper = mountDrawer({ open: true }, { default: () => h('p', 'drawer body') })

    const aside = wrapper.get('[role="dialog"]')
    expect(aside.find('p').text()).toBe('drawer body')
  })

  describe('useModalDialog integration', () => {
    let wrapper: ReturnType<typeof mountDrawer> | null = null

    beforeEach(() => {
      document.body.innerHTML = ''
    })

    afterEach(() => {
      wrapper?.unmount()
      wrapper = null
      document.body.innerHTML = ''
    })

    const mountAttachedDrawer = (
      props: Record<string, unknown> = {},
      slots: Record<string, unknown> = {},
    ) => {
      wrapper = mount(CbsDrawer, {
        props: { title: 'Drafts', closeLabel: 'Close drawer', ...props },
        slots: slots as never,
        global: { stubs: { teleport: true } },
        attachTo: document.body,
      })
      return wrapper
    }

    const twoButtonsSlot = {
      default: () => [
        h('button', { type: 'button', 'data-testid': 'slot-first' }, 'First'),
        h('button', { type: 'button', 'data-testid': 'slot-last' }, 'Last'),
      ],
    }

    it('emits update:open false when Escape is pressed inside the drawer', async () => {
      const w = mountAttachedDrawer({ open: true })
      await nextTick()
      await flushPromises()

      w.get('[role="dialog"]').element.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
      )
      await nextTick()

      expect(w.emitted('update:open')!.at(-1)).toEqual([false])
    })

    it('moves focus to the first tabbable element when opened', async () => {
      const w = mountAttachedDrawer({ open: true }, twoButtonsSlot)
      await nextTick()
      await flushPromises()

      expect(document.activeElement).toBe(w.get('[data-testid="drawer-close-button"]').element)
    })

    it('traps focus cycling forward with Tab', async () => {
      const w = mountAttachedDrawer({ open: true }, twoButtonsSlot)
      await nextTick()
      await flushPromises()

      const first = w.get('[data-testid="drawer-close-button"]').element as HTMLElement
      const last = w.get('[data-testid="slot-last"]').element as HTMLElement

      last.focus()
      document.activeElement?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }),
      )
      await nextTick()

      expect(document.activeElement).toBe(first)
    })

    it('traps focus cycling backward with Shift+Tab', async () => {
      const w = mountAttachedDrawer({ open: true }, twoButtonsSlot)
      await nextTick()
      await flushPromises()

      const first = w.get('[data-testid="drawer-close-button"]').element as HTMLElement
      const last = w.get('[data-testid="slot-last"]').element as HTMLElement

      first.focus()
      document.activeElement?.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }),
      )
      await nextTick()

      expect(document.activeElement).toBe(last)
    })

    it('makes background siblings inert while open and restores them on close', async () => {
      const sibling = document.createElement('div')
      document.body.appendChild(sibling)

      const w = mountAttachedDrawer({ open: true })
      await nextTick()
      await flushPromises()

      expect(sibling.inert).toBe(true)
      expect(sibling.getAttribute('aria-hidden')).toBe('true')

      await w.setProps({ open: false })
      await nextTick()

      expect(sibling.inert).toBe(false)
      expect(sibling.hasAttribute('aria-hidden')).toBe(false)
    })

    it('returns focus to the previously focused element when closed', async () => {
      const trigger = document.createElement('button')
      document.body.appendChild(trigger)
      trigger.focus()

      const w = mountAttachedDrawer({ open: true })
      await nextTick()
      await flushPromises()
      expect(document.activeElement).not.toBe(trigger)

      await w.setProps({ open: false })
      await nextTick()

      expect(document.activeElement).toBe(trigger)
    })
  })
})
