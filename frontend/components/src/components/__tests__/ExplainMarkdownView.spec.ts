import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ExplainMarkdownView from '../runner/ExplainMarkdownView.vue'

const { mermaidRender } = vi.hoisted(() => ({ mermaidRender: vi.fn() }))

vi.mock('mermaid', () => ({
  default: {
    initialize: vi.fn(),
    render: mermaidRender,
  },
}))

describe('ExplainMarkdownView', () => {
  beforeEach(() => {
    mermaidRender.mockReset()
  })

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

  it('renders mermaid fences as diagrams when the mermaid prop is set', async () => {
    mermaidRender.mockResolvedValue({ svg: '<svg data-mock="diagram"></svg>' })
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '```mermaid\ngraph TD; A-->B\n```\n', mermaid: true },
    })

    await flushPromises()

    const diagram = wrapper.find('[data-testid="explain-mermaid-diagram"]')
    expect(diagram.exists()).toBe(true)
    expect(diagram.find('svg').exists()).toBe(true)
    expect(mermaidRender).toHaveBeenCalledTimes(1)
  })

  it('does not invoke mermaid when the mermaid prop is unset', async () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '```mermaid\ngraph TD; A-->B\n```\n' },
    })

    await flushPromises()

    expect(wrapper.find('[data-testid="explain-mermaid-diagram"]').exists()).toBe(false)
    expect(mermaidRender).not.toHaveBeenCalled()
  })

  it('keeps the code block as pre text when mermaid rendering fails', async () => {
    mermaidRender.mockRejectedValue(new Error('bad diagram'))
    const wrapper = mount(ExplainMarkdownView, {
      props: { markdown: '```mermaid\ngraph TD; broken\n```\n', mermaid: true },
    })

    await flushPromises()

    expect(wrapper.find('[data-testid="explain-mermaid-diagram"]').exists()).toBe(false)
    const pre = wrapper.find('[data-testid="explain-markdown-rendered"] pre')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toContain('graph TD; broken')
    expect(mermaidRender).toHaveBeenCalledTimes(1)
  })
})
