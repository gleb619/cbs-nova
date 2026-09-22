import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { useHotkeyOverlay } from '../useHotkeyOverlay'

function dispatchKeydown(key: string, repeat = false) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, repeat }))
}

function dispatchKeyup(key: string) {
  window.dispatchEvent(new KeyboardEvent('keyup', { key }))
}

function dispatchBlur() {
  window.dispatchEvent(new Event('blur'))
}

describe('useHotkeyOverlay', () => {
  afterEach(() => {
    dispatchBlur()
  })

  it('starts with visible false', () => {
    const { visible } = useHotkeyOverlay()
    expect(visible.value).toBe(false)
  })

  it('shows overlay on Alt keydown and hides on Alt keyup', async () => {
    const { visible } = useHotkeyOverlay()
    dispatchKeydown('Alt')
    await nextTick()
    expect(visible.value).toBe(true)
    dispatchKeyup('Alt')
    await nextTick()
    expect(visible.value).toBe(false)
  })

  it('does not show overlay on repeated Alt keydown', async () => {
    const { visible } = useHotkeyOverlay()
    dispatchKeydown('Alt', true)
    await nextTick()
    expect(visible.value).toBe(false)
  })

  it('ignores non-Alt keys', async () => {
    const { visible } = useHotkeyOverlay()
    dispatchKeydown('a')
    await nextTick()
    expect(visible.value).toBe(false)
  })

  it('hides overlay on window blur even when Alt is held', async () => {
    const { visible } = useHotkeyOverlay()
    dispatchKeydown('Alt')
    await nextTick()
    expect(visible.value).toBe(true)
    dispatchBlur()
    await nextTick()
    expect(visible.value).toBe(false)
  })

  it('returns a read-only visible ref', async () => {
    const { visible } = useHotkeyOverlay()
    dispatchKeydown('Alt')
    await nextTick()
    expect(visible.value).toBe(true)

    // @ts-expect-error - visible is exposed as readonly
    visible.value = false
    expect(visible.value).toBe(true)
  })
})
