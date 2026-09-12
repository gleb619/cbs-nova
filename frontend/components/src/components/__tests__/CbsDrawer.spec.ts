import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import CbsDrawer from '../CbsDrawer.vue'

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
})
