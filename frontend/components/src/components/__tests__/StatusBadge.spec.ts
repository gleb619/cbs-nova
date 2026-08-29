import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ExecutionStatus } from '../../types/execution'
import StatusBadge from '../executions/StatusBadge.vue'

describe('StatusBadge', () => {
  it.each<{ status: ExecutionStatus; expectedClasses: string[] }>([
    { status: 'Pending', expectedClasses: ['bg-gray-200', 'text-gray-800'] },
    { status: 'Running', expectedClasses: ['bg-blue-500', 'text-white', 'animate-pulse'] },
    { status: 'Completed', expectedClasses: ['bg-green-500', 'text-white'] },
    { status: 'Failed', expectedClasses: ['bg-red-500', 'text-white'] },
    { status: 'Compensated', expectedClasses: ['bg-orange-500', 'text-white'] },
    { status: 'Stale', expectedClasses: ['bg-warning-500', 'text-white'] },
    { status: 'Cancelled', expectedClasses: ['bg-gray-500', 'text-white'] },
  ])('renders $status with the correct color classes and text', ({ status, expectedClasses }) => {
    const wrapper = mount(StatusBadge, { props: { status } })

    const badge = wrapper.find('[data-testid="status-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe(status)

    for (const className of expectedClasses) {
      expect(badge.classes()).toContain(className)
    }
  })

  it('exposes a polite live status region on the badge', () => {
    const wrapper = mount(StatusBadge, { props: { status: 'Running' } })

    const badge = wrapper.find('[data-testid="status-badge"]')
    expect(badge.attributes('role')).toBe('status')
    expect(badge.attributes('aria-live')).toBe('polite')
    expect(badge.attributes('aria-atomic')).toBe('true')
  })

  it('shows the stale polling indicator only when status is Stale and polling is true', () => {
    const notPolling = mount(StatusBadge, { props: { status: 'Stale', polling: false } })
    expect(notPolling.find('[data-testid="stale-poll-indicator"]').exists()).toBe(false)

    const wrongStatus = mount(StatusBadge, { props: { status: 'Running', polling: true } })
    expect(wrongStatus.find('[data-testid="stale-poll-indicator"]').exists()).toBe(false)

    const polling = mount(StatusBadge, { props: { status: 'Stale', polling: true } })
    const indicator = polling.find('[data-testid="stale-poll-indicator"]')
    expect(indicator.exists()).toBe(true)
    expect(indicator.attributes('aria-label')).toBe('Refreshing stale status')
  })
})
