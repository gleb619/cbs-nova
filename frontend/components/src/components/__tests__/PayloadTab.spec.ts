import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PayloadTab from '../executions/PayloadTab.vue'

describe('PayloadTab', () => {
  it('renders the payload tab root with the expected data-testid', () => {
    const wrapper = mount(PayloadTab, { props: { input: undefined, output: undefined } })

    expect(wrapper.find('[data-testid="executions-payload-tab"]').exists()).toBe(true)
  })

  it('renders formatted input and output JSON', () => {
    const input = { name: 'Alice', age: 30 }
    const output = { result: 'ok' }

    const wrapper = mount(PayloadTab, { props: { input, output } })

    const codes = wrapper.findAll('code')
    expect(codes).toHaveLength(2)
    expect(codes[0].text()).toBe(JSON.stringify(input, null, 2))
    expect(codes[1].text()).toBe(JSON.stringify(output, null, 2))
  })

  it('renders em-dash for undefined input and output', () => {
    const wrapper = mount(PayloadTab, { props: { input: undefined, output: undefined } })

    const codes = wrapper.findAll('code')
    expect(codes).toHaveLength(2)
    expect(codes[0].text()).toBe('—')
    expect(codes[1].text()).toBe('—')
  })

  it('does not throw when input/output are non-JSON-serializable circular references', () => {
    const circular: Record<string, unknown> = { self: undefined }
    circular.self = circular

    expect(() => mount(PayloadTab, { props: { input: circular, output: undefined } })).not.toThrow()
  })
})
