import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { resetNotificationsState, useNotifications } from '../useNotifications'

function mountConsumer() {
  const seen: ReturnType<typeof useNotifications>[] = []
  const Comp = defineComponent({
    setup() {
      seen.push(useNotifications())
      return () => h('div')
    },
  })
  mount(Comp)
  return seen[0] as ReturnType<typeof useNotifications>
}

describe('useNotifications', () => {
  beforeEach(() => {
    resetNotificationsState()
  })

  afterEach(() => {
    resetNotificationsState()
    vi.useRealTimers()
  })

  it('starts empty', () => {
    const n = mountConsumer()
    expect(n.notifications.value).toEqual([])
    expect(n.activeToasts.value).toEqual([])
    expect(n.history.value).toEqual([])
    expect(n.unreadCount.value).toBe(0)
  })

  it('defaults to a persistent (history-only) notification', () => {
    const n = mountConsumer()
    const note = n.push('saved', 'success')
    expect(note.toast).toBe(false)
    expect(note.expiresAt).toBeUndefined()
    expect(n.history.value.map((x) => x.id)).toEqual([note.id])
    expect(n.activeToasts.value).toEqual([])
  })

  it('opt-in toast surface: { toast: true } routes the record into activeToasts', () => {
    const n = mountConsumer()
    const toast = n.push('hi', 'info', { toast: true, duration: 4000 })
    expect(n.activeToasts.value.map((x) => x.id)).toEqual([toast.id])
    // Same record, same list — history includes it too while it lives.
    expect(n.notifications.value).toHaveLength(1)
  })

  it('sticky toast: toast + duration 0 → stays in activeToasts until dismissed', async () => {
    vi.useFakeTimers()
    const n = mountConsumer()
    const sticky = n.push('boom', 'error', { toast: true, duration: 0 })
    expect(sticky.expiresAt).toBeUndefined()
    expect(n.activeToasts.value.map((x) => x.id)).toEqual([sticky.id])
    await vi.runAllTimersAsync()
    expect(n.activeToasts.value.map((x) => x.id)).toEqual([sticky.id])
  })

  it('ephemeral toast auto-removes when its expiresAt fires', async () => {
    vi.useFakeTimers()
    const n = mountConsumer()
    n.push('hi', 'info', { toast: true, duration: 1000 })
    expect(n.activeToasts.value).toHaveLength(1)
    vi.advanceTimersByTime(1000)
    expect(n.activeToasts.value).toHaveLength(0)
    expect(n.notifications.value).toHaveLength(0)
  })

  it('persistent notifications survive any timer advance', async () => {
    vi.useFakeTimers()
    const n = mountConsumer()
    n.push('forever', 'info')
    await vi.runAllTimersAsync()
    expect(n.notifications.value).toHaveLength(1)
  })

  it('dismiss removes from every surface in one call', () => {
    const n = mountConsumer()
    const toast = n.push('hi', 'info', { toast: true, duration: 4000 })
    const persistent = n.push('kept', 'info')
    n.dismiss(toast.id)
    expect(n.activeToasts.value).toEqual([])
    expect(n.history.value.map((x) => x.id)).toEqual([persistent.id])
    n.dismiss(persistent.id)
    expect(n.notifications.value).toEqual([])
  })

  it('toasts do not count toward the unread badge', () => {
    const n = mountConsumer()
    n.push('toast', 'info', { toast: true })
    n.push('kept', 'info')
    expect(n.unreadCount.value).toBe(1)
  })

  it('markRead flips the read flag and shrinks unreadCount', () => {
    const n = mountConsumer()
    const note = n.push('x')
    expect(n.unreadCount.value).toBe(1)
    n.markRead(note.id)
    expect(n.unreadCount.value).toBe(0)
    expect(n.notifications.value[0].read).toBe(true)
  })

  it('markAllRead flips every flag in one call', () => {
    const n = mountConsumer()
    n.push('a')
    n.push('b')
    n.push('c')
    expect(n.unreadCount.value).toBe(3)
    n.markAllRead()
    expect(n.unreadCount.value).toBe(0)
  })

  it('clear empties the list and cancels every pending timer', async () => {
    vi.useFakeTimers()
    const n = mountConsumer()
    n.push('a', 'info', { toast: true, duration: 1000 })
    n.push('b', 'info', { toast: true, duration: 2000 })
    n.clear()
    expect(n.notifications.value).toEqual([])
    vi.advanceTimersByTime(5000)
    expect(n.notifications.value).toEqual([])
  })

  it('caps the list at maxItems to keep the bell drawer bounded', () => {
    const n = mountConsumer()
    for (let i = 0; i < 60; i += 1) {
      n.push(`m_${i}`)
    }
    expect(n.notifications.value).toHaveLength(50)
    expect(n.notifications.value[0].message).toBe('m_59')
  })

  it('shares state between consumers in the same Vue app', async () => {
    const seen: ReturnType<typeof useNotifications>[] = []

    const Inner = defineComponent({
      setup() {
        seen.push(useNotifications())
        return () => h('div')
      },
    })
    const Outer = defineComponent({
      setup() {
        seen.push(useNotifications())
        return () => h('div', [h(Inner)])
      },
    })

    mount(Outer)
    await nextTick()

    seen[0].push('shared')
    await flushPromises()

    expect(seen[1].notifications.value).toHaveLength(1)
    expect(seen[1].notifications.value[0].message).toBe('shared')
  })

  it('isolates state per Vue app', async () => {
    const seenA: ReturnType<typeof useNotifications>[] = []
    const seenB: ReturnType<typeof useNotifications>[] = []

    const AppA = defineComponent({
      setup() {
        seenA.push(useNotifications())
        return () => h('div')
      },
    })
    const AppB = defineComponent({
      setup() {
        seenB.push(useNotifications())
        return () => h('div')
      },
    })

    mount(AppA)
    seenA[0].push('a-only')
    mount(AppB)
    await flushPromises()

    expect(seenB[0].notifications.value).toEqual([])
  })
})
