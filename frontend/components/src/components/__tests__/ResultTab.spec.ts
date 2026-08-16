import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ResultTab from '../runner/ResultTab.vue'

describe('ResultTab', () => {
  it('renders a placeholder and does not throw when result is undefined', () => {
    expect(() => mount(ResultTab, { props: { result: undefined } })).not.toThrow()

    const wrapper = mount(ResultTab, { props: { result: undefined } })
    expect(wrapper.text()).toContain('No result yet.')
    expect(wrapper.find('pre').exists()).toBe(false)
  })

  it('renders a placeholder and does not throw when result is null', () => {
    expect(() => mount(ResultTab, { props: { result: null } })).not.toThrow()

    const wrapper = mount(ResultTab, { props: { result: null } })
    expect(wrapper.text()).toContain('No result yet.')
    expect(wrapper.find('pre').exists()).toBe(false)
  })

  it('renders a string result as a JSON-encoded string (with quotes)', () => {
    const wrapper = mount(ResultTab, { props: { result: 'hello world' } })
    const pre = wrapper.find('pre')
    expect(pre.exists()).toBe(true)
    // JSON.stringify wraps strings in quotes — the spec is "stringified/structured",
    // so verify the encoded payload renders, not the raw string.
    expect(pre.text()).toBe('"hello world"')
    expect(wrapper.text()).not.toContain('No result yet.')
  })

  it('renders an object result as pretty-printed JSON', () => {
    const wrapper = mount(ResultTab, {
      props: { result: { ok: true, count: 2 } },
    })
    const pre = wrapper.find('pre')
    expect(pre.exists()).toBe(true)
    const text = pre.text()
    expect(text).toContain('"ok": true')
    expect(text).toContain('"count": 2')
    expect(text).toContain('\n')
  })

  it('renders an array result as pretty-printed JSON', () => {
    const wrapper = mount(ResultTab, {
      props: { result: [1, 2, 3] },
    })
    const pre = wrapper.find('pre')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toContain('1')
    expect(pre.text()).toContain('2')
    expect(pre.text()).toContain('3')
  })

  it('falls back to String() when JSON.stringify throws on circular references', () => {
    const circular: Record<string, unknown> = { name: 'root' }
    circular.self = circular

    expect(() => mount(ResultTab, { props: { result: circular } })).not.toThrow()

    const wrapper = mount(ResultTab, { props: { result: circular } })
    const pre = wrapper.find('pre')
    expect(pre.exists()).toBe(true)
    expect(pre.text()).toContain('[object Object]')
  })
})
