import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExplainMarkdownView from '../runner/ExplainMarkdownView.vue'

describe('ExplainMarkdownView', () => {
  it('renders an empty-state placeholder when no markdown is provided', () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: undefined },
    })
    expect(wrapper.find('[data-testid="explain-markdown-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="explain-markdown-rendered"]').exists()).toBe(false)
  })

  it('renders markdown as HTML via marked when no slot is provided', () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '# Title\n\n**bold**' },
    })
    const rendered = wrapper.find('[data-testid="explain-markdown-rendered"]')
    expect(rendered.exists()).toBe(true)
    expect(rendered.html()).toContain('<h1')
    expect(rendered.html()).toContain('Title')
    expect(rendered.html()).toContain('<strong>bold</strong>')
  })

  it('passes computed HTML to the rendered slot for custom rendering', () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '# Heading' },
      slots: {
        rendered: '<div data-testid="custom-rendered">custom :: {{ params.html }}</div>',
      },
    })
    expect(wrapper.find('[data-testid="custom-rendered"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="custom-rendered"]').text()).toContain('<h1')
    expect(wrapper.find('[data-testid="explain-markdown-rendered"]').exists()).toBe(false)
  })

  it('honors the rendered prop override', () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '# ignored', rendered: '<em>forced</em>' },
    })
    const el = wrapper.find('[data-testid="explain-markdown-rendered"]')
    expect(el.exists()).toBe(true)
    expect(el.html()).toContain('<em>forced</em>')
    expect(el.html()).not.toContain('<h1')
  })
})
