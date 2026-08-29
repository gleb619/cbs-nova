import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import DropdownMenu from '../DropdownMenu.vue'
import type { DropdownMenuItem } from '../DropdownMenu.vue'

const items: DropdownMenuItem[] = [
  { label: 'Refresh', value: 'refresh' },
  { label: 'Validate', value: 'validate', disabled: true },
  { label: 'Save', value: 'save' },
]

function mountMenu(extraProps: Partial<{ label: string; items: DropdownMenuItem[]; align: 'left' | 'right' }> = {}) {
  return mount(DropdownMenu, {
    props: { label: 'Actions', items, ...extraProps },
    attachTo: document.body,
  })
}

describe('DropdownMenu', () => {
  it('renders the trigger button with the supplied label', () => {
    const wrapper = mountMenu()
    expect(wrapper.find('button[aria-haspopup="true"]').text()).toContain('Actions')
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('opens on trigger click and emits select on item click', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    expect(trigger.attributes('aria-expanded')).toBe('false')

    await trigger.trigger('click')
    await nextTick()

    expect(trigger.attributes('aria-expanded')).toBe('true')
    const menu = wrapper.find('[role="menu"]')
    expect(menu.exists()).toBe(true)

    const buttons = wrapper.findAll('[role="menuitem"]')
    expect(buttons).toHaveLength(items.length)
    expect(buttons[1]?.attributes('disabled')).toBeDefined()

    await buttons[0]!.trigger('click')
    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(items[0])
    expect(wrapper.find('[role="menu"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('closes on Escape and returns focus to the trigger', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')
    await nextTick()

    const menu = wrapper.find('[role="menu"]')
    await menu.trigger('keydown', { key: 'Escape' })

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)

    wrapper.unmount()
  })

  it('navigates items with ArrowDown and ArrowUp, skipping disabled', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')
    await nextTick()

    const menu = wrapper.find('[role="menu"]')
    const buttons = wrapper.findAll('[role="menuitem"]')

    await menu.trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement).toBe(buttons[2]!.element) // skips disabled Validate

    await menu.trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement).toBe(buttons[0]!.element)

    wrapper.unmount()
  })

  it('selects the active item with Enter', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')
    await nextTick()

    const menu = wrapper.find('[role="menu"]')
    await menu.trigger('keydown', { key: 'ArrowDown' }) // moves past disabled Validate → Save
    const buttons = wrapper.findAll('[role="menuitem"]')
    await buttons[2]!.trigger('keydown', { key: 'Enter' })

    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual(items[2])
    wrapper.unmount()
  })

  it('opens and focuses the first item when ArrowDown is pressed on the closed trigger', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')

    await trigger.trigger('keydown.down')
    await nextTick()

    const buttons = wrapper.findAll('[role="menuitem"]')
    expect(buttons.length).toBeGreaterThan(0)
    expect(document.activeElement).toBe(buttons[0]!.element)

    wrapper.unmount()
  })

  it('closes when focus leaves the menu container', async () => {
    const wrapper = mountMenu()
    const trigger = wrapper.find('button[aria-haspopup="true"]')
    await trigger.trigger('click')
    await nextTick()
    expect(wrapper.find('[role="menu"]').exists()).toBe(true)

    const menu = wrapper.find('[role="menu"]')
    await menu.trigger('focusout', { relatedTarget: document.body })

    expect(wrapper.find('[role="menu"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('applies primary variant classes to items flagged as primary', async () => {
    const wrapper = mountMenu({
      items: [{ label: 'Publish', value: 'publish', variant: 'primary' }],
    })
    await wrapper.find('button[aria-haspopup="true"]').trigger('click')
    await nextTick()

    const item = wrapper.find('[role="menuitem"]')
    expect(item.classes().join(' ')).toContain('text-blue-700')

    wrapper.unmount()
  })

  it('aligns the menu panel via the align prop', async () => {
    const left = mountMenu({ align: 'left' })
    await left.find('button[aria-haspopup="true"]').trigger('click')
    await nextTick()
    expect(left.find('[role="menu"]').classes()).toContain('left-0')
    expect(left.find('[role="menu"]').classes()).not.toContain('right-0')
    left.unmount()

    const right = mountMenu({ align: 'right' })
    await right.find('button[aria-haspopup="true"]').trigger('click')
    await nextTick()
    expect(right.find('[role="menu"]').classes()).toContain('right-0')
    right.unmount()
  })
})
