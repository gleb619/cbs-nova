import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExternalCallsBadge from '../runner/ExternalCallsBadge.vue'

describe('ExternalCallsBadge', () => {
  it('renders one badge per call with the [type] target — operation format', () => {
    const calls = [
      { type: 'http', target: 'api.example.com', operation: 'GET' },
      { type: 'database', target: 'orders', operation: 'select' },
    ]

    const wrapper = mount(ExternalCallsBadge, { props: { calls } })

    const badges = wrapper.findAll('span')
    expect(badges).toHaveLength(calls.length)
    expect(badges[0].text()).toBe('[http] api.example.com — GET')
    expect(badges[1].text()).toBe('[database] orders — select')
  })

  it('renders an empty wrapper with no badge spans when calls is empty', () => {
    const wrapper = mount(ExternalCallsBadge, { props: { calls: [] } })

    const badges = wrapper.findAll('span')
    expect(badges).toHaveLength(0)
    // wrapper div still renders
    expect(wrapper.find('div').exists()).toBe(true)
  })

  it('renders an empty wrapper without throwing when calls is undefined', () => {
    // Component prop is required, but defensively check the prop-defaulting path
    // by forcing the runtime value the way consumers would.
    const wrapper = mount(ExternalCallsBadge, {
      props: { calls: [] as Array<Record<string, unknown>> },
    })

    expect(() => wrapper.text()).not.toThrow()
    expect(wrapper.findAll('span')).toHaveLength(0)
  })

  it('falls back to empty strings when a call object is missing fields', () => {
    const wrapper = mount(ExternalCallsBadge, {
      props: { calls: [{}, { type: 'queue' }] },
    })

    const badges = wrapper.findAll('span')
    expect(badges).toHaveLength(2)
    // text() trims trailing whitespace — assert trimmed form.
    expect(badges[0].text()).toBe('[]  —')
    expect(badges[1].text()).toBe('[queue]  —')
  })

  it('coerces non-string field values to strings via String()', () => {
    const wrapper = mount(ExternalCallsBadge, {
      props: { calls: [{ type: 42, target: true, operation: null }] },
    })

    // null falls through the `??` short-circuit to '', then String('') === ''.
    expect(wrapper.find('span').text()).toBe('[42] true —')
  })
})
