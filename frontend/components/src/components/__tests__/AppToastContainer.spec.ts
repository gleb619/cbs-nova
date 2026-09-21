import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import AppToastContainer from '../AppToastContainer.vue'
import { resetNotificationsState, useNotifications } from '../../composables/useNotifications'

// The container teleports into <body>; stub Teleport so it renders in place.
const mountContainer = () => mount(AppToastContainer, { global: { stubs: { teleport: true } } })

describe('AppToastContainer', () => {
  beforeEach(() => {
    resetNotificationsState()
  })
  afterEach(() => {
    resetNotificationsState()
  })

  it('renders nothing when no toasts are active', () => {
    const wrapper = mountContainer()
    expect(wrapper.find('[data-testid="app-toast-container"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="app-toast"]')).toHaveLength(0)
  })

  it('renders one card per active toast with the right kind', async () => {
    const wrapper = mountContainer()
    const n = useNotifications()
    n.push('ok', 'success', { toast: true, duration: 4000 })
    n.push('bad', 'error', { toast: true, duration: 0 }) // sticky
    await wrapper.vm.$nextTick()
    const cards = wrapper.findAll('[data-testid="app-toast"]')
    expect(cards).toHaveLength(2)
    // Newest first.
    expect(cards[0].attributes('data-toast-kind')).toBe('error')
    expect(cards[1].attributes('data-toast-kind')).toBe('success')
  })

  it('only renders records opted into the toast surface', async () => {
    const wrapper = mountContainer()
    const n = useNotifications()
    n.push('persistent', 'info') // no toast flag → bell history only
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="app-toast"]')).toHaveLength(0)
  })

  it('removes the toast when the dismiss button is clicked', async () => {
    const wrapper = mountContainer()
    const n = useNotifications()
    n.push('gone', 'info', { toast: true, duration: 4000 })
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="app-toast"]')).toHaveLength(1)
    await wrapper.find('[data-testid="app-toast-dismiss"]').trigger('click')
    expect(wrapper.findAll('[data-testid="app-toast"]')).toHaveLength(0)
    // The underlying notification is gone — dismiss is unified.
    expect(n.notifications.value).toEqual([])
  })
})
