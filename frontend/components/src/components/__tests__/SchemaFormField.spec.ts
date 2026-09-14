import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import SchemaFormField from '../dsl/SchemaFormField.vue'
import type { JsonSchema } from '../../types/jsonSchema'
import { createSchemaFieldEventBus, SCHEMA_FIELD_EVENTS_KEY } from '../dsl/schemaFieldEvents'

function mountField(props: Record<string, unknown> = {}) {
  return mount(SchemaFormField, { props: props as never })
}

describe('SchemaFormField', () => {
  describe('string', () => {
    it('renders a text input with the current value and emits raw string on input', async () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: 'alice',
      })
      const input = wrapper.find('[data-testid="schema-field-name"]')
      expect(input.exists()).toBe(true)
      expect(input.element.tagName.toLowerCase()).toBe('input')
      expect(input.attributes('type')).toBe('text')
      expect((input.element as HTMLInputElement).value).toBe('alice')

      await input.setValue('bob')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(emitted?.[emitted.length - 1]).toEqual(['bob'])
    })
  })

  describe('string + enum', () => {
    const enumSchema = {
      type: 'string',
      enum: ['admin', 'user', 'guest'],
    }

    it('renders one option per enum value plus __NULL__ when not required', () => {
      const wrapper = mountField({
        name: 'role',
        schema: enumSchema,
        modelValue: undefined,
      })
      const select = wrapper.find('[data-testid="schema-field-role"]')
      expect(select.exists()).toBe(true)
      expect(select.element.tagName.toLowerCase()).toBe('select')
      const options = select.findAll('option')
      // __NULL__ + 3 enum values
      expect(options).toHaveLength(4)
      expect(options[0].attributes('value')).toBe('__NULL__')
      expect(options[1].attributes('value')).toBe(JSON.stringify('admin'))
      expect(options[2].attributes('value')).toBe(JSON.stringify('user'))
      expect(options[3].attributes('value')).toBe(JSON.stringify('guest'))
    })

    it('omits __NULL__ when required', () => {
      const wrapper = mountField({
        name: 'role',
        schema: enumSchema,
        modelValue: 'admin',
        required: true,
      })
      const options = wrapper.findAll('option')
      expect(options).toHaveLength(3)
      expect(options.find((o) => o.attributes('value') === '__NULL__')).toBeUndefined()
    })

    it('emits JSON.parsed value when an enum option is selected', async () => {
      const wrapper = mountField({
        name: 'role',
        schema: enumSchema,
        modelValue: undefined,
      })
      const select = wrapper.find('[data-testid="schema-field-role"]')
      await select.setValue(JSON.stringify('user'))
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual(['user'])
    })

    it('emits null when __NULL__ is selected (not required)', async () => {
      const wrapper = mountField({
        name: 'role',
        schema: enumSchema,
        modelValue: 'admin',
      })
      const select = wrapper.find('[data-testid="schema-field-role"]')
      await select.setValue('__NULL__')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([null])
    })
  })

  describe('number', () => {
    it('emits 42 (number) when typing "42"', async () => {
      const wrapper = mountField({
        name: 'age',
        schema: { type: 'number' },
        modelValue: undefined,
      })
      const input = wrapper.find('[data-testid="schema-field-age"]')
      expect(input.attributes('type')).toBe('number')
      await input.setValue('42')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([42])
    })

    it('emits undefined when input is cleared to ""', async () => {
      const wrapper = mountField({
        name: 'age',
        schema: { type: 'number' },
        modelValue: 5,
      })
      const input = wrapper.find('[data-testid="schema-field-age"]')
      await input.setValue('')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([undefined])
    })

    it('emits 1.5 when typing "1.5"', async () => {
      const wrapper = mountField({
        name: 'age',
        schema: { type: 'number' },
        modelValue: undefined,
      })
      const input = wrapper.find('[data-testid="schema-field-age"]')
      await input.setValue('1.5')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([1.5])
    })
  })

  describe('boolean', () => {
    it('checkbox :checked reflects Boolean(modelValue)', () => {
      const wrapper = mountField({
        name: 'active',
        schema: { type: 'boolean' },
        modelValue: true,
      })
      const input = wrapper.find('[data-testid="schema-field-active"]')
      expect(input.attributes('type')).toBe('checkbox')
      expect((input.element as HTMLInputElement).checked).toBe(true)

      const wrapper2 = mountField({
        name: 'active',
        schema: { type: 'boolean' },
        modelValue: false,
      })
      const input2 = wrapper2.find('[data-testid="schema-field-active"]')
      expect((input2.element as HTMLInputElement).checked).toBe(false)
    })

    it('toggling emits true / false', async () => {
      const wrapper = mountField({
        name: 'active',
        schema: { type: 'boolean' },
        modelValue: false,
      })
      const input = wrapper.find('[data-testid="schema-field-active"]')
      await input.setValue(true)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([true])

      await input.setValue(false)
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([false])
    })

    it('label is the field name (wraps the checkbox, no separate <label> above)', () => {
      const wrapper = mountField({
        name: 'active',
        schema: { type: 'boolean' },
        modelValue: false,
      })
      // Single label, and it contains the field name as text.
      const labels = wrapper.findAll('label')
      expect(labels).toHaveLength(1)
      expect(labels[0].text()).toContain('active')
      // The wrapping label must be the one that contains the checkbox input.
      expect(labels[0].find('input[type="checkbox"]').exists()).toBe(true)
    })
  })

  describe('null type', () => {
    it('renders a hidden input with no interactive control', () => {
      const wrapper = mountField({
        name: 'empty',
        schema: { type: 'null' },
        modelValue: null,
      })
      const input = wrapper.find('[data-testid="schema-field-empty"]')
      expect(input.exists()).toBe(true)
      expect(input.attributes('type')).toBe('hidden')
      // No button/select/textarea/visible input.
      expect(wrapper.find('button').exists()).toBe(false)
      expect(wrapper.find('select').exists()).toBe(false)
      expect(wrapper.find('textarea').exists()).toBe(false)
    })

    it('emits nothing on interaction (no handlers wired)', async () => {
      const wrapper = mountField({
        name: 'empty',
        schema: { type: 'null' },
        modelValue: null,
      })
      const input = wrapper.find('[data-testid="schema-field-empty"]')
      // Dispatching input/change should not trigger any emits because no listener is bound.
      await input.trigger('input')
      await input.trigger('change')
      expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    })
  })

  describe('object + properties', () => {
    const objectSchema = {
      type: 'object',
      properties: {
        city: { type: 'string' },
      },
    } satisfies JsonSchema

    it('renders the nested SchemaForm (stubbed) and forwards update:modelValue', async () => {
      const wrapper = mount(SchemaFormField, {
        props: {
          name: 'address',
          schema: objectSchema,
          modelValue: { city: 'NYC' },
        },
        global: { stubs: { SchemaForm: true } },
      })
      const nested = wrapper.findComponent({ name: 'SchemaForm' })
      expect(nested.exists()).toBe(true)
      // Passes modelValue ?? {} -> here modelValue is defined so forwarded as-is.
      expect(nested.props('modelValue')).toEqual({ city: 'NYC' })
      expect(nested.props('schema')).toEqual(objectSchema)
      // Nested update forwards out.
      await nested.vm.$emit('update:modelValue', { city: 'LA' })
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([{ city: 'LA' }])
    })

    it('passes {} as modelValue when prop is undefined', () => {
      const wrapper = mount(SchemaFormField, {
        props: {
          name: 'address',
          schema: objectSchema,
          modelValue: undefined,
        },
        global: { stubs: { SchemaForm: true } },
      })
      const nested = wrapper.findComponent({ name: 'SchemaForm' })
      expect(nested.props('modelValue')).toEqual({})
    })
  })

  describe('any / object-without-properties (jsonText textarea)', () => {
    it('valid JSON (typed) emits parsed value and clears any prior error', async () => {
      const wrapper = mountField({
        name: 'payload',
        schema: { type: 'any' },
        modelValue: undefined,
      })
      const textarea = wrapper.find('[data-testid="schema-field-payload"]')
      expect(textarea.element.tagName.toLowerCase()).toBe('textarea')

      // First type something invalid.
      await textarea.setValue('not json')
      expect(wrapper.text()).toContain('Invalid JSON')

      // Then type valid JSON — v-model set should emit parsed and clear error.
      await textarea.setValue('{ "foo": 1 }')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([{ foo: 1 }])
      expect(wrapper.text()).not.toContain('Invalid JSON')
    })

    it('invalid JSON shows "Invalid JSON: ..." and emits nothing', async () => {
      const wrapper = mountField({
        name: 'payload',
        schema: { type: 'any' },
        modelValue: undefined,
      })
      const textarea = wrapper.find('[data-testid="schema-field-payload"]')
      await textarea.setValue('not json')
      expect(wrapper.emitted('update:modelValue')).toBeFalsy()
      expect(wrapper.text()).toContain('Invalid JSON')
      expect(wrapper.text()).toContain('not json')
    })

    it('empty textarea emits undefined', async () => {
      const wrapper = mountField({
        name: 'payload',
        schema: { type: 'any' },
        modelValue: { keep: true },
      })
      const textarea = wrapper.find('[data-testid="schema-field-payload"]')
      await textarea.setValue('')
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted?.[emitted.length - 1]).toEqual([undefined])
    })

    it('object schema without properties also falls through to the textarea branch', async () => {
      const wrapper = mountField({
        name: 'misc',
        schema: { type: 'object' },
        modelValue: undefined,
      })
      const textarea = wrapper.find('[data-testid="schema-field-misc"]')
      expect(textarea.element.tagName.toLowerCase()).toBe('textarea')
      await textarea.setValue('[1,2,3]')
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([[1, 2, 3]])
    })
  })

  describe('required validation', () => {
    it('shows "<name> is required" and applies border-danger / aria-invalid once blurred while empty', async () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: '',
        required: true,
      })
      const input = wrapper.find('[data-testid="schema-field-name"]')
      // Before blur: no error.
      expect(wrapper.text()).not.toContain('name is required')
      expect(input.classes()).not.toContain('border-danger')
      expect(input.attributes('aria-invalid')).toBe('false')

      await input.trigger('blur')
      expect(wrapper.text()).toContain('name is required')
      expect(input.classes()).toContain('border-danger')
      expect(input.attributes('aria-invalid')).toBe('true')
    })

    it('does NOT show error when required but not yet blurred', () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: undefined,
        required: true,
      })
      expect(wrapper.text()).not.toContain('name is required')
    })

    it('does NOT show error for non-empty required field even after blur', async () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: 'alice',
        required: true,
      })
      const input = wrapper.find('[data-testid="schema-field-name"]')
      await input.trigger('blur')
      expect(wrapper.text()).not.toContain('name is required')
    })
  })

  describe('readonly', () => {
    it('disables string, number, boolean, and select inputs', () => {
      const wrapper = mount(SchemaFormField, {
        props: {
          name: 'name',
          schema: { type: 'string' },
          modelValue: 'x',
          readonly: true,
        },
      })
      expect(wrapper.find('[data-testid="schema-field-name"]').attributes('disabled')).toBeDefined()

      const num = mount(SchemaFormField, {
        props: { name: 'age', schema: { type: 'number' }, modelValue: 1, readonly: true },
      })
      expect(num.find('[data-testid="schema-field-age"]').attributes('disabled')).toBeDefined()

      const bool = mount(SchemaFormField, {
        props: { name: 'active', schema: { type: 'boolean' }, modelValue: true, readonly: true },
      })
      expect(bool.find('[data-testid="schema-field-active"]').attributes('disabled')).toBeDefined()

      const sel = mount(SchemaFormField, {
        props: {
          name: 'role',
          schema: { type: 'string', enum: ['a', 'b'] },
          modelValue: 'a',
          readonly: true,
        },
      })
      expect(sel.find('[data-testid="schema-field-role"]').attributes('disabled')).toBeDefined()
    })

    it('handlers emit nothing on interaction when readonly', async () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: 'x',
        readonly: true,
      })
      await wrapper.find('[data-testid="schema-field-name"]').setValue('y')
      expect(wrapper.emitted('update:modelValue')).toBeFalsy()
    })

    it('remove() no-ops when readonly', () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: 'x',
        readonly: true,
      })
      ;(wrapper.vm as unknown as { remove: () => void }).remove()
      expect(wrapper.emitted('remove')).toBeFalsy()
    })
  })

  describe('remove', () => {
    it('emits "remove" when not readonly', () => {
      const wrapper = mountField({
        name: 'name',
        schema: { type: 'string' },
        modelValue: 'x',
      })
      ;(wrapper.vm as unknown as { remove: () => void }).remove()
      expect(wrapper.emitted('remove')).toBeTruthy()
      expect(wrapper.emitted('remove')).toHaveLength(1)
    })
  })

  describe('jsonError cleared via schema field events', () => {
    it('clears a prior jsonError when the bus emits clear-error for the field', async () => {
      const bus = createSchemaFieldEventBus()
      const wrapper = mount(SchemaFormField, {
        props: {
          name: 'payload',
          schema: { type: 'any' },
          modelValue: undefined,
        },
        global: { provide: { [SCHEMA_FIELD_EVENTS_KEY as symbol]: bus } },
      })
      const textarea = wrapper.find('[data-testid="schema-field-payload"]')
      await textarea.setValue('not json')
      expect(wrapper.text()).toContain('Invalid JSON')

      bus.emitClearError('payload')
      await nextTick()
      expect(wrapper.text()).not.toContain('Invalid JSON')
    })

    it('ignores clear-error events for other fields', async () => {
      const bus = createSchemaFieldEventBus()
      const wrapper = mount(SchemaFormField, {
        props: {
          name: 'payload',
          schema: { type: 'any' },
          modelValue: undefined,
        },
        global: { provide: { [SCHEMA_FIELD_EVENTS_KEY as symbol]: bus } },
      })
      const textarea = wrapper.find('[data-testid="schema-field-payload"]')
      await textarea.setValue('not json')
      expect(wrapper.text()).toContain('Invalid JSON')

      bus.emitClearError('other')
      expect(wrapper.text()).toContain('Invalid JSON')
    })
  })
})
