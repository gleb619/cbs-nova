import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import WhatIfConfigPanel from '../runner/WhatIfConfigPanel.vue'

function mountPanel(props: Record<string, unknown>) {
  return mount(WhatIfConfigPanel, { props })
}

describe('WhatIfConfigPanel', () => {
  it('shows an empty state with only the Add mock button when no entries are present', () => {
    const wrapper = mountPanel({ modelValue: {} })
    expect(wrapper.findAll('li')).toHaveLength(0)
    expect(wrapper.text()).toContain('No mock entries.')
    const addButton = wrapper.find('button')
    expect(addButton.exists()).toBe(true)
    expect(addButton.text()).toBe('Add mock')
  })

  it('appends an empty row when Add mock is clicked', async () => {
    const wrapper = mountPanel({ modelValue: {} })
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    // Empty signature/payload contributes no entry to the map.
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{}])
  })

  it('emits a model entry when a row signature is filled in', async () => {
    const wrapper = mountPanel({ modelValue: {} })
    await wrapper.find('button').trigger('click')
    await flushPromises()
    const signatureInput = wrapper.find('input[type="text"]')
    await signatureInput.setValue('activity:MyActivity:invoke')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { 'activity:MyActivity:invoke': {} },
    ])
  })

  it('emits a parsed JSON payload alongside the signature', async () => {
    const wrapper = mountPanel({
      modelValue: { 'activity:MyActivity:invoke': {} },
    })
    const textarea = wrapper.find('textarea')
    await textarea.setValue('{"result":"mocked","count":3}')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      { 'activity:MyActivity:invoke': { result: 'mocked', count: 3 } },
    ])
  })

  it('shows an inline error and does not throw when JSON payload is invalid', async () => {
    const wrapper = mountPanel({
      modelValue: { 'activity:MyActivity:invoke': {} },
    })
    const beforeEmits = wrapper.emitted('update:modelValue')?.length ?? 0
    const textarea = wrapper.find('textarea')
    await textarea.setValue('not valid json')
    await flushPromises()
    expect(wrapper.text()).toContain('Invalid JSON')
    // The invalid row is dropped from the emitted model — no new entry for it.
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{}])
    // No exception escaped the component (no thrown render error).
    expect(wrapper.emitted('update:modelValue')?.length ?? 0).toBeGreaterThanOrEqual(
      beforeEmits,
    )
  })

  it('removes a row and emits the model without that entry', async () => {
    const wrapper = mountPanel({
      modelValue: {
        'activity:A:invoke': { a: 1 },
        'mq:topic:send': { b: 2 },
      },
    })
    expect(wrapper.findAll('li')).toHaveLength(2)
    const removeButtons = wrapper.findAll('button')
    // First row's Remove button — find by text.
    const firstRemove = removeButtons.find((b) => b.text() === 'Remove')
    expect(firstRemove).toBeTruthy()
    await firstRemove?.trigger('click')
    await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(1)
    const last = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as Record<string, unknown>
    expect(Object.keys(last)).toEqual(['mq:topic:send'])
  })
})