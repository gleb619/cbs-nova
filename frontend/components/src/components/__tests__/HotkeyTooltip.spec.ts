import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import HotkeyTooltip, { type TooltipLocation } from '../HotkeyTooltip.vue'

function dispatchKeydown(key: string, repeat = false) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, repeat }))
}

function dispatchBlur() {
  window.dispatchEvent(new Event('blur'))
}

function mountTooltip(
  slot = () => h('button', 'Save'),
  keys = 'Ctrl+S',
  location?: TooltipLocation,
) {
  return mount(HotkeyTooltip, {
    props: { keys, location },
    slots: { default: slot },
  })
}

describe('HotkeyTooltip', () => {
  afterEach(() => {
    dispatchBlur()
  })

  it('always renders default slot content', () => {
    const wrapper = mountTooltip()
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.text()).toContain('Save')
  })

  it('does not render tooltip when overlay is hidden (default state)', () => {
    const wrapper = mountTooltip()
    expect(wrapper.find('[data-testid="hotkey-tooltip"]').exists()).toBe(false)
  })

  it('renders tooltip with keys after Alt keydown', async () => {
    const wrapper = mountTooltip()
    dispatchKeydown('Alt')
    await nextTick()

    const tooltip = wrapper.find('[data-testid="hotkey-tooltip"]')
    expect(tooltip.exists()).toBe(true)
    expect(tooltip.text()).toBe('Ctrl+S')
  })

  it('reflects keys prop verbatim', async () => {
    const wrapper = mountTooltip(() => h('span', 'Trigger'), 'Shift+Tab')
    dispatchKeydown('Alt')
    await nextTick()

    expect(wrapper.find('[data-testid="hotkey-tooltip"]').text()).toBe('Shift+Tab')
  })

  it('positions tooltip at the bottom by default', async () => {
    const wrapper = mountTooltip()
    dispatchKeydown('Alt')
    await nextTick()

    const tooltip = wrapper.find('[data-testid="hotkey-tooltip"]')
    const classes = tooltip.classes().join(' ')
    expect(classes).toContain('top-full')
    expect(classes).not.toContain('bottom-full')
  })

  it('positions tooltip at the top when location is top', async () => {
    const wrapper = mountTooltip(() => h('button', 'Save'), 'Alt+O', 'top')
    dispatchKeydown('Alt')
    await nextTick()

    const tooltip = wrapper.find('[data-testid="hotkey-tooltip"]')
    const classes = tooltip.classes().join(' ')
    expect(classes).toContain('bottom-full')
    expect(classes).not.toContain('top-full')
  })
})
