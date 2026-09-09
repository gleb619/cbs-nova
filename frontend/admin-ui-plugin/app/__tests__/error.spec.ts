import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ErrorPage from '../error.vue'

const { clearError } = vi.hoisted(() => ({
  clearError: vi.fn(),
}))

vi.mock('nuxt/app', () => ({
  clearError,
}))

function mountPage(error?: { statusCode?: number; statusMessage?: string; message?: string }) {
  return mount(ErrorPage, {
    props: error === undefined ? undefined : { error },
    attachTo: document.body,
  })
}

describe('error.vue Nuxt error page', () => {
  beforeEach(() => {
    clearError.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('defaults to 500 and Something went wrong when no error prop is provided', () => {
    const wrapper = mountPage()

    expect(wrapper.find('[data-testid="error-page"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('500')
    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('renders 404 and Page not found for a 404 error', () => {
    const wrapper = mountPage({ statusCode: 404 })

    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('Page not found')
  })

  it('prefers statusMessage over the 404 default message', () => {
    const wrapper = mountPage({ statusCode: 404, statusMessage: 'Nope' })

    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('Nope')
    expect(wrapper.text()).not.toContain('Page not found')
  })

  it('falls back to message when statusMessage is absent', () => {
    const wrapper = mountPage({ statusCode: 503, message: 'Down' })

    expect(wrapper.text()).toContain('503')
    expect(wrapper.text()).toContain('Down')
  })

  it('shows the default message for a non-404 error without explicit text', () => {
    const wrapper = mountPage({ statusCode: 500 })

    expect(wrapper.text()).toContain('500')
    expect(wrapper.text()).toContain('Something went wrong')
  })

  it('calls clearError with redirect target when Go to dashboard is clicked', async () => {
    const wrapper = mountPage({ statusCode: 500 })

    await wrapper.find('button').trigger('click')

    expect(clearError).toHaveBeenCalledTimes(1)
    expect(clearError).toHaveBeenCalledWith({ redirect: '/' })
  })
})
