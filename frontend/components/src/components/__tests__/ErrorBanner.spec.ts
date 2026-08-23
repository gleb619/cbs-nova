import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorBanner from '../ErrorBanner.vue'

describe('ErrorBanner', () => {
  it('renders the message and exposes role=alert', () => {
    const wrapper = mount(ErrorBanner, { props: { message: 'Boom' } })

    const banner = wrapper.find('[role="alert"]')
    expect(banner.exists()).toBe(true)
    expect(banner.text()).toContain('Boom')
  })

  it('uses the default Retry label', () => {
    const wrapper = mount(ErrorBanner, { props: { message: 'Boom' } })

    const button = wrapper.find('button')
    expect(button.text()).toBe('Retry')
  })

  it('honors a custom retryLabel', () => {
    const wrapper = mount(ErrorBanner, {
      props: { message: 'Boom', retryLabel: 'Try again' },
    })

    expect(wrapper.find('button').text()).toBe('Try again')
  })

  it('emits retry when the button is clicked', async () => {
    const wrapper = mount(ErrorBanner, { props: { message: 'Boom' } })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry')).toBeTruthy()
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
