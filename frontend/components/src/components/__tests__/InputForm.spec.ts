import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InputForm from '../runner/InputForm.vue'
import InputField from '../runner/InputField.vue'

const schemaFixture = {
  type: 'object',
  properties: {
    name: { type: 'string' },
    age: { type: 'number' },
    active: { type: 'boolean' },
  },
  required: ['name'],
}

function mountInputForm(props: Record<string, unknown>) {
  return mount(InputForm, {
    props,
    global: { components: { InputField } },
  })
}

describe('InputForm', () => {
  it('renders a typed field per schema property', () => {
    const wrapper = mountInputForm({ schema: schemaFixture, modelValue: {} })
    expect(wrapper.findAllComponents(InputField)).toHaveLength(3)
    expect(wrapper.find('#input-name').exists()).toBe(true)
    expect(wrapper.find('#input-age').exists()).toBe(true)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('name')
    expect(wrapper.text()).toContain('age')
  })

  it('renders the freeform textarea when schema is missing', () => {
    const wrapper = mountInputForm({ schema: undefined, modelValue: {} })
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.text()).toContain('Input (JSON)')
    expect(wrapper.findAllComponents(InputField)).toHaveLength(0)
  })

  it('renders the freeform textarea when schema properties are empty', () => {
    const wrapper = mountInputForm({ schema: { type: 'object', properties: {} }, modelValue: {} })
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.findAllComponents(InputField)).toHaveLength(0)
  })

  it('emits update:modelValue when a schema field is edited', async () => {
    const wrapper = mountInputForm({ schema: schemaFixture, modelValue: {} })
    const input = wrapper.find('#input-name')
    expect(input.exists()).toBe(true)
    await input.setValue('alice')
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![emitted!.length - 1]).toEqual([{ name: 'alice' }])
  })

  it('aggregates multiple schema field values into one object when the parent updates modelValue', async () => {
    const wrapper = mountInputForm({ schema: schemaFixture, modelValue: { name: 'alice' } })
    await wrapper.find('#input-age').setValue('42')
    await flushPromises()
    await wrapper.setProps({ modelValue: { name: 'alice', age: '42' } })
    await wrapper.find('#input-active').setValue(true)
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted![emitted!.length - 1]).toEqual([
      { name: 'alice', age: '42', active: true },
    ])
  })

  it('honors the required flag from the schema', () => {
    const wrapper = mountInputForm({ schema: schemaFixture, modelValue: {} })
    const fields = wrapper.findAllComponents(InputField)
    expect(fields[0].props('name')).toBe('name')
    expect(fields[0].props('required')).toBe(true)
    expect(wrapper.find('label[for="input-name"]').text()).toContain('*')
    expect(fields[1].props('required')).toBe(false)
    expect(fields[2].props('required')).toBe(false)
  })

  it('emits a parsed object from valid freeform JSON', async () => {
    const wrapper = mountInputForm({ schema: undefined, modelValue: {} })
    const textarea = wrapper.find('textarea')
    await textarea.setValue('{"count": 7, "enabled": true}')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([[{ count: 7, enabled: true }]])
    expect(wrapper.text()).not.toContain('Invalid JSON')
  })

  it('emits an empty object when freeform JSON is empty', async () => {
    const wrapper = mountInputForm({ schema: undefined, modelValue: {} })
    await wrapper.find('textarea').setValue('')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([[{}]])
  })

  it('does not emit and shows an error for invalid freeform JSON', async () => {
    const wrapper = mountInputForm({ schema: undefined, modelValue: {} })
    await wrapper.find('textarea').setValue('not valid json')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).toContain('Invalid JSON')
    expect(wrapper.text()).toContain('Unexpected token')
  })

  it('does not emit an array parsed from freeform JSON as the model value', async () => {
    const wrapper = mountInputForm({ schema: undefined, modelValue: {} })
    await wrapper.find('textarea').setValue('[1, 2, 3]')
    await flushPromises()
    expect(wrapper.emitted('update:modelValue')).toEqual([[{}]])
  })
})
