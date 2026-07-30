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
  ])('renders $status with the correct color classes and text', ({ status, expectedClasses }) => {
    const wrapper = mount(StatusBadge, { props: { status } })

    const badge = wrapper.find('span')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe(status)

    for (const className of expectedClasses) {
      expect(badge.classes()).toContain(className)
    }
  })
})
