import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { h, nextTick } from 'vue'
import HotkeyTooltip from '../HotkeyTooltip.vue'

function dispatchKeydown(key: string) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key }))
}

function dispatchBlur() {
  window.dispatchEvent(new Event('blur'))
}

describe('HotkeyTooltip', () => {
  afterEach(() => {
    dispatchBlur()
  })

  it('always renders default slot content', () => {
    const wrapper = mount(HotkeyTooltip, {
      props: { keys: 'Ctrl+S' },
      slots: {
        default: () => h('button', 'Save'),
      },
    })

    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.text()).toContain('Save')
  })

  it('does not render tooltip when overlay is not visible', () => {
    const wrapper = mount(HotkeyTooltip, {
      props: { keys: 'Ctrl+S' },
      slots: {
        default: () => h('button', 'Save'),
      },
    })

    expect(wrapper.find('[data-testid="hotkey-tooltip"]').exists()).toBe(false)
  })

  it('renders tooltip with keys after Alt keydown', async () => {
    const wrapper = mount(HotkeyTooltip, {
      props: { keys: 'Ctrl+S' },
      slots: {
        default: () => h('button', 'Save'),
      },
    })

    dispatchKeydown('Alt')
    await nextTick()

    const tooltip = wrapper.find('[data-testid="hotkey-tooltip"]')
    expect(tooltip.exists()).toBe(true)
    expect(tooltip.text()).toBe('Ctrl+S')
  })

  it('reflects keys prop verbatim', async () => {
    const wrapper = mount(HotkeyTooltip, {
      props: { keys: 'Shift+Tab' },
      slots: {
        default: () => h('span', 'Trigger'),
      },
    })

    dispatchKeydown('Alt')
    await nextTick()

    expect(wrapper.find('[data-testid="hotkey-tooltip"]').text()).toBe('Shift+Tab')
  })
})
