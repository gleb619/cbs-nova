import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SchemaForm from '../dsl/SchemaForm.vue'
import SchemaFormField from '../dsl/SchemaFormField.vue'

const objectSchema = {
  type: 'object',
  properties: {
    name: { type: 'string' },
    age: { type: 'number' },
    active: { type: 'boolean' },
    role: { type: 'string', enum: ['admin', 'user'] },
  },
  required: ['name'],
}

function mountSchemaForm(props: Record<string, unknown> = {}) {
  return mount(SchemaForm, {
    props,
    global: { components: { SchemaFormField } },
  })
}

describe('SchemaForm', () => {
  it('exposes root data-testid', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    expect(wrapper.find('[data-testid="schema-form"]').exists()).toBe(true)
  })

  it('renders a field for each object property', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    expect(wrapper.findAllComponents(SchemaFormField)).toHaveLength(4)
  })

  it('renders text input for string', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    const input = wrapper.find('[data-testid="schema-field-name"]')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('text')
  })

  it('renders number input for number', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    const input = wrapper.find('[data-testid="schema-field-age"]')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('number')
  })

  it('renders checkbox input for boolean', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    const input = wrapper.find('[data-testid="schema-field-active"]')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('checkbox')
  })

  it('renders select input for enum string', () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    const input = wrapper.find('[data-testid="schema-field-role"]')
    expect(input.exists()).toBe(true)
    expect(input.element.tagName.toLowerCase()).toBe('select')
  })

  it('emits update:modelValue when a field is edited', async () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: {} })
    await wrapper.find('[data-testid="schema-field-name"]').setValue('alice')
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted?.[emitted.length - 1]).toEqual([{ name: 'alice' }])
  })

  it('aggregates multiple field edits', async () => {
    const wrapper = mountSchemaForm({ schema: objectSchema, modelValue: { name: 'alice' } })
    await wrapper.setProps({ modelValue: { name: 'alice', age: 42 } })
    await wrapper.find('[data-testid="schema-field-active"]').setValue(true)
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.[emitted?.length - 1]).toEqual([{ name: 'alice', age: 42, active: true }])
  })

  it('renders nested object via recursion', () => {
    const schema = {
      type: 'object',
      properties: {
        address: {
          type: 'object',
          properties: { city: { type: 'string' } },
          required: ['city'],
        },
      },
    }
    const wrapper = mountSchemaForm({ schema, modelValue: {} })
    expect(wrapper.find('fieldset').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schema-field-city"]').exists()).toBe(true)
  })

  it('renders array items with add and remove', async () => {
    const schema = {
      type: 'array',
      items: { type: 'string' },
    }
    const wrapper = mountSchemaForm({ schema, modelValue: [] })
    expect(wrapper.find('[data-testid="add-array-item"]').exists()).toBe(true)
    await wrapper.find('[data-testid="add-array-item"]').trigger('click')
    await flushPromises()
    await wrapper.setProps({ modelValue: [undefined] })
    const removeBtn = wrapper.find('[data-testid="remove-array-item-0"]')
    expect(removeBtn.exists()).toBe(true)
    await removeBtn.trigger('click')
    await flushPromises()
    const emitted2 = wrapper.emitted('update:modelValue')
    expect(emitted2?.[emitted2.length - 1]).toEqual([[]])
  })

  it('initializes from schema.default', async () => {
    const schema = {
      type: 'object',
      properties: {
        count: { type: 'number', default: 7 },
      },
    }
    const wrapper = mountSchemaForm({ schema, modelValue: {} })
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.[emitted?.length - 1]).toEqual([{ count: 7 }])
  })

  it('renders textarea for type any and emits parsed JSON on input', async () => {
    const schema = { type: 'any' }
    const wrapper = mountSchemaForm({ schema, modelValue: undefined })
    const textarea = wrapper.find('textarea')
    expect(textarea.exists()).toBe(true)
    await textarea.setValue('{ "foo": 1 }')
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted?.[emitted?.length - 1]).toEqual([{ foo: 1 }])
  })

  it('shows JSON error for invalid any textarea', async () => {
    const schema = { type: 'any' }
    const wrapper = mountSchemaForm({ schema, modelValue: undefined })
    const textarea = wrapper.find('textarea')
    await textarea.setValue('not json')
    await flushPromises()
    expect(wrapper.text()).toContain('Invalid JSON')
  })
})
