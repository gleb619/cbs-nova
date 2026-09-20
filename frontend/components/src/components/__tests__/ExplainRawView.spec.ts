import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExplainRawView from '../runner/ExplainRawView.vue'

describe('ExplainRawView', () => {
  it('renders an empty-state placeholder when no markdown is provided', () => {
    const wrapper = mount(ExplainRawView, {
      props: { markdown: undefined },
    })
    expect(wrapper.find('[data-testid="explain-raw-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="explain-raw-pre"]').exists()).toBe(false)
  })

  it('renders the raw markdown source verbatim', () => {
    const wrapper = mount(ExplainRawView, {
      props: { markdown: '# Heading\n\n**bold**' },
    })
    const pre = wrapper.find('[data-testid="explain-raw-pre"]')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toBe('# Heading\n\n**bold**')
    expect(pre.html()).not.toContain('<h1')
  })

  it('renders a report object as pretty-printed JSON', () => {
    const report = { name: 'demo', description: 'd', markdown: 'body', children: [] }
    const wrapper = mount(ExplainRawView, {
      props: { report },
    })
    const pre = wrapper.find('[data-testid="explain-raw-pre"]')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toBe(JSON.stringify(report, null, 2))
  })

  it('prefers the report over markdown when both are provided', () => {
    const wrapper = mount(ExplainRawView, {
      props: { markdown: '# md', report: { name: 'demo' } },
    })
    const pre = wrapper.find('[data-testid="explain-raw-pre"]')
    expect(pre.text()).toContain('"name": "demo"')
    expect(pre.text()).not.toContain('# md')
  })

  it('passes the source to the raw slot for custom rendering', () => {
    const wrapper = mount(ExplainRawView, {
      props: { markdown: 'source' },
      slots: {
        raw: '<div data-testid="custom-raw">{{ params.text }}</div>',
      },
    })
    expect(wrapper.find('[data-testid="custom-raw"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="custom-raw"]').text()).toBe('source')
    expect(wrapper.find('[data-testid="explain-raw-pre"]').exists()).toBe(false)
  })
})
