import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RunnerStatus } from '../../types/runner'
import StatusIndicator from '../runner/StatusIndicator.vue'

interface StatusCase {
  status: RunnerStatus
  label: string
  classes: string[]
  animate: boolean
}

const STATUS_CASES: StatusCase[] = [
  {
    status: 'idle',
    label: 'Idle',
    classes: ['bg-gray-200', 'text-gray-700'],
    animate: false,
  },
  {
    status: 'loading',
    label: 'Loading',
    classes: ['bg-yellow-100', 'text-yellow-800'],
    animate: true,
  },
  {
    status: 'running',
    label: 'Running',
    classes: ['bg-blue-100', 'text-blue-800'],
    animate: true,
  },
  {
    status: 'success',
    label: 'Success',
    classes: ['bg-green-100', 'text-green-800'],
    animate: false,
  },
  {
    status: 'failed',
    label: 'Failed',
    classes: ['bg-red-100', 'text-red-800'],
    animate: false,
  },
]

describe('StatusIndicator', () => {
  it.each(STATUS_CASES)(
    'renders $status with the correct label, colour classes, and pulse animation',
    ({ status, label, classes, animate }) => {
      const wrapper = mount(StatusIndicator, { props: { status } })

      const root = wrapper.find('[role="status"]')
      expect(root.exists()).toBe(true)
      expect(root.attributes('aria-label')).toBe(`Runner status: ${label}`)
      expect(root.text()).toContain(label)

      for (const cls of classes) {
        expect(root.classes()).toContain(cls)
      }

      const dot = root.find('span')
      expect(dot.exists()).toBe(true)
      if (animate) {
        expect(dot.classes()).toContain('animate-pulse')
      } else {
        expect(dot.classes()).not.toContain('animate-pulse')
      }
    },
  )
})