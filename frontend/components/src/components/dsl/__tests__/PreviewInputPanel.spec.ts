import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { __resetConstructSchemaCache } from '../../../composables/useConstructSchema'
import PreviewInputPanel from '../PreviewInputPanel.vue'
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
  vi.stubGlobal('$fetch', fetchMock)
  return mount(PreviewInputPanel, {
    props: { name: 'demo', type: 'Process', modelValue: '', ...props },
    global: { components: { SchemaForm, SchemaFormField } },
  })
}

describe('PreviewInputPanel', () => {
  beforeEach(() => {
    __resetConstructSchemaCache()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
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
})
