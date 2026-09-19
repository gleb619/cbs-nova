import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { resetNotificationsState, useNotifications } from '../useNotifications'
import { useToast } from '../useToast'

function mountConsumer(): ReturnType<typeof useToast> {
  let captured!: ReturnType<typeof useToast>
  const Comp = defineComponent({
    setup() {
      captured = useToast()
      return () => h('div')
    },
  })
  mount(Comp)
  return captured
}

describe('useToast', () => {
  beforeEach(() => {
    resetNotificationsState()
  })
  afterEach(() => {
    resetNotificationsState()
    vi.useRealTimers()
  })

  it('push returns a notification with toast: true', () => {
    const t = mountConsumer()
    const note = t.push('hello', 'success', 500)
    expect(note.toast).toBe(true)
    expect(note.expiresAt).toBeDefined()
    // Same record visible in both surfaces while alive.
    expect(t.activeToasts.value).toHaveLength(1)
    expect(t.notifications.value).toHaveLength(1)
  })

  it('success/info/warning helpers produce ephemeral toasts; error produces a sticky toast', async () => {
    vi.useFakeTimers()
    const t = mountConsumer()
    t.success('s')
    t.info('i')
    t.warning('w')
    t.error('e')
    await vi.runAllTimersAsync()
    // Three ephemeral ones auto-removed; the sticky error remains.
    expect(t.activeToasts.value.map((x) => x.message)).toEqual(['e'])
    expect(t.notifications.value.every((n) => n.toast)).toBe(true)
  })

  it('error helper defaults to sticky (duration 0) so messages cannot vanish silently', async () => {
    vi.useFakeTimers()
    const t = mountConsumer()
    t.error('boom')
    await vi.runAllTimersAsync()
    expect(t.activeToasts.value).toHaveLength(1)
  })

  it('dismiss removes the notification from every surface', async () => {
    vi.useFakeTimers()
    const t = mountConsumer()
    const note = t.info('x', 1000)
    t.dismiss(note.id)
    await vi.runAllTimersAsync()
    expect(t.notifications.value).toEqual([])
  })

  it('shares the same notification queue across consumers in one app', () => {
    const seen: ReturnType<typeof useToast>[] = []
    const Inner = defineComponent({
      setup() {
        seen.push(useToast())
        return () => h('div')
      },
    })
    const Outer = defineComponent({
      setup() {
        seen.push(useToast())
        return () => h('div', [h(Inner)])
      },
    })
    mount(Outer)
    seen[1].success('shared')
    expect(seen[0].activeToasts.value).toHaveLength(1)
    const n = useNotifications()
    expect(n.notifications.value).toHaveLength(1)
  })
})
