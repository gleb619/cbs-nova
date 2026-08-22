import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ValidationError } from '../../types/dsl'
import ProblemsPanel from '../dsl/ProblemsPanel.vue'

describe('ProblemsPanel', () => {
  it('renders the empty state when no errors are supplied', () => {
    const wrapper = mount(ProblemsPanel, { props: { errors: [] } })

    expect(wrapper.text()).toContain('Problems (0)')
    expect(wrapper.text()).toContain('No problems detected.')
    expect(wrapper.findAll('li')).toHaveLength(0)
  })

  it('renders each ValidationError with field path and message', () => {
    const errors: ValidationError[] = [
      { field: 'steps[0].name', message: 'name is required', severity: 'error' },
      { field: 'process.version', message: 'version must be semver', severity: 'warning' },
    ]

    const wrapper = mount(ProblemsPanel, { props: { errors } })

    expect(wrapper.text()).toContain('Problems (2)')

    const items = wrapper.findAll('li')
    expect(items).toHaveLength(2)

    expect(items[0]!.text()).toContain('steps[0].name')
    expect(items[0]!.text()).toContain('name is required')

    expect(items[1]!.text()).toContain('process.version')
    expect(items[1]!.text()).toContain('version must be semver')
  })

  it('applies red severity dot for errors and yellow for warnings', () => {
    const errors: ValidationError[] = [
      { field: 'a', message: 'bad', severity: 'error' },
      { field: 'b', message: 'meh', severity: 'warning' },
    ]

    const wrapper = mount(ProblemsPanel, { props: { errors } })

    const dots = wrapper.findAll('li > span.rounded-full')
    expect(dots).toHaveLength(2)
    expect(dots[0]!.classes()).toContain('bg-red-500')
    expect(dots[1]!.classes()).toContain('bg-yellow-500')
  })
})
