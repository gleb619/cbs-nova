import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DropdownMenu from '../dropdownMenu/DropdownMenu.vue'
import type { DropdownMenuItem } from '../dropdownMenu/DropdownMenu.vue'

const items: DropdownMenuItem[] = [
  { label: 'Alpha', value: 'a' },
  { label: 'Beta', value: 'b' },
  { label: 'Gamma', value: 'c' },
]

// The component moves real DOM focus between trigger and items; mount attached
// to the document so document.activeElement assertions work in happy-dom.
let wrapper: ReturnType<typeof mountMenu> | null = null

const mountMenu = (props: Record<string, unknown> = {}) => {
  wrapper = mount(DropdownMenu, {
    props: { label: 'Actions', items, ...props },
    attachTo: document.body,
  })
  return wrapper
}

const triggerOf = (w: ReturnType<typeof mountMenu>) =>
  w.get('[data-testid="dropdown-menu-trigger"]')

const menuOf = (w: ReturnType<typeof mountMenu>) => w.get('[role="menu"]')

const itemOf = (w: ReturnType<typeof mountMenu>, value: string) =>
  w.get(`[data-testid="dropdown-menu-item-${value}"]`)

const activeElement = () => document.activeElement as HTMLElement

describe('DropdownMenu', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('is closed by default: no menu rendered, trigger aria-expanded false', () => {
    const w = mountMenu()

    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(triggerOf(w).attributes('aria-expanded')).toBe('false')
  })

  it('opens on trigger click and closes on a second click', async () => {
    const w = mountMenu()

    await triggerOf(w).trigger('click')
    expect(w.find('[role="menu"]').exists()).toBe(true)
    expect(triggerOf(w).attributes('aria-expanded')).toBe('true')

    await triggerOf(w).trigger('click')
    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(triggerOf(w).attributes('aria-expanded')).toBe('false')
  })

  it('always sets aria-haspopup and aria-controls from the menuId prop', () => {
    const w = mountMenu({ menuId: 'actions-menu' })

    const trigger = triggerOf(w)
    expect(trigger.attributes('aria-haspopup')).toBe('true')
    expect(trigger.attributes('aria-controls')).toBe('actions-menu')
  })

  it('opens and focuses the first item on ArrowDown on the trigger', async () => {
    const w = mountMenu()

    await triggerOf(w).trigger('keydown', { key: 'ArrowDown' })

    expect(w.find('[role="menu"]').exists()).toBe(true)
    expect(activeElement()).toBe(itemOf(w, 'a').element)
  })

  it('moves focus across items with ArrowDown and ArrowUp and wraps at both ends', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'a').element)

    await menuOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'b').element)

    await menuOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'c').element)

    // wrap forward: Down from last lands on first
    await menuOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'a').element)

    // wrap backward: Up from first lands on last
    await menuOf(w).trigger('keydown', { key: 'ArrowUp' })
    expect(activeElement()).toBe(itemOf(w, 'c').element)
  })

  it('skips a disabled item during navigation', async () => {
    const w = mountMenu({
      items: [
        { label: 'Alpha', value: 'a' },
        { label: 'Beta', value: 'b', disabled: true },
        { label: 'Gamma', value: 'c' },
      ],
    })

    await triggerOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'a').element)

    await menuOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'c').element)
  })

  it.each(['click', 'enter', 'space'] as const)(
    'emits select exactly once with the item and closes on %s',
    async (interaction) => {
      const w = mountMenu()
      await triggerOf(w).trigger('click')

      const beta = itemOf(w, 'b')
      if (interaction === 'click') {
        await beta.trigger('click')
      } else if (interaction === 'enter') {
        await beta.trigger('keydown', { key: 'Enter' })
      } else {
        await beta.trigger('keydown', { key: ' ' })
      }

      expect(w.emitted('select')).toEqual([[items[1]]])
      expect(w.find('[role="menu"]').exists()).toBe(false)
    },
  )

  it.each(['click', 'enter'] as const)(
    'emits no select and keeps the menu open when a disabled item gets %s',
    async (interaction) => {
      const w = mountMenu({
        items: [
          { label: 'Alpha', value: 'a' },
          { label: 'Beta', value: 'b', disabled: true },
          { label: 'Gamma', value: 'c' },
        ],
      })
      await triggerOf(w).trigger('click')

      const beta = itemOf(w, 'b')
      if (interaction === 'click') {
        await beta.trigger('click')
      } else {
        await beta.trigger('keydown', { key: 'Enter' })
      }

      expect(w.emitted('select')).toBeUndefined()
      expect(w.find('[role="menu"]').exists()).toBe(true)
    },
  )

  it('closes and returns focus to the trigger on Escape on the trigger', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('keydown', { key: 'ArrowDown' })
    expect(activeElement()).toBe(itemOf(w, 'a').element)

    await triggerOf(w).trigger('keydown', { key: 'Escape' })

    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(activeElement()).toBe(triggerOf(w).element)
  })

  it('closes and returns focus to the trigger on Escape while focus is in the menu', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('keydown', { key: 'ArrowDown' })

    await menuOf(w).trigger('keydown', { key: 'Escape' })

    expect(w.find('[role="menu"]').exists()).toBe(false)
    expect(activeElement()).toBe(triggerOf(w).element)
  })

  it('aligns the menu with left-0 when align is left', async () => {
    const w = mountMenu({ align: 'left' })
    await triggerOf(w).trigger('click')

    expect(menuOf(w).classes()).toContain('left-0')
    expect(menuOf(w).classes()).not.toContain('right-0')
  })

  it('aligns the menu with right-0 by default and when align is right', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('click')
    expect(menuOf(w).classes()).toContain('right-0')

    await w.setProps({ align: 'right' })
    expect(menuOf(w).classes()).toContain('right-0')
    expect(menuOf(w).classes()).not.toContain('left-0')
  })

  it('closes the menu via the exposed close() method', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('click')
    expect(w.find('[role="menu"]').exists()).toBe(true)

    w.vm.close()
    await nextTick()

    expect(w.find('[role="menu"]').exists()).toBe(false)
  })

  it('closes on focusout whose relatedTarget is outside the component', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('click')

    const outside = document.createElement('button')
    document.body.appendChild(outside)

    menuOf(w).element.dispatchEvent(
      new FocusEvent('focusout', { bubbles: true, relatedTarget: outside }),
    )
    await nextTick()

    expect(w.find('[role="menu"]').exists()).toBe(false)
  })

  it('stays open on focusout whose relatedTarget is the trigger', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('click')

    menuOf(w).element.dispatchEvent(
      new FocusEvent('focusout', {
        bubbles: true,
        relatedTarget: triggerOf(w).element,
      }),
    )
    await nextTick()

    expect(w.find('[role="menu"]').exists()).toBe(true)
  })

  it('stays open on focusout whose relatedTarget is another menu item', async () => {
    const w = mountMenu()
    await triggerOf(w).trigger('click')

    menuOf(w).element.dispatchEvent(
      new FocusEvent('focusout', {
        bubbles: true,
        relatedTarget: itemOf(w, 'c').element,
      }),
    )
    await nextTick()

    expect(w.find('[role="menu"]').exists()).toBe(true)
  })
})
