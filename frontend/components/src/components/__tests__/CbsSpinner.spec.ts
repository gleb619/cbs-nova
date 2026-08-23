import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CbsSpinner from '../CbsSpinner.vue'

describe('CbsSpinner', () => {
  it('renders spinner and label by default', () => {
    const wrapper = mount(CbsSpinner)

    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Loading...')
    expect(wrapper.find('.animate-spin').exists()).toBe(true)
  })

  it('does not render when loading is false', () => {
    const wrapper = mount(CbsSpinner, { props: { loading: false } })

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })

  it('applies size classes', () => {
    const wrapper = mount(CbsSpinner, { props: { size: 'lg' } })

    expect(wrapper.find('.w-8').exists()).toBe(true)
    expect(wrapper.find('.h-8').exists()).toBe(true)
    expect(wrapper.find('.border-4').exists()).toBe(true)
  })

  it('shows custom label', () => {
    const wrapper = mount(CbsSpinner, { props: { label: 'Please wait' } })

    expect(wrapper.text()).toContain('Please wait')
  })

  it('hides label when label is empty', () => {
    const wrapper = mount(CbsSpinner, { props: { label: '' } })

    expect(wrapper.text()).toBe('')
  })
})
