import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { resetNotificationsState, useNotifications } from '../../composables/useNotifications'
import NotificationsWidget from '../NotificationsWidget.vue'

const mountWidget = (props: Record<string, unknown> = {}) =>
  mount(NotificationsWidget, {
    props,
    global: { stubs: { teleport: true } },
  })

describe('NotificationsWidget', () => {
  beforeEach(() => {
    resetNotificationsState()
  })
  afterEach(() => {
    resetNotificationsState()
  })

  it('shows zero on the badge when no notifications exist', () => {
    const wrapper = mountWidget()
    expect(wrapper.find('[data-testid="notifications-widget-count"]').text()).toBe('0')
  })

  it('reflects the unread count on the badge (persistent only)', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    n.push('a')
    n.push('b')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="notifications-widget-count"]').text()).toBe('2')
  })

  it('toasts do not inflate the unread badge — they vanish when they expire', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    n.push('kept', 'info')
    n.push('transient', 'info', { toast: true, duration: 4000 })
    await wrapper.vm.$nextTick()
    // Only the persistent (non-toast) record counts toward unread.
    expect(wrapper.find('[data-testid="notifications-widget-count"]').text()).toBe('1')
  })

  it('keeps the drawer closed until Details is pressed', async () => {
    const wrapper = mountWidget()
    expect(wrapper.find('[data-testid="notifications-drawer"]').exists()).toBe(false)
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    expect(wrapper.find('[data-testid="notifications-drawer"]').exists()).toBe(true)
    expect(wrapper.emitted('open')).toHaveLength(1)
  })

  it('shows the empty state when there are no notifications', async () => {
    const wrapper = mountWidget()
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    expect(wrapper.find('[data-testid="notifications-drawer-empty"]').exists()).toBe(true)
  })

  it('lists every notification in the drawer, newest first', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    n.push('first')
    n.push('second', 'warning', { toast: true, duration: 4000 })
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    expect(wrapper.find('[data-testid="notifications-drawer-empty"]').exists()).toBe(false)
    const items = wrapper.findAll('[data-testid^="notifications-drawer-item-"]')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('second')
    expect(items[1].text()).toContain('first')
    // Records opted into the toast surface carry a "toast" badge.
    expect(items[0].find('[data-testid^="notifications-drawer-toast-"]').exists()).toBe(true)
    expect(items[1].find('[data-testid^="notifications-drawer-toast-"]').exists()).toBe(false)
  })

  it('marks a notification as read when the row is clicked and emits select', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    const note = n.push('clicked')
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    await wrapper.find(`[data-testid="notifications-drawer-item-${note.id}"]`).trigger('click')
    expect(wrapper.emitted('select')).toEqual([[note.id]])
    expect(n.notifications.value[0].read).toBe(true)
  })

  it('markAllRead flips every flag and the button is disabled when nothing is unread', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    n.push('a')
    n.push('b')
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    const button = wrapper.find('[data-testid="notifications-drawer-mark-all"]')
    expect(button.attributes('disabled')).toBeUndefined()
    await button.trigger('click')
    expect(n.unreadCount.value).toBe(0)
    expect(
      wrapper.find('[data-testid="notifications-drawer-mark-all"]').attributes('disabled'),
    ).toBeDefined()
  })

  it('dismiss button on a row removes that notification everywhere', async () => {
    const wrapper = mountWidget()
    const n = useNotifications()
    const note = n.push('drop me')
    await wrapper.vm.$nextTick()
    await wrapper.find('[data-testid="notifications-widget-details"]').trigger('click')
    await wrapper.find(`[data-testid="notifications-drawer-dismiss-${note.id}"]`).trigger('click')
    expect(n.notifications.value).toHaveLength(0)
  })
})
