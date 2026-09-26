import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ConstructStatus } from '../../types/dsl'
import ConstructStatusIcon from '../dsl/ConstructStatusIcon.vue'

const statuses: ConstructStatus[] = [
  'Draft',
  'Valid',
  'Invalid',
  'Published',
  'Modified',
  'Added',
  'Deleted',
  'Conflicting',
]

describe('ConstructStatusIcon', () => {
  it('renders an svg with a data-testid per status', () => {
    for (const status of statuses) {
      const wrapper = mount(ConstructStatusIcon, { props: { status } })
      const svg = wrapper.find(`[data-testid="construct-status-icon-${status}"]`)
      expect(svg.exists(), status).toBe(true)
      expect(svg.element.tagName.toLowerCase()).toBe('svg')
    }
  })

  it('exposes the status as aria-label and title', () => {
    const wrapper = mount(ConstructStatusIcon, { props: { status: 'Modified' } })

    expect(wrapper.attributes('aria-label')).toBe('Status: Modified')
    expect(wrapper.find('title').text()).toBe('Modified')
  })

  it('colors the icon by status', () => {
    const expected: Record<ConstructStatus, string> = {
      Published: 'text-sky-400',
      Modified: 'text-yellow-400',
      Added: 'text-emerald-400',
      Deleted: 'text-orange-400',
      Conflicting: 'text-red-500',
      Draft: 'text-gray-400',
      Valid: 'text-green-400',
      Invalid: 'text-red-400',
    }
    for (const [status, cls] of Object.entries(expected)) {
      const wrapper = mount(ConstructStatusIcon, {
        props: { status: status as ConstructStatus },
      })
      expect(wrapper.classes(), status).toContain(cls)
    }
  })

  it('draws distinct path content per status', () => {
    const paths = new Set(
      statuses.map((status) => mount(ConstructStatusIcon, { props: { status } }).find('g').html()),
    )
    expect(paths.size).toBe(statuses.length)
  })
})
