import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorsTab from '../executions/ErrorsTab.vue'

describe('ErrorsTab', () => {
  it('renders each error message and code', () => {
    const errors = [
      { message: 'First failure', code: 'ERR_001' },
      { message: 'Second failure', code: 'ERR_002' },
    ]

    const wrapper = mount(ErrorsTab, { props: { errors } })

    expect(wrapper.text()).toContain('First failure')
    expect(wrapper.text()).toContain('Second failure')
    expect(wrapper.text()).toContain('Code: ERR_001')
    expect(wrapper.text()).toContain('Code: ERR_002')
  })

  it('hides the stack trace by default and reveals it on toggle', async () => {
    const errors = [{ message: 'Oops', code: 'ERR_003', stackTrace: 'at line 42' }]

    const wrapper = mount(ErrorsTab, { props: { errors } })

    const toggleButton = wrapper.find('button')
    expect(toggleButton.exists()).toBe(true)
    expect(toggleButton.text()).toBe('Show stack trace')
    expect(wrapper.text()).not.toContain('at line 42')

    await toggleButton.trigger('click')

    expect(wrapper.find('button').text()).toBe('Hide stack trace')
    expect(wrapper.text()).toContain('at line 42')

    await wrapper.find('button').trigger('click')

    expect(wrapper.find('button').text()).toBe('Show stack trace')
    expect(wrapper.text()).not.toContain('at line 42')
  })

  it('renders a placeholder and no error cards when errors is undefined', () => {
    const wrapper = mount(ErrorsTab, { props: { errors: undefined } })

    expect(wrapper.text()).toContain('No errors recorded.')
    expect(wrapper.findAll('.bg-white.border.border-red-200')).toHaveLength(0)
  })

  it('renders a placeholder and no error cards when errors is empty', () => {
    const wrapper = mount(ErrorsTab, { props: { errors: [] } })

    expect(wrapper.text()).toContain('No errors recorded.')
    expect(wrapper.findAll('.bg-white.border.border-red-200')).toHaveLength(0)
  })

  it('does not render a code line when code is missing', () => {
    const errors = [{ message: 'No code here' }]

    const wrapper = mount(ErrorsTab, { props: { errors } })

    expect(wrapper.text()).toContain('No code here')
    expect(wrapper.text()).not.toContain('Code:')
  })

  it('does not render a stack trace toggle when stackTrace is missing', () => {
    const errors = [{ message: 'No stack', code: 'ERR_004' }]

    const wrapper = mount(ErrorsTab, { props: { errors } })

    expect(wrapper.find('button').exists()).toBe(false)
  })
})
