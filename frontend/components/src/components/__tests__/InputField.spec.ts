import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import InputField from '../runner/InputField.vue'

describe('InputField', () => {
it('exposes root data-testid', () => {
    const wrapper = mount(InputField, {
      props: { name: 'username', type: 'string', modelValue: '' },
    })
    expect(wrapper.find('[data-testid="input-field"]').exists()).toBe(true)
  })
  it('renders a label and a text input', () => {
    const wrapper = mount(InputField, {
      props: { name: 'username', type: 'string', modelValue: '' },
    })
    expect(wrapper.text()).toContain('username')
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('text')
  })

  it('marks required fields with an asterisk in the label', () => {
    const wrapper = mount(InputField, {
      props: { name: 'email', type: 'string', modelValue: '', required: true },
    })
    expect(wrapper.find('label').text()).toContain('*')
  })

  it('emits update:modelValue when the text input changes', async () => {
    const wrapper = mount(InputField, {
      props: { name: 'username', type: 'string', modelValue: '' },
    })
    const input = wrapper.find('input')
    await input.setValue('alice')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]).toEqual(['alice'])
  })

  it('renders a number input when type is number', () => {
    const wrapper = mount(InputField, {
      props: { name: 'age', type: 'number', modelValue: 0 },
    })
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('number')
  })

  it('emits update:modelValue when the number input changes', async () => {
    const wrapper = mount(InputField, {
      props: { name: 'age', type: 'number', modelValue: 0 },
    })
    const input = wrapper.find('input')
    await input.setValue('42')
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted?.[0]).toEqual(['42'])
  })

  it('renders a checkbox input when type is boolean', () => {
    const wrapper = mount(InputField, {
      props: { name: 'agree', type: 'boolean', modelValue: false },
    })
    const input = wrapper.find('input')
    expect(input.exists()).toBe(true)
    expect(input.attributes('type')).toBe('checkbox')
  })

  it('does not show the required error before the field is touched', () => {
    const wrapper = mount(InputField, {
      props: { name: 'email', type: 'string', modelValue: '', required: true },
    })
    expect(wrapper.text()).not.toContain('is required')
  })

  it('shows the required error after blur on an empty required field', async () => {
    const wrapper = mount(InputField, {
      props: { name: 'email', type: 'string', modelValue: '', required: true },
    })
    await wrapper.find('input').trigger('blur')
    expect(wrapper.text()).toContain('email is required')
    expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
  })

  it('does not show the required error when a required field has a value', async () => {
    const wrapper = mount(InputField, {
      props: { name: 'email', type: 'string', modelValue: 'a@b.c', required: true },
    })
    await wrapper.find('input').trigger('blur')
    expect(wrapper.text()).not.toContain('is required')
  })
})
