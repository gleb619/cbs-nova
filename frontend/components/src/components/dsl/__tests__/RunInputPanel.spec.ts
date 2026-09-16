import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DSL_SCHEMA_FETCH_KEY,
  __resetConstructSchemaCache,
} from '../../../composables/useConstructSchema'
import RunInputPanel from '../RunInputPanel.vue'
import SchemaForm from '../SchemaForm.vue'
import SchemaFormField from '../SchemaFormField.vue'

const schemaResponse = {
  inputSchema: {
    type: 'object',
    properties: {
      name: { type: 'string' },
      count: { type: 'number' },
      active: { type: 'boolean' },
      role: { type: 'string', enum: ['admin', 'user'] },
      nested: {
        type: 'object',
        properties: { city: { type: 'string' } },
      },
      tags: { type: 'array', items: { type: 'string' } },
      defaulted: { type: 'string', default: 'hello' },
    },
  },
}

function mountPanel(
  props: Record<string, unknown> = {},
  fetchMock = vi.fn().mockResolvedValue({}),
) {
  return mount(RunInputPanel, {
    props: { name: 'demo', type: 'Process', modelValue: '', ...props } as never,
    global: {
      components: { SchemaForm, SchemaFormField },
      provide: { [DSL_SCHEMA_FETCH_KEY as symbol]: fetchMock },
    },
  })
}

describe('RunInputPanel', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
  })

  afterEach(() => {
    vi.resetModules()
  })

  it('keeps Run button in the header', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    const header = wrapper.find('header')
    expect(header.find('[data-testid="run-button"]').exists()).toBe(true)
    expect(header.text()).toContain('Run')
  })

  it('moves mode toggle and action buttons into the footer', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    const footer = wrapper.find('footer')
    expect(footer.find('[data-testid="mode-form"]').exists()).toBe(true)
    expect(footer.find('[data-testid="mode-json"]').exists()).toBe(true)
    expect(footer.find('[data-testid="generate-input"]').exists()).toBe(true)
    expect(footer.find('[data-testid="format-input"]').exists()).toBe(true)
    expect(wrapper.find('header').find('[data-testid="mode-form"]').exists()).toBe(false)
  })

  it('shows generate button when schema available', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()
    expect(wrapper.find('[data-testid="generate-input"]').exists()).toBe(true)
  })

  it('hides generate button when schema missing', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue({ inputSchema: null }))
    await flushPromises()
    expect(wrapper.find('[data-testid="generate-input"]').exists()).toBe(false)
  })

  it('generates fake JSON in json mode', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="generate-input"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const value = JSON.parse(emitted?.[emitted.length - 1]?.[0] as string)
    expect(value).toMatchObject({
      name: 'Test Name',
      count: 42,
      active: true,
      role: 'admin',
      nested: { city: 'fake-city' },
      tags: ['fake-tagsItem'],
      defaulted: 'hello',
    })
  })

  it('emits update:modelValue when generating', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="generate-input"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const last = emitted?.[emitted.length - 1]?.[0] as string
    expect(JSON.parse(last)).toMatchObject({ count: 42 })
  })

  it('fills form fields when generating in form mode', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="generate-input"]').trigger('click')
    await flushPromises()

    expect(
      (wrapper.find('[data-testid="schema-field-name"]').element as HTMLInputElement).value,
    ).toBe('Test Name')
    expect(
      (wrapper.find('[data-testid="schema-field-count"]').element as HTMLInputElement).value,
    ).toBe('42')
    expect(
      (wrapper.find('[data-testid="schema-field-role"]').element as HTMLSelectElement).value,
    ).toBe('"admin"')
  })

  it('disables generate button while busy', async () => {
    const wrapper = mountPanel({ busy: true }, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    const btn = wrapper.find('[data-testid="generate-input"]')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('does not show generate button for unsupported type', async () => {
    const wrapper = mountPanel({ type: undefined }, vi.fn())
    await flushPromises()
    expect(wrapper.find('[data-testid="generate-input"]').exists()).toBe(false)
  })

  it('formats JSON in json mode', async () => {
    const wrapper = mountPanel({ modelValue: '{"a":1}' }, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="format-input"]').trigger('click')
    await flushPromises()

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    const last = emitted?.[emitted.length - 1]?.[0] as string
    expect(last).toBe('{\n  "a": 1\n}\n')
  })

  it('shows the read-only input schema in Schema mode', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="mode-schema"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="schema-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schema-view"]').text()).toContain('"type": "object"')
    expect(wrapper.find('[data-testid="json-textarea"]').exists()).toBe(false)
  })

  it('disables format button in form mode', async () => {
    const wrapper = mountPanel({}, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    await wrapper.find('[data-testid="mode-form"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="format-input"]').attributes('disabled')).toBeDefined()
  })

  it('shows invalid JSON status in the footer', async () => {
    const wrapper = mountPanel({ modelValue: '{ bad' }, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    const status = wrapper.find('[data-testid="input-status"]')
    expect(status.text()).toContain('Invalid JSON')
    expect(status.classes()).toContain('text-danger')
  })

  it('shows valid JSON status in the footer', async () => {
    const wrapper = mountPanel(
      { modelValue: '{"ok":true}' },
      vi.fn().mockResolvedValue(schemaResponse),
    )
    await flushPromises()

    const status = wrapper.find('[data-testid="input-status"]')
    expect(status.text()).toBe('Valid JSON')
  })

  it('shows schema loading skeleton while the schema is loading', async () => {
    const wrapper = mountPanel(
      { type: 'Process' },
      vi.fn(() => new Promise(() => {})),
    )
    await flushPromises()

    expect(wrapper.find('[data-testid="input-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="json-textarea"]').exists()).toBe(false)
  })

  it('hides the skeleton after the schema loads', async () => {
    const wrapper = mountPanel({ type: 'Process' }, vi.fn().mockResolvedValue(schemaResponse))
    await flushPromises()

    expect(wrapper.find('[data-testid="input-skeleton"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="json-textarea"]').exists()).toBe(true)
  })
})
